package com.lordcb.homelauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.lordcb.homelauncher.LauncherViewModel
import com.lordcb.homelauncher.data.HaEntity
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class Hue(val name: String, val hue: Float)

private val hues = listOf(
    Hue("Red", 0f), Hue("Orange", 28f), Hue("Yellow", 52f), Hue("Green", 120f),
    Hue("Cyan", 185f), Hue("Blue", 225f), Hue("Purple", 275f), Hue("Pink", 320f),
)

private data class White(val name: String, val kelvin: Int, val color: Color)

private val whites = listOf(
    White("Candle", 2200, Color(0xFFFFA24A)),
    White("Warm", 2700, Color(0xFFFFB86E)),
    White("Soft", 3500, Color(0xFFFFD29C)),
    White("Neutral", 4500, Color(0xFFFFE9D2)),
    White("Daylight", 6500, Color(0xFFF2F4FF)),
)

/** Colour shown for a light on tiles: its actual colour when on, else dim. */
fun lightColor(e: HaEntity?): Color = when {
    e == null || !e.isOn -> Color.White.copy(alpha = 0.1f)
    e.rgb != null -> Color(e.rgb)
    else -> Palette.Warm
}

/**
 * Control panel for a light: on/off, brightness (left/right), white temperature and colour.
 * [extraOptions] adds buttons such as "Move left" when opened from a quick button.
 */
@Composable
fun LightPanel(
    vm: LauncherViewModel,
    entityId: String,
    onDismiss: () -> Unit,
    extraOptions: List<DialogOption> = emptyList(),
) {
    val states by vm.ha.states.collectAsStateWithLifecycle()
    val e = states[entityId]
    if (e == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        DialogPanel {
            Column(
                Modifier
                    .width(600.dp)
                    .ignoreHeldSelect()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(lightColor(e)))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        e.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryText(stateText(e), size = 16)
                }
                Spacer(Modifier.height(20.dp))

                ActionButton(
                    label = if (e.isOn) "Turn off" else "Turn on",
                    icon = Icons.Rounded.PowerSettingsNew,
                    onClick = { vm.toggleLight(e) },
                    modifier = Modifier.focusRequester(first),
                )

                if (e.supportsBrightness) {
                    Spacer(Modifier.height(16.dp))
                    BrightnessBar(vm, e)
                }

                if (e.supportsColorTemp) {
                    SectionLabel("White")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        whites.forEach { w ->
                            Swatch(w.color, w.name) { vm.setLightTemperature(e, w.kelvin) }
                        }
                    }
                }

                if (e.supportsColor) {
                    SectionLabel("Colour")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        hues.forEach { h ->
                            Swatch(Color.hsv(h.hue, 0.85f, 1f), h.name) { vm.setLightColor(e, h.hue, 85f) }
                        }
                    }
                }

                if (extraOptions.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        extraOptions.forEach { opt ->
                            ActionButton(opt.label, icon = opt.icon, compact = true, onClick = {
                                onDismiss()
                                opt.action()
                            })
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
    )
}

/** Focus it, then press left/right to change brightness in 10% steps. */
@Composable
private fun BrightnessBar(vm: LauncherViewModel, e: HaEntity) {
    val actual = if (e.isOn) ((e.brightness ?: 255) * 100 / 255.0).roundToInt().coerceIn(1, 100) else 0
    var percent by remember { mutableIntStateOf(actual) }
    var pending by remember { mutableStateOf(false) }

    // Follow changes from Home Assistant unless the user is mid-adjustment.
    LaunchedEffect(actual) { if (!pending) percent = actual }
    // Send after a short pause so holding left/right doesn't flood HA with calls.
    LaunchedEffect(percent, pending) {
        if (pending) {
            delay(300)
            vm.setBrightness(e, percent)
            pending = false
        }
    }

    FocusCard(
        onClick = {},
        focusedScale = 1.02f,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (ev.key) {
                    Key.DirectionLeft -> -10
                    Key.DirectionRight -> 10
                    else -> return@onPreviewKeyEvent false
                }
                percent = (if (percent <= 1 && delta > 0) 10 else percent + delta).coerceIn(1, 100)
                pending = true
                true
            },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Brightness", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("‹  $percent%  ›", fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(LocalContentColor.current.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(percent / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Palette.Warm)
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape),
            ),
            colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
        ) {}
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Palette.TextSecondary)
    }
}
