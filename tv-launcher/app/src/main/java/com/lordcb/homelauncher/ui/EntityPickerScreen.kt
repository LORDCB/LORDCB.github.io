package com.lordcb.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.lordcb.homelauncher.LauncherViewModel
import com.lordcb.homelauncher.data.HaEntity
import com.lordcb.homelauncher.data.HaStatus
import kotlinx.coroutines.delay

private data class DomainGroup(val label: String, val domains: Set<String>?)

private val groups = listOf(
    DomainGroup("All", null),
    DomainGroup("Lights", setOf("light")),
    DomainGroup("Switches", setOf("switch", "input_boolean", "fan", "automation")),
    DomainGroup("Scenes & scripts", setOf("scene", "script", "button", "input_button")),
    DomainGroup("Climate", setOf("climate")),
    DomainGroup("Covers & locks", setOf("cover", "lock")),
    DomainGroup("Media", setOf("media_player")),
    DomainGroup("Sensors", setOf("sensor", "binary_sensor")),
)

private val hiddenDomains = setOf(
    "update", "zone", "sun", "conversation", "tts", "stt", "wake_word", "event",
    "persistent_notification", "assist_satellite", "todo", "calendar", "image", "tag",
)

private val domainRank = listOf(
    "light", "switch", "fan", "scene", "script", "climate", "cover", "lock",
    "media_player", "input_boolean", "button", "sensor", "binary_sensor",
)

@Composable
fun EntityPickerScreen(vm: LauncherViewModel) {
    val states by vm.ha.states.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.ha.status.collectAsStateWithLifecycle()
    var groupIndex by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }

    val group = groups[groupIndex]
    val list = remember(states, groupIndex) {
        states.values
            .filter { e ->
                e.domain !in hiddenDomains && (group.domains == null || e.domain in group.domains)
            }
            .sortedWith(
                compareBy<HaEntity> { domainRank.indexOf(it.domain).let { r -> if (r < 0) 99 else r } }
                    .thenBy { it.name.lowercase() }
            )
    }

    Column(Modifier.fillMaxSize().background(Palette.Background).padding(top = 40.dp)) {
        Column(Modifier.padding(horizontal = 120.dp)) {
            Text("Choose devices", fontSize = 40.sp, fontWeight = FontWeight.Light, color = Palette.TextPrimary)
            Text(
                "${settings.entities.size} on your home screen · press select to add or remove",
                fontSize = 17.sp,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 120.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(groups) { i, g ->
                FocusCard(
                    onClick = { groupIndex = i },
                    radius = 24,
                    container = if (i == groupIndex) Palette.Accent.copy(alpha = 0.25f) else Palette.Card,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) {
                    Text(g.label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
        if (status != HaStatus.Connected && states.isEmpty()) {
            Text(
                "Connect to Home Assistant in Settings to see your devices.",
                fontSize = 18.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(horizontal = 120.dp, vertical = 24.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 120.dp, end = 120.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.entityId }) { e ->
                val selected = e.entityId in settings.entities
                FocusCard(
                    onClick = { vm.toggleEntitySelected(e.entityId) },
                    focusedScale = 1.02f,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(entityIcon(e, e.entityId), contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            SecondaryText("${e.entityId} · ${stateText(e)}", size = 14)
                        }
                        Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (selected) "Selected" else "Not selected",
                            tint = if (selected) Palette.Good else LocalContentColor.current.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
}
