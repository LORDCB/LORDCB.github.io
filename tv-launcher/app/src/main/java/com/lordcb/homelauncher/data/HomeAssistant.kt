package com.lordcb.homelauncher.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class HaEntity(
    val entityId: String,
    val state: String,
    val name: String,
    val unit: String?,
    val deviceClass: String?,
    val brightness: Int?,
    val currentTemperature: Double?,
    val targetTemperature: Double?,
    val mediaTitle: String?,
    // Lights
    val colorModes: List<String> = emptyList(),
    val rgb: Int? = null,
    val colorTempKelvin: Int? = null,
    val minKelvin: Int? = null,
    val maxKelvin: Int? = null,
    // Cameras
    val entityPicture: String? = null,
) {
    val domain: String get() = entityId.substringBefore('.')
    val isUnavailable: Boolean get() = state == "unavailable" || state == "unknown"
    val isActive: Boolean get() = state in ACTIVE_STATES
    val isOn: Boolean get() = state == "on"

    val supportsColor: Boolean get() = colorModes.any { it in COLOR_MODES }
    val supportsColorTemp: Boolean get() = "color_temp" in colorModes
    val supportsBrightness: Boolean
        get() = brightness != null || colorModes.any { it != "onoff" && it != "unknown" }

    companion object {
        private val ACTIVE_STATES = setOf(
            "on", "open", "opening", "playing", "unlocked", "heat", "cool", "heat_cool",
            "auto", "dry", "fan_only", "cleaning", "home",
        )
        private val COLOR_MODES = setOf("hs", "rgb", "rgbw", "rgbww", "xy")

        fun fromJson(o: JSONObject): HaEntity {
            val id = o.getString("entity_id")
            val a = o.optJSONObject("attributes") ?: JSONObject()
            val modes = a.optJSONArray("supported_color_modes")
            val rgb = a.optJSONArray("rgb_color")
            return HaEntity(
                entityId = id,
                state = o.str("state") ?: "unknown",
                name = a.str("friendly_name") ?: id,
                unit = a.str("unit_of_measurement"),
                deviceClass = a.str("device_class"),
                brightness = if (a.isNull("brightness")) null else a.optInt("brightness"),
                currentTemperature = a.dbl("current_temperature"),
                targetTemperature = a.dbl("temperature"),
                mediaTitle = a.str("media_title"),
                colorModes = modes?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),
                rgb = if (rgb != null && rgb.length() >= 3) {
                    (0xFF shl 24) or (rgb.optInt(0) shl 16) or (rgb.optInt(1) shl 8) or rgb.optInt(2)
                } else null,
                colorTempKelvin = if (a.isNull("color_temp_kelvin")) null else a.optInt("color_temp_kelvin"),
                minKelvin = if (a.isNull("min_color_temp_kelvin")) null else a.optInt("min_color_temp_kelvin"),
                maxKelvin = if (a.isNull("max_color_temp_kelvin")) null else a.optInt("max_color_temp_kelvin"),
                entityPicture = a.str("entity_picture"),
            )
        }
    }
}

data class HaLocation(val latitude: Double, val longitude: Double, val name: String)

/** Area names from Home Assistant, in the order they're defined there. */
data class HaAreas(val areaOfEntity: Map<String, String> = emptyMap(), val order: List<String> = emptyList())

sealed interface HaStatus {
    data object NotConfigured : HaStatus
    data object Connecting : HaStatus
    data object Connected : HaStatus
    data class Error(val message: String) : HaStatus
}

internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.dbl(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

/**
 * Talks to Home Assistant over its WebSocket API: fetches all states once, then
 * receives live `state_changed` events, and sends service calls on the same socket.
 * Reconnects automatically with backoff.
 */
class HomeAssistantClient(private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _states = MutableStateFlow<Map<String, HaEntity>>(emptyMap())
    val states: StateFlow<Map<String, HaEntity>> = _states.asStateFlow()

    private val _status = MutableStateFlow<HaStatus>(HaStatus.NotConfigured)
    val status: StateFlow<HaStatus> = _status.asStateFlow()

    private val _location = MutableStateFlow<HaLocation?>(null)
    val location: StateFlow<HaLocation?> = _location.asStateFlow()

    private val _areas = MutableStateFlow(HaAreas())
    val areas: StateFlow<HaAreas> = _areas.asStateFlow()

    /** e.g. "http://192.168.1.10:8123", for camera streams and snapshots. */
    @Volatile var baseUrl: String? = null
        private set
    @Volatile var token: String = ""
        private set

    private val ids = AtomicInteger(1)
    private var loopJob: Job? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject?>>()

    /**
     * Live changes are only applied for these entities and domains. Everything else is ignored,
     * so busy sensors elsewhere in the house don't cause work on the TV. The full list is
     * refreshed on demand (e.g. when the device picker opens) via [refreshAllStates].
     */
    @Volatile var watched: Set<String> = emptySet()
    private val watchedDomains = setOf("light", "camera")

    @Volatile private var requestStates: (() -> Unit)? = null
    @Volatile private var socket: WebSocket? = null

    private enum class SessionEnd { AuthFailed, WasConnected, Failed }

    private fun isWatched(id: String) = id in watched || id.substringBefore('.') in watchedDomains

    fun connect(rawUrl: String, token: String) {
        loopJob?.cancel()
        socket?.cancel()
        socket = null
        _states.value = emptyMap()
        val wsUrl = websocketUrl(rawUrl)
        baseUrl = httpBaseUrl(rawUrl)
        this.token = token.trim()
        if (wsUrl == null || token.isBlank()) {
            _status.value = HaStatus.NotConfigured
            return
        }
        loopJob = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                _status.value = HaStatus.Connecting
                val end = try {
                    runSession(wsUrl, token.trim())
                } catch (e: IllegalArgumentException) {
                    _status.value = HaStatus.Error("That server address doesn't look right")
                    return@launch
                }
                if (end == SessionEnd.AuthFailed) {
                    _status.value = HaStatus.Error("Home Assistant rejected the access token")
                    return@launch
                }
                if (end == SessionEnd.WasConnected) {
                    backoff = 2_000L
                    _status.value = HaStatus.Error("Connection lost, reconnecting…")
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun runSession(url: String, token: String): SessionEnd {
        val done = CompletableDeferred<SessionEnd>()
        var connected = false
        var statesId = -1
        var configId = -1

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = try { JSONObject(text) } catch (e: JSONException) { return }
                when (msg.optString("type")) {
                    "auth_required" -> webSocket.send(
                        JSONObject().put("type", "auth").put("access_token", token).toString()
                    )
                    "auth_invalid" -> {
                        done.complete(SessionEnd.AuthFailed)
                        webSocket.close(1000, null)
                    }
                    "auth_ok" -> {
                        connected = true
                        _status.value = HaStatus.Connected
                        requestStates = {
                            statesId = ids.incrementAndGet()
                            webSocket.send(JSONObject().put("id", statesId).put("type", "get_states").toString())
                        }
                        requestStates?.invoke()
                        configId = ids.incrementAndGet()
                        webSocket.send(JSONObject().put("id", configId).put("type", "get_config").toString())
                        webSocket.send(
                            JSONObject().put("id", ids.incrementAndGet())
                                .put("type", "subscribe_events")
                                .put("event_type", "state_changed")
                                .toString()
                        )
                        scope.launch { loadAreas() }
                    }
                    "result" -> {
                        val id = msg.optInt("id")
                        pending.remove(id)?.let {
                            it.complete(if (msg.optBoolean("success")) msg else null)
                            return
                        }
                        if (!msg.optBoolean("success")) return
                        if (id == statesId) {
                            val arr = msg.optJSONArray("result") ?: return
                            val map = HashMap<String, HaEntity>(arr.length())
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                try {
                                    val e = HaEntity.fromJson(o)
                                    map[e.entityId] = e
                                } catch (_: JSONException) {
                                }
                            }
                            _states.value = map
                        } else if (id == configId) {
                            val r = msg.optJSONObject("result") ?: return
                            val lat = r.dbl("latitude")
                            val lon = r.dbl("longitude")
                            if (lat != null && lon != null) {
                                _location.value = HaLocation(lat, lon, r.str("location_name") ?: "Home")
                            }
                        }
                    }
                    "event" -> {
                        val data = msg.optJSONObject("event")?.optJSONObject("data") ?: return
                        val id = data.str("entity_id") ?: return
                        if (!isWatched(id)) return
                        val newState = data.optJSONObject("new_state")
                        val entity = try { newState?.let { HaEntity.fromJson(it) } } catch (_: JSONException) { return }
                        // Skip updates that don't change anything we display (e.g. last_updated churn).
                        if (_states.value[id] == entity) return
                        _states.update { current ->
                            if (entity == null) current - id else current + (id to entity)
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                done.complete(if (connected) SessionEnd.WasConnected else SessionEnd.Failed)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.complete(if (connected) SessionEnd.WasConnected else SessionEnd.Failed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connected) {
                    _status.value = HaStatus.Error(t.message ?: "Couldn't reach Home Assistant")
                }
                done.complete(if (connected) SessionEnd.WasConnected else SessionEnd.Failed)
            }
        }

        val ws = http.newWebSocket(Request.Builder().url(url).build(), listener)
        socket = ws
        return try {
            done.await()
        } finally {
            ws.cancel()
            pending.values.forEach { it.complete(null) }
            pending.clear()
            if (socket === ws) {
                socket = null
                requestStates = null
            }
        }
    }

    /** Sends a command and waits for its result message (or null on failure/timeout). */
    suspend fun request(type: String, payload: JSONObject = JSONObject(), timeoutMs: Long = 10_000): JSONObject? {
        val ws = socket ?: return null
        val id = ids.incrementAndGet()
        val result = CompletableDeferred<JSONObject?>()
        pending[id] = result
        payload.put("id", id).put("type", type)
        if (!ws.send(payload.toString())) {
            pending.remove(id)
            return null
        }
        return try {
            withTimeoutOrNull(timeoutMs) { result.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** Asks HA to start an HLS stream for a camera; returns the playlist URL. */
    suspend fun cameraStreamUrl(entityId: String): String? {
        val res = request("camera/stream", JSONObject().put("entity_id", entityId), 20_000)
        val path = res?.optJSONObject("result")?.str("url") ?: return null
        return absoluteUrl(path)
    }

    fun absoluteUrl(path: String): String? =
        if (path.startsWith("http")) path else baseUrl?.let { it + path }

    /** Builds a map of entity → area name (entity's own area, else its device's area). */
    private suspend fun loadAreas() {
        val areaList = request("config/area_registry/list")?.optJSONArray("result") ?: return
        val areaNames = LinkedHashMap<String, String>()
        areaList.forEachObject { areaNames[it.optString("area_id")] = it.optString("name") }

        val deviceArea = HashMap<String, String>()
        request("config/device_registry/list", timeoutMs = 20_000)?.optJSONArray("result")?.forEachObject { d ->
            d.str("area_id")?.let { deviceArea[d.optString("id")] = it }
        }
        val result = HashMap<String, String>()
        request("config/entity_registry/list", timeoutMs = 20_000)?.optJSONArray("result")?.forEachObject { e ->
            val areaId = e.str("area_id") ?: e.str("device_id")?.let { deviceArea[it] }
            val name = areaId?.let { areaNames[it] }
            if (name != null) result[e.optString("entity_id")] = name
        }
        _areas.value = HaAreas(result, areaNames.values.toList())
    }

    /** Re-fetch every entity's current state (used by the device picker). */
    fun refreshAllStates() {
        requestStates?.invoke()
    }

    fun callService(domain: String, service: String, entityId: String, data: JSONObject? = null) {
        val msg = JSONObject()
            .put("id", ids.incrementAndGet())
            .put("type", "call_service")
            .put("domain", domain)
            .put("service", service)
            .put("target", JSONObject().put("entity_id", entityId))
        if (data != null) msg.put("service_data", data)
        socket?.send(msg.toString())
    }

    companion object {
        fun httpBaseUrl(raw: String): String? {
            var u = raw.trim().trimEnd('/')
            if (u.isBlank()) return null
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            return u
        }

        fun websocketUrl(raw: String): String? =
            httpBaseUrl(raw)?.replaceFirst("http", "ws")?.plus("/api/websocket")
    }
}

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(block)
}
