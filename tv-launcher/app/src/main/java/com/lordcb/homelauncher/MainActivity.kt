package com.lordcb.homelauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lordcb.homelauncher.ui.CameraScreen
import com.lordcb.homelauncher.ui.EntityPickerScreen
import com.lordcb.homelauncher.ui.HomeScreen
import com.lordcb.homelauncher.ui.LauncherTheme
import com.lordcb.homelauncher.ui.SettingsScreen

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Picker(val target: PickTarget) : Screen
    data class Camera(val entityId: String) : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private var stack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
    private var homeFocusTrigger by mutableIntStateOf(0)
    private var homeTab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                val screen = stack.last()
                // On the home screen Back does nothing, like the stock launcher.
                BackHandler {
                    if (stack.size > 1) {
                        stack = stack.dropLast(1)
                        if (stack.last() == Screen.Home) homeFocusTrigger++
                    }
                }
                when (screen) {
                    Screen.Home -> HomeScreen(
                        vm = vm,
                        tab = homeTab,
                        onTab = { homeTab = it },
                        focusTrigger = homeFocusTrigger,
                        onOpenSettings = { stack = stack + Screen.Settings },
                        onOpenPicker = { stack = stack + Screen.Picker(it) },
                        onOpenCamera = { stack = stack + Screen.Camera(it) },
                    )
                    Screen.Settings -> SettingsScreen(vm, onOpenPicker = { stack = stack + Screen.Picker(it) })
                    is Screen.Picker -> EntityPickerScreen(vm, screen.target)
                    is Screen.Camera -> CameraScreen(vm, screen.entityId)
                }
            }
        }
    }

    /** Pressing Home while already on the launcher returns to the main screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stack = listOf(Screen.Home)
        homeTab = 0
        homeFocusTrigger++
    }
}
