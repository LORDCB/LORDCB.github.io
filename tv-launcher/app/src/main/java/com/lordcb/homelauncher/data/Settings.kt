package com.lordcb.homelauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LauncherSettings(
    val haUrl: String = "",
    val haToken: String = "",
    /** Entity ids shown on the home screen, in display order. */
    val entities: List<String> = emptyList(),
    /** When true (or no manual location is set) weather uses Home Assistant's home location. */
    val useHaLocation: Boolean = true,
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fahrenheit: Boolean = false,
    val appOrder: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        _settings.update(transform)
        save(_settings.value)
    }

    private fun load() = LauncherSettings(
        haUrl = prefs.getString("ha_url", "") ?: "",
        haToken = prefs.getString("ha_token", "") ?: "",
        entities = prefs.getString("entities", "").toList(),
        useHaLocation = prefs.getBoolean("use_ha_location", true),
        locationName = prefs.getString("location_name", "") ?: "",
        latitude = prefs.getString("latitude", null)?.toDoubleOrNull(),
        longitude = prefs.getString("longitude", null)?.toDoubleOrNull(),
        fahrenheit = prefs.getBoolean("fahrenheit", false),
        appOrder = prefs.getString("app_order", "").toList(),
        hiddenApps = prefs.getString("hidden_apps", "").toList().toSet(),
    )

    @Synchronized
    private fun save(s: LauncherSettings) {
        prefs.edit()
            .putString("ha_url", s.haUrl)
            .putString("ha_token", s.haToken)
            .putString("entities", s.entities.joinToString("\n"))
            .putBoolean("use_ha_location", s.useHaLocation)
            .putString("location_name", s.locationName)
            .putString("latitude", s.latitude?.toString())
            .putString("longitude", s.longitude?.toString())
            .putBoolean("fahrenheit", s.fahrenheit)
            .putString("app_order", s.appOrder.joinToString("\n"))
            .putString("hidden_apps", s.hiddenApps.joinToString("\n"))
            .apply()
    }

    private fun String?.toList(): List<String> =
        this?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
