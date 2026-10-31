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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject
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
) {
    val domain: String get() = entityId.substringBefore('.')
    val isUnavailable: Boolean get() = state == "unavailable" || state == "unknown"
    val isActive: Boolean get() = state in ACTIVE_STATES

    companion object {
        private val ACTIVE_STATES = setOf(
            "on", "open", "opening", "playing", "unlocked", "heat", "cool", "heat_cool",
            "auto", "dry", "fan_only", "cleaning", "home",
        )

        fun fromJson(o: JSONObject): HaEntity {
            val id = o.getString("entity_id")
            val a = o.optJSONObject("attributes") ?: JSONObject()
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
            )
        }
    }
}

data class HaLocation(val latitude: Double, val longitude: Double, val name: String)

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

    private val ids = AtomicInteger(1)
    private var loopJob: Job? = null
    @Volatile private var socket: WebSocket? = null

    private enum class SessionEnd { AuthFailed, WasConnected, Failed }

    fun connect(rawUrl: String, token: String) {
        loopJob?.cancel()
        socket?.cancel()
        socket = null
        _states.value = emptyMap()
        val wsUrl = websocketUrl(rawUrl)
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
                        statesId = ids.incrementAndGet()
                        webSocket.send(JSONObject().put("id", statesId).put("type", "get_states").toString())
                        configId = ids.incrementAndGet()
                        webSocket.send(JSONObject().put("id", configId).put("type", "get_config").toString())
                        webSocket.send(
                            JSONObject().put("id", ids.incrementAndGet())
                                .put("type", "subscribe_events")
                                .put("event_type", "state_changed")
                                .toString()
                        )
                    }
                    "result" -> {
                        if (!msg.optBoolean("success")) return
                        val id = msg.optInt("id")
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
                        val newState = data.optJSONObject("new_state")
                        _states.update { current ->
                            if (newState == null) current - id
                            else try { current + (id to HaEntity.fromJson(newState)) } catch (_: JSONException) { current }
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
            if (socket === ws) socket = null
        }
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
        fun websocketUrl(raw: String): String? {
            var u = raw.trim().trimEnd('/')
            if (u.isBlank()) return null
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            return u.replaceFirst("http", "ws") + "/api/websocket"
        }
    }
}
