package com.lordcb.homelauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lordcb.homelauncher.LauncherViewModel
import com.lordcb.homelauncher.data.ConfigServer
import com.lordcb.homelauncher.data.HaStatus
import kotlinx.coroutines.delay

private enum class EditField { Url, Token, City }

@Composable
fun SettingsScreen(vm: LauncherViewModel, onOpenPicker: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.ha.status.collectAsStateWithLifecycle()
    val haLocation by vm.ha.location.collectAsStateWithLifecycle()
    val cities by vm.cityResults.collectAsStateWithLifecycle()
    val hidden by vm.hiddenApps.collectAsStateWithLifecycle()
    val weatherError by vm.weatherError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditField?>(null) }
    val first = remember { FocusRequester() }

    // Phone setup page, only while this screen is open.
    val currentSettings by rememberUpdatedState(settings)
    var serverRunning by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val server = ConfigServer(
            currentUrl = { currentSettings.haUrl },
            onSave = { url, token -> vm.saveHomeAssistant(url, token) },
        )
        serverRunning = server.start()
        onDispose { server.stop() }
    }
    val ip = remember { ConfigServer.localIpAddress(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background),
        contentPadding = PaddingValues(horizontal = 120.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Settings", fontSize = 40.sp, fontWeight = FontWeight.Light, color = Palette.TextPrimary)
            Spacer(Modifier.height(8.dp))
        }

        item { Header("Home Assistant") }
        if (serverRunning && ip != null) {
            item {
                InfoCard(
                    icon = Icons.Rounded.Smartphone,
                    title = "Set up from your phone",
                    body = "On a phone on the same Wi-Fi, open  http://$ip:${ConfigServer.PORT}  and paste your URL and token there.",
                )
            }
        }
        item {
            SettingRow(
                title = "Server address",
                value = settings.haUrl.ifBlank { "Not set" },
                modifier = Modifier.focusRequester(first),
                onClick = { editing = EditField.Url },
            )
        }
        item {
            SettingRow(
                title = "Long-lived access token",
                value = if (settings.haToken.isBlank()) "Not set" else "••••••••" + settings.haToken.takeLast(4),
                onClick = { editing = EditField.Token },
            )
        }
        item {
            val (label, color) = when (val s = status) {
                HaStatus.Connected -> "Connected" to Palette.Good
                HaStatus.Connecting -> "Connecting…" to Palette.Warm
                HaStatus.NotConfigured -> "Not configured" to Palette.TextSecondary
                is HaStatus.Error -> s.message to Palette.Bad
            }
            SettingRow(
                title = "Status",
                value = label,
                dot = color,
                trailingIcon = Icons.Rounded.Refresh,
                onClick = { vm.reconnect() },
            )
        }
        item {
            SettingRow(
                title = "Devices on the home screen",
                value = "${settings.entities.size} selected",
                trailingIcon = Icons.Rounded.ChevronRight,
                onClick = onOpenPicker,
            )
        }

        item { Header("Weather") }
        item {
            ToggleRow(
                title = "Use Home Assistant's home location",
                subtitle = haLocation?.let { "${it.name} (${"%.2f".format(it.latitude)}, ${"%.2f".format(it.longitude)})" }
                    ?: "Available once Home Assistant is connected",
                checked = settings.useHaLocation,
                onToggle = { vm.setUseHaLocation(!settings.useHaLocation) },
            )
        }
        item {
            SettingRow(
                title = "City",
                value = if (!settings.useHaLocation && settings.locationName.isNotBlank()) settings.locationName else "Search for a city",
                trailingIcon = Icons.Rounded.Search,
                onClick = { editing = EditField.City },
            )
        }
        items(cities, key = { "${it.latitude},${it.longitude}" }) { city ->
            SettingRow(
                title = city.name,
                value = city.detail,
                trailingIcon = Icons.Rounded.ChevronRight,
                modifier = Modifier.padding(start = 32.dp),
                onClick = { vm.chooseCity(city) },
            )
        }
        item {
            ToggleRow(
                title = "Show temperatures in °F",
                subtitle = null,
                checked = settings.fahrenheit,
                onToggle = { vm.setFahrenheit(!settings.fahrenheit) },
            )
        }
        weatherError?.let { err ->
            item { SecondaryText("Weather: $err", modifier = Modifier.padding(start = 8.dp)) }
        }

        item { Header("Apps") }
        item {
            SettingRow(
                title = "Apps layout",
                value = if (settings.appsGrid) "Grid — 6 per row" else "Row — scroll sideways",
                trailingIcon = if (settings.appsGrid) Icons.Rounded.GridView else Icons.Rounded.ViewStream,
                onClick = { vm.setAppsGrid(!settings.appsGrid) },
            )
        }

        if (hidden.isNotEmpty()) {
            item { Header("Hidden apps") }
            items(hidden, key = { it.packageName }) { app ->
                SettingRow(title = app.label, value = "Show on home screen", onClick = { vm.unhideApp(app) })
            }
        }

        item { Header("System") }
        item {
            SettingRow(
                title = "Google TV settings",
                value = "Network, display, accounts and more",
                trailingIcon = Icons.Rounded.ChevronRight,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {}
                },
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            SecondaryText(
                "Tip: hold the select button on an app or device tile to reorder, hide or adjust it.",
                maxLines = 2,
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }

    when (editing) {
        EditField.Url -> TextEditDialog(
            title = "Home Assistant address",
            initial = settings.haUrl,
            hint = "http://homeassistant.local:8123",
            onConfirm = { vm.setHaUrl(it) },
            onDismiss = { editing = null },
        )
        EditField.Token -> TextEditDialog(
            title = "Long-lived access token",
            initial = settings.haToken,
            hint = "Paste or type your token",
            keyboardType = KeyboardType.Ascii,
            onConfirm = { vm.setHaToken(it) },
            onDismiss = { editing = null },
        )
        EditField.City -> TextEditDialog(
            title = "Search for a city",
            initial = "",
            hint = "e.g. London",
            keyboardType = KeyboardType.Text,
            onConfirm = { vm.searchCity(it) },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text.uppercase(),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        color = Palette.Accent,
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp, start = 8.dp),
    )
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        colors = SurfaceDefaults.colors(containerColor = Color(0xFF13263A), contentColor = Palette.TextPrimary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                SecondaryText(body, maxLines = 2)
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    trailingIcon: ImageVector? = null,
) {
    FocusCard(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.02f) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dot != null) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(8.dp))
                    }
                    SecondaryText(value)
                }
            }
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = LocalContentColor.current.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onToggle: () -> Unit) {
    FocusCard(onClick = onToggle, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) SecondaryText(subtitle)
            }
            Icon(
                if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (checked) "On" else "Off",
                tint = if (checked) Palette.Good else LocalContentColor.current.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
