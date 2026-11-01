package com.lordcb.homelauncher.ui

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.lordcb.homelauncher.LauncherViewModel
import kotlinx.coroutines.delay

/** True while the launcher is on screen; video and snapshot polling stop when another app is open. */
@Composable
fun rememberOnScreen(): Boolean {
    // Plain collectAsState: the lifecycle-aware variant would stop collecting right as we go
    // to the background and could miss the "stopped" state.
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/**
 * Live video from a Home Assistant camera via its HLS stream. Falls back to refreshing
 * snapshots if the stream can't be started (e.g. the camera doesn't support streaming).
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveCamera(
    vm: LauncherViewModel,
    entityId: String,
    modifier: Modifier = Modifier,
    fallbackIntervalMs: Long = 2_000,
) {
    val context = LocalContext.current
    val onScreen = rememberOnScreen()
    var streamUrl by remember(entityId) { mutableStateOf<String?>(null) }
    var attempts by remember(entityId) { mutableIntStateOf(0) }
    val failed = attempts >= 3

    // Ask HA for a stream when shown; drop it when hidden (HA idles unused streams out).
    LaunchedEffect(entityId, onScreen, attempts) {
        if (!onScreen) {
            streamUrl = null
            return@LaunchedEffect
        }
        if (streamUrl == null && !failed) {
            val url = vm.cameraStreamUrl(entityId)
            if (url == null) attempts = 3 else streamUrl = url
        }
    }

    Box(modifier.background(Color.Black)) {
        val url = streamUrl
        if (onScreen && url != null && !failed) {
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                    playWhenReady = true
                    prepare()
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Usually an expired stream token: fetch a new stream, give up after a few tries.
                        streamUrl = null
                        attempts += 1
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CameraSnapshot(
                vm = vm,
                entityId = entityId,
                modifier = Modifier.fillMaxSize(),
                intervalMs = if (failed) fallbackIntervalMs else 30_000,
            )
        }
    }
}

/** A camera still that refreshes every [intervalMs]. */
@Composable
fun CameraSnapshot(vm: LauncherViewModel, entityId: String, modifier: Modifier = Modifier, intervalMs: Long = 3_000) {
    val states by vm.ha.states.collectAsStateWithLifecycle()
    val picture = states[entityId]?.entityPicture
    val onScreen = rememberOnScreen()
    val image by produceState<ImageBitmap?>(null, picture, intervalMs, onScreen) {
        if (picture == null || !onScreen) return@produceState
        while (true) {
            vm.cameraSnapshot(picture, 640)?.let { value = it }
            delay(intervalMs)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("Loading camera…", color = Palette.TextSecondary, fontSize = 14.sp)
        }
    }
}

/** Full-screen live view. Left/right switches camera, Back returns. */
@Composable
fun CameraScreen(vm: LauncherViewModel, startEntityId: String) {
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    var index by remember {
        mutableIntStateOf(cameras.indexOfFirst { it.entityId == startEntityId }.coerceAtLeast(0))
    }
    val focus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || cameras.size < 2) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { index = (index - 1 + cameras.size) % cameras.size; true }
                    Key.DirectionRight -> { index = (index + 1) % cameras.size; true }
                    else -> false
                }
            }
            .focusable()
    ) {
        val cam = cameras.getOrNull(index.coerceAtMost(cameras.lastIndex))
        if (cam != null) {
            LiveCamera(vm, cam.entityId, Modifier.fillMaxSize(), fallbackIntervalMs = 1_000)
            Text(
                cam.name + if (cameras.size > 1) "   ‹ ›" else "",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            Text("No cameras found", color = Palette.TextSecondary, modifier = Modifier.align(Alignment.Center))
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focus.requestFocus() }
    }
}
