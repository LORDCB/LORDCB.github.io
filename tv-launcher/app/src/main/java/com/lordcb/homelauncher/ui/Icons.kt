package com.lordcb.homelauncher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Window
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lordcb.homelauncher.data.HaEntity
import com.lordcb.homelauncher.data.WeatherKind

fun entityIcon(e: HaEntity?, entityId: String): ImageVector {
    val domain = e?.domain ?: entityId.substringBefore('.')
    return when (domain) {
        "light" -> Icons.Rounded.Lightbulb
        "switch" -> Icons.Rounded.Power
        "fan" -> Icons.Rounded.Air
        "climate" -> Icons.Rounded.Thermostat
        "cover" -> Icons.Rounded.Window
        "lock" -> if (e?.state == "unlocked") Icons.Rounded.LockOpen else Icons.Rounded.Lock
        "scene" -> Icons.Rounded.Palette
        "script", "automation" -> Icons.Rounded.PlayArrow
        "button", "input_button" -> Icons.Rounded.SmartButton
        "media_player" -> Icons.Rounded.Tv
        "camera" -> Icons.Rounded.Videocam
        "vacuum" -> Icons.Rounded.CleaningServices
        "input_boolean" -> Icons.Rounded.ToggleOn
        "sensor" -> when (e?.deviceClass) {
            "temperature" -> Icons.Rounded.Thermostat
            "humidity", "moisture" -> Icons.Rounded.WaterDrop
            "power", "energy", "current", "voltage", "battery" -> Icons.Rounded.Bolt
            else -> Icons.Rounded.Sensors
        }
        "binary_sensor" -> Icons.Rounded.Sensors
        else -> Icons.Rounded.Home
    }
}

fun weatherIcon(kind: WeatherKind, isDay: Boolean): ImageVector = when (kind) {
    WeatherKind.Clear -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    WeatherKind.PartlyCloudy -> if (isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    WeatherKind.Cloudy -> Icons.Rounded.Cloud
    WeatherKind.Fog -> Icons.Rounded.Dehaze
    WeatherKind.Drizzle -> Icons.Rounded.Grain
    WeatherKind.Rain -> Icons.Rounded.Umbrella
    WeatherKind.Snow -> Icons.Rounded.AcUnit
    WeatherKind.Thunder -> Icons.Rounded.FlashOn
}

fun weatherTint(kind: WeatherKind, isDay: Boolean): Color = when (kind) {
    WeatherKind.Clear -> if (isDay) Color(0xFFFFC65C) else Color(0xFFB9C4FF)
    WeatherKind.PartlyCloudy -> if (isDay) Color(0xFFFFD98A) else Color(0xFFB9C4FF)
    WeatherKind.Cloudy, WeatherKind.Fog -> Color(0xFFC5CFDB)
    WeatherKind.Drizzle, WeatherKind.Rain -> Color(0xFF7CC4FF)
    WeatherKind.Snow -> Color(0xFFE3F2FD)
    WeatherKind.Thunder -> Color(0xFFFFE082)
}

/** Soft glow colour behind the home screen, so the room mood follows the weather. */
fun weatherGlow(kind: WeatherKind?, isDay: Boolean): Color = when {
    kind == null -> Color(0xFF1B2B44)
    !isDay -> Color(0xFF1E2350)
    kind == WeatherKind.Clear || kind == WeatherKind.PartlyCloudy -> Color(0xFF4A3A1C)
    kind == WeatherKind.Rain || kind == WeatherKind.Drizzle || kind == WeatherKind.Thunder -> Color(0xFF15344A)
    kind == WeatherKind.Snow -> Color(0xFF2E3F52)
    else -> Color(0xFF26303D)
}
