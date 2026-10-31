package com.lordcb.homelauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.lordcb.homelauncher.DashboardItem
import com.lordcb.homelauncher.LauncherViewModel
import com.lordcb.homelauncher.data.HaEntity
import com.lordcb.homelauncher.data.HaStatus
import com.lordcb.homelauncher.data.LauncherApp
import com.lordcb.homelauncher.data.Weather
import com.lordcb.homelauncher.data.weatherKind
import com.lordcb.homelauncher.data.weatherLabel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val EdgePadding = 56.dp

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    focusTrigger: Int,
    onOpenSettings: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val status by vm.ha.status.collectAsStateWithLifecycle()

    var appMenu by remember { mutableStateOf<LauncherApp?>(null) }
    var entityMenu by remember { mutableStateOf<DashboardItem?>(null) }
    val firstApp = remember { FocusRequester() }

    val kind = weather?.let { weatherKind(it.code) }
    val glow = weatherGlow(kind, weather?.isDay ?: true)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BackgroundTop, Palette.Background)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.88f, 0f),
                        radius = size.width * 0.7f,
                    )
                )
            }
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 32.dp)) {
            TopBar(weather, onOpenSettings)
            Spacer(Modifier.weight(1f))

            SectionTitle("Home", trailing = statusLabel(status))
            HomeRow(
                items = dashboard,
                status = status,
                onActivate = { item ->
                    val e = item.entity
                    if (e == null || e.domain == "lock" || !vm.activate(e)) entityMenu = item
                },
                onMenu = { entityMenu = it },
                onOpenSettings = onOpenSettings,
                onOpenPicker = onOpenPicker,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Apps")
            LazyRow(
                contentPadding = PaddingValues(horizontal = EdgePadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                    AppCard(
                        app = app,
                        modifier = if (i == 0) Modifier.focusRequester(firstApp) else Modifier,
                        onClick = {
                            vm.launchIntent(app)?.let {
                                try { context.startActivity(it) } catch (_: ActivityNotFoundException) {}
                            }
                        },
                        onLongClick = { appMenu = app },
                    )
                }
            }
        }
    }

    LaunchedEffect(apps.isNotEmpty(), focusTrigger) {
        if (apps.isNotEmpty()) {
            delay(150)
            runCatching { firstApp.requestFocus() }
        }
    }

    appMenu?.let { app ->
        OptionsDialog(
            title = app.label,
            subtitle = null,
            onDismiss = { appMenu = null },
            options = listOf(
                DialogOption("Move left", Icons.Rounded.ArrowBack) { vm.moveApp(app, -1) },
                DialogOption("Move right", Icons.Rounded.ArrowForward) { vm.moveApp(app, 1) },
                DialogOption("Move to front", Icons.Rounded.Home) { vm.moveApp(app, -1000) },
                DialogOption("Hide from home", Icons.Rounded.VisibilityOff) { vm.hideApp(app) },
                DialogOption("App info", Icons.Rounded.Info) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {}
                },
            ),
        )
    }

    entityMenu?.let { item ->
        val e = item.entity
        val options = buildList {
            if (e != null) {
                when (e.domain) {
                    "light" -> {
                        add(DialogOption("Turn off", Icons.Rounded.PowerSettingsNew) { vm.callService("light", "turn_off", e.entityId) })
                        listOf(25, 50, 75, 100).forEach { pct ->
                            add(DialogOption("Brightness $pct%") { vm.setBrightness(e, pct) })
                        }
                    }
                    "lock" -> {
                        add(DialogOption("Lock", Icons.Rounded.Lock) { vm.callService("lock", "lock", e.entityId) })
                        add(DialogOption("Unlock", Icons.Rounded.LockOpen) { vm.callService("lock", "unlock", e.entityId) })
                    }
                    "climate" -> if (e.targetTemperature != null) {
                        add(DialogOption("Warmer (+0.5°)", Icons.Rounded.Add) { vm.adjustTemperature(e, 0.5) })
                        add(DialogOption("Cooler (−0.5°)", Icons.Rounded.Remove) { vm.adjustTemperature(e, -0.5) })
                    }
                    "cover" -> {
                        add(DialogOption("Open") { vm.callService("cover", "open_cover", e.entityId) })
                        add(DialogOption("Close") { vm.callService("cover", "close_cover", e.entityId) })
                    }
                }
            }
            add(DialogOption("Move left", Icons.Rounded.ArrowBack) { vm.moveEntity(item.entityId, -1) })
            add(DialogOption("Move right", Icons.Rounded.ArrowForward) { vm.moveEntity(item.entityId, 1) })
            add(DialogOption("Remove from home", Icons.Rounded.Delete) { vm.removeEntity(item.entityId) })
        }
        OptionsDialog(
            title = e?.name ?: item.entityId,
            subtitle = e?.let { stateText(it) },
            options = options,
            onDismiss = { entityMenu = null },
        )
    }
}

@Composable
private fun TopBar(weather: Weather?, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L * (60 - value.second).coerceAtLeast(1))
        }
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Good night"
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = EdgePadding), verticalAlignment = Alignment.Top) {
        Column {
            Text(greeting, fontSize = 20.sp, color = Palette.TextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    now.format(DateTimeFormatter.ofPattern(timePattern)),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Light,
                    color = Palette.TextPrimary,
                    lineHeight = 88.sp,
                )
                if (!DateFormat.is24HourFormat(context)) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("a")).uppercase(),
                        fontSize = 24.sp,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                    )
                }
            }
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                fontSize = 22.sp,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        WeatherPanel(weather)
        Spacer(Modifier.width(28.dp))
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier.size(52.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Palette.TextPrimary,
                focusedContainerColor = Palette.Focused,
                focusedContentColor = Palette.OnFocused,
            ),
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                modifier = Modifier.align(Alignment.Center).size(26.dp),
            )
        }
    }
}

@Composable
private fun WeatherPanel(weather: Weather?) {
    if (weather == null) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 12.dp)) {
            Text("Weather", fontSize = 20.sp, color = Palette.TextPrimary)
            Text(
                "Connect Home Assistant or pick a city in Settings",
                fontSize = 15.sp,
                color = Palette.TextSecondary,
            )
        }
        return
    }
    val kind = weatherKind(weather.code)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            weatherIcon(kind, weather.isDay),
            contentDescription = null,
            tint = weatherTint(kind, weather.isDay),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "${weather.temperature.roundToInt()}°",
                fontSize = 60.sp,
                fontWeight = FontWeight.Light,
                color = Palette.TextPrimary,
                lineHeight = 62.sp,
            )
            Text(
                "${weatherLabel(weather.code)} · Feels ${weather.feelsLike.roundToInt()}°",
                fontSize = 16.sp,
                color = Palette.TextPrimary,
            )
            if (weather.locationName.isNotBlank()) {
                Text(weather.locationName, fontSize = 14.sp, color = Palette.TextSecondary)
            }
        }
        Spacer(Modifier.width(32.dp))
        Box(Modifier.width(1.dp).height(96.dp).background(Color.White.copy(alpha = 0.12f)))
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            weather.daily.drop(1).take(5).forEach { day ->
                val dk = weatherKind(day.code)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        fontSize = 15.sp,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        weatherIcon(dk, true),
                        contentDescription = weatherLabel(day.code),
                        tint = weatherTint(dk, true),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${day.max.roundToInt()}°", fontSize = 16.sp, color = Palette.TextPrimary)
                    Text("${day.min.roundToInt()}°", fontSize = 14.sp, color = Palette.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: Pair<String, Color>? = null) {
    Row(
        Modifier.padding(horizontal = EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
        if (trailing != null) {
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(trailing.second))
            Spacer(Modifier.width(8.dp))
            Text(trailing.first, fontSize = 15.sp, color = Palette.TextSecondary)
        }
    }
}

private fun statusLabel(status: HaStatus): Pair<String, Color>? = when (status) {
    HaStatus.Connected -> null
    HaStatus.Connecting -> "Connecting…" to Palette.Warm
    HaStatus.NotConfigured -> null
    is HaStatus.Error -> status.message to Palette.Bad
}

@Composable
private fun HomeRow(
    items: List<DashboardItem>,
    status: HaStatus,
    onActivate: (DashboardItem) -> Unit,
    onMenu: (DashboardItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EdgePadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (status == HaStatus.NotConfigured) {
            item {
                PromptTile(
                    title = "Connect Home Assistant",
                    body = "Add your server in Settings to control your home from here",
                    onClick = onOpenSettings,
                )
            }
            return@LazyRow
        }
        itemsIndexed(items, key = { _, it -> it.entityId }) { _, item ->
            EntityTile(item, onClick = { onActivate(item) }, onLongClick = { onMenu(item) })
        }
        item {
            if (items.isEmpty()) {
                PromptTile(
                    title = "Add devices",
                    body = "Choose lights, switches, scenes and sensors to show here",
                    onClick = onOpenPicker,
                )
            } else {
                FocusCard(onClick = onOpenPicker, modifier = Modifier.size(width = 104.dp, height = 112.dp)) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text("Add", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptTile(title: String, body: String, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.size(width = 420.dp, height = 112.dp)) {
        Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                SecondaryText(body, maxLines = 2)
            }
        }
    }
}

@Composable
private fun EntityTile(item: DashboardItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val e = item.entity
    val active = e?.isActive == true
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        container = if (active) Palette.CardActive else Palette.Card,
        modifier = Modifier.size(width = 184.dp, height = 112.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (active) Palette.Warm else LocalContentColor.current.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    entityIcon(e, item.entityId),
                    contentDescription = null,
                    tint = if (active) Palette.OnFocused else LocalContentColor.current,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                e?.name ?: item.entityId,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SecondaryText(if (e == null) "Unavailable" else stateText(e), size = 14)
        }
    }
}

fun stateText(e: HaEntity): String {
    if (e.isUnavailable) return "Unavailable"
    return when (e.domain) {
        "light" -> if (e.state == "on" && e.brightness != null) {
            "On · ${(e.brightness * 100 / 255.0).roundToInt()}%"
        } else pretty(e.state)
        "sensor", "number", "input_number" -> listOfNotNull(e.state, e.unit).joinToString(" ")
        "climate" -> buildString {
            e.currentTemperature?.let { append("${formatNum(it)}°") }
            e.targetTemperature?.let { append(" → ${formatNum(it)}°") }
            if (isEmpty()) append(pretty(e.state))
        }
        "scene" -> "Scene"
        "script" -> if (e.state == "on") "Running" else "Script"
        "button", "input_button" -> "Press"
        "media_player" -> e.mediaTitle?.let { "${pretty(e.state)} · $it" } ?: pretty(e.state)
        else -> pretty(e.state)
    }
}

private fun formatNum(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)

private fun pretty(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun AppCard(
    app: LauncherApp,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(width = 196.dp, height = 110.dp),
            interactionSource = interaction,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = focusedBorder(14),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.Card,
                contentColor = Palette.TextPrimary,
                focusedContainerColor = Palette.Card,
                focusedContentColor = Palette.TextPrimary,
            ),
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (app.icon != null) {
                        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        app.label,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            app.label,
            fontSize = 15.sp,
            color = if (focused) Palette.TextPrimary else Color.Transparent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(196.dp),
        )
    }
}
