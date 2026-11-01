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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lordcb.homelauncher.DashboardItem
import com.lordcb.homelauncher.LauncherViewModel
import com.lordcb.homelauncher.LightGroup
import com.lordcb.homelauncher.PickTarget
import com.lordcb.homelauncher.SensorGroup
import com.lordcb.homelauncher.data.HaEntity
import com.lordcb.homelauncher.data.HaStatus
import com.lordcb.homelauncher.data.LauncherApp
import com.lordcb.homelauncher.data.Weather
import com.lordcb.homelauncher.data.weatherKind
import com.lordcb.homelauncher.data.weatherLabel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Layout is sized for the 960x540dp screen Google TV uses at 1080p.
private val Edge = 48.dp
private val PanelColor = Color(0xFF1A212B)
private const val APPS_PER_ROW = 6

private val tabs = listOf("Home", "Cameras", "Lights", "Apps")

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    focusTrigger: Int,
    onOpenSettings: () -> Unit,
    onOpenPicker: (PickTarget) -> Unit,
    onOpenCamera: (String) -> Unit,
) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    val quick by vm.dashboard.collectAsStateWithLifecycle()
    val status by vm.ha.status.collectAsStateWithLifecycle()

    var appMenu by remember { mutableStateOf<LauncherApp?>(null) }
    var quickMenu by remember { mutableStateOf<DashboardItem?>(null) }
    var lightPanel by remember { mutableStateOf<String?>(null) }
    val firstApp = remember { FocusRequester() }

    val launch: (LauncherApp) -> Unit = { app ->
        vm.launchIntent(app)?.let {
            try { context.startActivity(it) } catch (_: ActivityNotFoundException) {}
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .padding(top = 20.dp, bottom = 14.dp)
    ) {
        Header(
            tab = tab,
            onTab = onTab,
            quick = quick,
            status = status,
            onQuick = { item ->
                val e = item.entity
                if (e == null || e.domain == "lock" || !vm.activate(e)) quickMenu = item
            },
            onQuickMenu = { item -> if (item.entity?.domain == "light") lightPanel = item.entityId else quickMenu = item },
            onAddQuick = { onOpenPicker(PickTarget.QuickButtons) },
            onOpenSettings = onOpenSettings,
        )
        Spacer(Modifier.height(14.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeBody(vm, status, onOpenCamera, onOpenSettings)
                1 -> CamerasTab(vm, onOpenCamera)
                2 -> LightsTab(vm, onPanel = { lightPanel = it })
                else -> AppsGrid(apps, firstApp, launch, onMenu = { appMenu = it })
            }
        }

        if (tab == 0) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Edge, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                    AppCard(
                        app = app,
                        modifier = Modifier
                            .size(width = 128.dp, height = 72.dp)
                            .let { if (i == 0) it.focusRequester(firstApp) else it },
                        onClick = { launch(app) },
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

    lightPanel?.let { id ->
        val isQuick = quick.any { it.entityId == id }
        LightPanel(
            vm = vm,
            entityId = id,
            onDismiss = { lightPanel = null },
            extraOptions = if (isQuick) quickOrderOptions(vm, id) else emptyList(),
        )
    }

    quickMenu?.let { item ->
        val e = item.entity
        val options = buildList {
            if (e != null) {
                when (e.domain) {
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
            addAll(quickOrderOptions(vm, item.entityId))
        }
        OptionsDialog(
            title = e?.name ?: item.entityId,
            subtitle = e?.let { stateText(it) },
            options = options,
            onDismiss = { quickMenu = null },
        )
    }
}

private fun quickOrderOptions(vm: LauncherViewModel, entityId: String) = listOf(
    DialogOption("Move left", Icons.Rounded.ArrowBack) { vm.moveEntity(entityId, -1) },
    DialogOption("Move right", Icons.Rounded.ArrowForward) { vm.moveEntity(entityId, 1) },
    DialogOption("Remove", Icons.Rounded.Delete) { vm.removeEntity(entityId) },
)

// ---------------------------------------------------------------- Header

@Composable
private fun Header(
    tab: Int,
    onTab: (Int) -> Unit,
    quick: List<DashboardItem>,
    status: HaStatus,
    onQuick: (DashboardItem) -> Unit,
    onQuickMenu: (DashboardItem) -> Unit,
    onAddQuick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L * (60 - value.second).coerceAtLeast(1))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Edge).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, label ->
            TabItem(label, selected = tab == i, onSelect = { onTab(i) })
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))

        when {
            status is HaStatus.Error -> StatusChip(status.message)
            status == HaStatus.Connecting -> StatusChip("Connecting…")
        }
        if (status != HaStatus.NotConfigured) {
            quick.take(3).forEach { item ->
                QuickButton(item, onClick = { onQuick(item) }, onLongClick = { onQuickMenu(item) })
                Spacer(Modifier.width(10.dp))
            }
            if (quick.isEmpty() && status == HaStatus.Connected) {
                PillButton("Add buttons", Icons.Rounded.Add, onClick = onAddQuick)
                Spacer(Modifier.width(10.dp))
            }
        }

        Surface(
            onClick = onOpenSettings,
            modifier = Modifier.size(42.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Palette.TextPrimary,
                focusedContainerColor = Palette.Focused,
                focusedContentColor = Palette.OnFocused,
            ),
        ) {
            Icon(Icons.Rounded.Settings, "Settings", Modifier.align(Alignment.Center).size(22.dp))
        }
        Spacer(Modifier.width(18.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")),
            fontSize = 28.sp,
            color = Palette.TextPrimary,
        )
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) Palette.TextPrimary else Palette.TextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            focusedContentColor = Palette.TextPrimary,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontSize = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.width(26.dp).height(2.dp)
                    .background(if (selected) LocalContentColor.current else Color.Transparent)
            )
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Palette.Bad,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 200.dp).padding(end = 12.dp),
    )
}

@Composable
private fun QuickButton(item: DashboardItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val e = item.entity
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    PillSurface(onClick = onClick, onLongClick = onLongClick, interaction = interaction) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                entityIcon(e, item.entityId),
                contentDescription = null,
                tint = if (e?.isActive == true && !focused) Palette.Warm else LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                e?.name ?: item.entityId,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    PillSurface(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PillSurface(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interaction: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            contentColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Focused,
            focusedContentColor = Palette.OnFocused,
        ),
    ) { content() }
}

// ---------------------------------------------------------------- Home tab

@Composable
private fun HomeBody(
    vm: LauncherViewModel,
    status: HaStatus,
    onOpenCamera: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val weather by vm.weather.collectAsStateWithLifecycle()
    val sensors by vm.sensorGroups.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxSize().padding(horizontal = Edge),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CameraPanel(vm, status, onOpenCamera, onOpenSettings, Modifier.weight(1.9f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherCard(weather, Modifier.fillMaxWidth())
            SensorsCard(sensors, status, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun CameraPanel(
    vm: LauncherViewModel,
    status: HaStatus,
    onOpenCamera: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(16.dp)

    if (status == HaStatus.NotConfigured || cameras.isEmpty()) {
        FocusCard(onClick = onOpenSettings, container = PanelColor, modifier = modifier, focusedScale = 1f) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (status == HaStatus.NotConfigured) "Connect Home Assistant" else "No cameras yet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                SecondaryText(
                    if (status == HaStatus.NotConfigured) "Open Settings to add your server"
                    else "Cameras from Home Assistant will show here",
                    maxLines = 2,
                )
            }
        }
        return
    }

    val index = cameras.indexOfFirst { it.entityId == settings.lastCamera }.coerceAtLeast(0)
    val cam = cameras[index]
    Surface(
        onClick = { onOpenCamera(cam.entityId) },
        modifier = modifier.onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown || cameras.size < 2) return@onPreviewKeyEvent false
            val next = when (ev.key) {
                Key.DirectionLeft -> (index - 1 + cameras.size) % cameras.size
                Key.DirectionRight -> (index + 1) % cameras.size
                else -> return@onPreviewKeyEvent false
            }
            vm.selectCamera(cameras[next].entityId)
            true
        },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = focusedBorder(16),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black, focusedContainerColor = Color.Black),
    ) {
        key(cam.entityId) {
            LiveCamera(vm, cam.entityId, Modifier.fillMaxSize())
        }
        if (cameras.size > 1) {
            Icon(
                Icons.Rounded.ChevronLeft, contentDescription = "Previous camera", tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterStart).size(36.dp),
            )
            Icon(
                Icons.Rounded.ChevronRight, contentDescription = "Next camera", tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterEnd).size(36.dp),
            )
        }
        Text(
            cam.name,
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = SurfaceDefaults.colors(containerColor = PanelColor, contentColor = Palette.TextPrimary),
    ) { content() }
}

@Composable
private fun WeatherCard(weather: Weather?, modifier: Modifier) {
    val context = LocalContext.current
    Panel(modifier) {
        if (weather == null) {
            Column(Modifier.padding(16.dp)) {
                Text("Weather", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                SecondaryText("Connect Home Assistant or pick a city in Settings", maxLines = 2, size = 13)
            }
            return@Panel
        }
        val kind = weatherKind(weather.code)
        val today = weather.daily.firstOrNull()
        val rain = today?.precipChance?.let { if (it < 15) "no rain" else "$it% rain" }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    weatherIcon(kind, weather.isDay), contentDescription = null,
                    tint = weatherTint(kind, weather.isDay), modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("${weather.temperature.roundToInt()}°", fontSize = 30.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(weatherLabel(weather.code), fontSize = 15.sp, maxLines = 1)
                    if (today != null) {
                        SecondaryText(
                            listOfNotNull("${today.max.roundToInt()}° / ${today.min.roundToInt()}°", rain).joinToString(" · "),
                            size = 12,
                        )
                    }
                }
            }
            if (weather.hourly.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "ha")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weather.hourly.forEach { h ->
                        val hk = weatherKind(h.code)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(h.time.format(fmt).lowercase(), fontSize = 12.sp, color = Palette.TextSecondary)
                            Spacer(Modifier.height(3.dp))
                            Icon(
                                weatherIcon(hk, h.isDay), contentDescription = weatherLabel(h.code),
                                tint = weatherTint(hk, h.isDay), modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text("${h.temperature.roundToInt()}°", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorsCard(groups: List<SensorGroup>, status: HaStatus, modifier: Modifier) {
    Panel(modifier) {
        if (groups.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sensors", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                SecondaryText(
                    if (status == HaStatus.NotConfigured) "Connect Home Assistant in Settings"
                    else "Choose sensors in Settings → Sensors panel",
                    maxLines = 2,
                    size = 13,
                )
            }
            return@Panel
        }
        val showHeaders = groups.size > 1 || groups.first().area != null
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            groups.forEach { g ->
                if (showHeaders) {
                    Text(
                        g.area ?: "Other",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                g.items.forEach { SensorRow(it) }
            }
        }
    }
}

@Composable
private fun SensorRow(item: DashboardItem) {
    val e = item.entity
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            entityIcon(e, item.entityId), contentDescription = null,
            tint = Palette.TextSecondary, modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            e?.name ?: item.entityId,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (e != null && e.domain == "sensor" && !e.isUnavailable) {
            Text(e.state, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            e.unit?.let { Text(" $it", fontSize = 12.sp, color = Palette.TextSecondary) }
        } else {
            Text(if (e == null) "—" else stateText(e), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- Other tabs

@Composable
private fun CamerasTab(vm: LauncherViewModel, onOpenCamera: (String) -> Unit) {
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    if (cameras.isEmpty()) {
        EmptyMessage("No cameras found in Home Assistant")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = Edge, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(cameras, key = { it.entityId }) { cam ->
            Surface(
                onClick = { onOpenCamera(cam.entityId) },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                border = focusedBorder(12),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black, focusedContainerColor = Color.Black),
            ) {
                CameraSnapshot(vm, cam.entityId, Modifier.fillMaxSize(), intervalMs = 5_000)
                Text(
                    cam.name,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun LightsTab(vm: LauncherViewModel, onPanel: (String) -> Unit) {
    val groups by vm.lightGroups.collectAsStateWithLifecycle()
    if (groups.isEmpty()) {
        EmptyMessage("No lights found in Home Assistant")
        return
    }
    val showHeaders = groups.size > 1 || groups.first().area != null
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = Edge, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { g: LightGroup ->
            if (showHeaders) {
                item(key = "area-${g.area}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        g.area ?: "Other",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(g.lights, key = { it.entityId }) { light ->
                LightTile(light, onClick = { vm.toggleLight(light) }, onLongClick = { onPanel(light.entityId) })
            }
        }
    }
}

@Composable
private fun LightTile(e: HaEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        container = if (e.isOn) Palette.CardActive else Palette.Card,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(lightColor(e)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = if (e.isOn) Palette.OnFocused else LocalContentColor.current,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SecondaryText(stateText(e), size = 13)
            }
        }
    }
}

@Composable
private fun AppsGrid(
    apps: List<LauncherApp>,
    firstApp: FocusRequester,
    launch: (LauncherApp) -> Unit,
    onMenu: (LauncherApp) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(APPS_PER_ROW),
        contentPadding = PaddingValues(horizontal = Edge, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
            AppCard(
                app = app,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .let { if (i == 0) it.focusRequester(firstApp) else it },
                onClick = { launch(app) },
                onLongClick = { onMenu(app) },
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 18.sp, color = Palette.TextSecondary)
    }
}

// ---------------------------------------------------------------- Shared bits

fun stateText(e: HaEntity): String {
    if (e.isUnavailable) return "Unavailable"
    return when (e.domain) {
        "light" -> if (e.isOn && e.brightness != null) {
            "On · ${(e.brightness * 100 / 255.0).roundToInt()}%"
        } else pretty(e.state)
        "sensor", "number", "input_number" -> listOfNotNull(e.state, e.unit).joinToString(" ")
        "binary_sensor" -> when (e.deviceClass) {
            "door", "garage_door", "opening", "window" -> if (e.isOn) "Open" else "Closed"
            "motion", "occupancy", "presence" -> if (e.isOn) "Detected" else "Clear"
            "moisture" -> if (e.isOn) "Wet" else "Dry"
            else -> pretty(e.state)
        }
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

/** A 16:9 app banner. */
@Composable
private fun AppCard(
    app: LauncherApp,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = focusedBorder(10),
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
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (app.icon != null) {
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    app.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
