package com.lordcb.homelauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lordcb.homelauncher.data.AppsRepository
import com.lordcb.homelauncher.data.GeoResult
import com.lordcb.homelauncher.data.HaEntity
import com.lordcb.homelauncher.data.HomeAssistantClient
import com.lordcb.homelauncher.data.LauncherApp
import com.lordcb.homelauncher.data.LauncherSettings
import com.lordcb.homelauncher.data.SettingsStore
import com.lordcb.homelauncher.data.Weather
import com.lordcb.homelauncher.data.WeatherKey
import com.lordcb.homelauncher.data.WeatherRepository
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject

data class DashboardItem(val entityId: String, val entity: HaEntity?)

data class SensorGroup(val area: String?, val items: List<DashboardItem>)

data class LightGroup(val area: String?, val lights: List<HaEntity>)

/** Which list the device picker edits. */
enum class PickTarget { QuickButtons, Sensors }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    val settings: StateFlow<LauncherSettings> = store.settings

    val ha = HomeAssistantClient(viewModelScope)
    private val weatherRepo = WeatherRepository(OkHttpClient())
    private val snapshotHttp = OkHttpClient()
    private val appsRepo = AppsRepository(app)

    private val allApps = MutableStateFlow<List<LauncherApp>>(emptyList())

    val apps: StateFlow<List<LauncherApp>> = combine(allApps, settings) { all, s ->
        val position = s.appOrder.withIndex().associate { it.value to it.index }
        all.filter { it.packageName !in s.hiddenApps }
            .sortedWith(compareBy<LauncherApp> { position[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<LauncherApp>> = combine(allApps, settings) { all, s ->
        all.filter { it.packageName in s.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dashboard: StateFlow<List<DashboardItem>> = combine(settings, ha.states) { s, states ->
        s.entities.map { DashboardItem(it, states[it]) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Chosen sensors grouped by their Home Assistant area, areas in HA's order. */
    val sensorGroups: StateFlow<List<SensorGroup>> = combine(settings, ha.states, ha.areas) { s, states, areas ->
        val items = s.sensors.map { DashboardItem(it, states[it]) }
        groupByArea(items, areas.order) { areas.areaOfEntity[it.entityId] }
            .map { (area, list) -> SensorGroup(area, list) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every light in Home Assistant, grouped by area. */
    val lightGroups: StateFlow<List<LightGroup>> = combine(ha.states, ha.areas) { states, areas ->
        val lights = states.values.filter { it.domain == "light" }.sortedBy { it.name.lowercase() }
        groupByArea(lights, areas.order) { areas.areaOfEntity[it.entityId] }
            .map { (area, list) -> LightGroup(area, list) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cameras: StateFlow<List<HaEntity>> = ha.states.map { states ->
        states.values.filter { it.domain == "camera" }.sortedBy { it.name.lowercase() }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T> groupByArea(items: List<T>, order: List<String>, areaOf: (T) -> String?): List<Pair<String?, List<T>>> {
        val grouped = items.groupBy(areaOf)
        val rank = order.withIndex().associate { it.value to it.index }
        return grouped.entries
            .sortedBy { (area, _) -> if (area == null) Int.MAX_VALUE else rank[area] ?: (Int.MAX_VALUE - 1) }
            .map { it.key to it.value }
    }

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    private val _cityResults = MutableStateFlow<List<GeoResult>>(emptyList())
    val cityResults: StateFlow<List<GeoResult>> = _cityResults.asStateFlow()

    private var appsJob: Job? = null
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshApps()
    }

    init {
        viewModelScope.launch {
            settings.map { it.entities.toSet() + it.sensors }.distinctUntilChanged().collect { ha.watched = it }
        }

        viewModelScope.launch {
            settings.map { it.haUrl to it.haToken }.distinctUntilChanged().collect { (url, token) ->
                ha.connect(url, token)
            }
        }

        viewModelScope.launch {
            combine(settings, ha.location) { s, haLoc ->
                val lat = s.latitude
                val lon = s.longitude
                if (!s.useHaLocation && lat != null && lon != null) {
                    WeatherKey(lat, lon, s.locationName, s.fahrenheit)
                } else {
                    haLoc?.let { WeatherKey(it.latitude, it.longitude, it.name, s.fahrenheit) }
                }
            }.distinctUntilChanged().collectLatest { key ->
                if (key == null) {
                    _weather.value = null
                    return@collectLatest
                }
                while (true) {
                    val ok = try {
                        _weather.value = weatherRepo.forecast(key)
                        _weatherError.value = null
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _weatherError.value = e.message ?: "Weather unavailable"
                        false
                    }
                    delay(if (ok) 15 * 60_000L else 60_000L)
                }
            }
        }

        refreshApps()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    fun refreshApps() {
        appsJob?.cancel()
        appsJob = viewModelScope.launch { allApps.value = appsRepo.load() }
    }

    // ---- Apps ----

    fun launchIntent(app: LauncherApp): Intent? = appsRepo.launchIntent(app.packageName)

    fun moveApp(app: LauncherApp, delta: Int) {
        val order = apps.value.map { it.packageName }.toMutableList()
        val from = order.indexOf(app.packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from == to) return
        order.removeAt(from)
        order.add(to, app.packageName)
        store.update { it.copy(appOrder = order) }
    }

    fun hideApp(app: LauncherApp) = store.update { it.copy(hiddenApps = it.hiddenApps + app.packageName) }
    fun unhideApp(app: LauncherApp) = store.update { it.copy(hiddenApps = it.hiddenApps - app.packageName) }

    // ---- Home Assistant ----

    fun saveHomeAssistant(url: String, token: String) =
        store.update { it.copy(haUrl = url.trim(), haToken = token.trim()) }

    fun setHaUrl(url: String) = store.update { it.copy(haUrl = url.trim()) }
    fun setHaToken(token: String) = store.update { it.copy(haToken = token.trim()) }

    fun reconnect() = settings.value.let { ha.connect(it.haUrl, it.haToken) }

    fun isSelected(target: PickTarget, s: LauncherSettings, entityId: String) = when (target) {
        PickTarget.QuickButtons -> entityId in s.entities
        PickTarget.Sensors -> entityId in s.sensors
    }

    fun toggleEntitySelected(target: PickTarget, entityId: String) = store.update {
        when (target) {
            PickTarget.QuickButtons ->
                if (entityId in it.entities) it.copy(entities = it.entities - entityId)
                else it.copy(entities = it.entities + entityId)
            PickTarget.Sensors ->
                if (entityId in it.sensors) it.copy(sensors = it.sensors - entityId)
                else it.copy(sensors = it.sensors + entityId)
        }
    }

    // ---- Lights ----

    fun toggleLight(e: HaEntity) = ha.callService("light", "toggle", e.entityId)

    /** hue 0-360, saturation 0-100 */
    fun setLightColor(e: HaEntity, hue: Float, saturation: Float) = ha.callService(
        "light", "turn_on", e.entityId,
        JSONObject().put("hs_color", org.json.JSONArray().put(hue.toDouble()).put(saturation.toDouble())),
    )

    fun setLightTemperature(e: HaEntity, kelvin: Int) {
        val k = kelvin.coerceIn(e.minKelvin ?: 2000, e.maxKelvin ?: 6500)
        ha.callService("light", "turn_on", e.entityId, JSONObject().put("color_temp_kelvin", k))
    }

    // ---- Cameras ----

    fun selectCamera(entityId: String) = store.update { it.copy(lastCamera = entityId) }

    suspend fun cameraStreamUrl(entityId: String): String? = ha.cameraStreamUrl(entityId)

    /** Fetches a still from the camera, decoded down to roughly [targetWidth] px wide. */
    suspend fun cameraSnapshot(picturePath: String, targetWidth: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val url = ha.absoluteUrl(picturePath) ?: return@withContext null
        try {
            val bytes = snapshotHttp.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer ${ha.token}").build()
            ).execute().use { if (it.isSuccessful) it.body?.bytes() else null } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun removeEntity(entityId: String) = store.update { it.copy(entities = it.entities - entityId) }

    fun moveEntity(entityId: String, delta: Int) = store.update {
        val list = it.entities.toMutableList()
        val from = list.indexOf(entityId)
        if (from < 0) return@update it
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.removeAt(from)
        list.add(to, entityId)
        it.copy(entities = list)
    }

    /** What a single press on a tile does. Returns false when the tile has no quick action. */
    fun activate(e: HaEntity): Boolean {
        val id = e.entityId
        when (e.domain) {
            "light", "switch", "fan", "input_boolean", "automation", "siren", "humidifier", "cover" ->
                ha.callService(e.domain, "toggle", id)
            "scene", "script" -> ha.callService(e.domain, "turn_on", id)
            "button", "input_button" -> ha.callService(e.domain, "press", id)
            "media_player" -> ha.callService("media_player", "media_play_pause", id)
            "climate" -> ha.callService("climate", if (e.state == "off") "turn_on" else "turn_off", id)
            "vacuum" -> ha.callService("vacuum", if (e.state == "cleaning") "return_to_base" else "start", id)
            else -> return false
        }
        return true
    }

    fun callService(domain: String, service: String, entityId: String, data: JSONObject? = null) =
        ha.callService(domain, service, entityId, data)

    fun setBrightness(e: HaEntity, percent: Int) =
        ha.callService("light", "turn_on", e.entityId, JSONObject().put("brightness_pct", percent))

    fun adjustTemperature(e: HaEntity, delta: Double) {
        val target = e.targetTemperature ?: return
        ha.callService("climate", "set_temperature", e.entityId, JSONObject().put("temperature", target + delta))
    }

    // ---- Weather ----

    fun setUseHaLocation(use: Boolean) = store.update { it.copy(useHaLocation = use) }
    fun setFahrenheit(f: Boolean) = store.update { it.copy(fahrenheit = f) }

    fun searchCity(query: String) {
        viewModelScope.launch {
            _cityResults.value = try { weatherRepo.searchCity(query) } catch (e: Exception) { emptyList() }
        }
    }

    fun chooseCity(city: GeoResult) {
        store.update {
            it.copy(useHaLocation = false, locationName = city.name, latitude = city.latitude, longitude = city.longitude)
        }
        _cityResults.value = emptyList()
    }
}
