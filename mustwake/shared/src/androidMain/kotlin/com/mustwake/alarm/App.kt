package com.mustwake.alarm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.ui.AlarmUiActions
import com.mustwake.alarm.ui.alarms.AlarmEditScreen
import com.mustwake.alarm.ui.alarms.AlarmListScreen
import com.mustwake.alarm.ui.design.MwBottomBar
import com.mustwake.alarm.ui.design.LocalMwGlassEnabled
import com.mustwake.alarm.ui.design.LocalMwDialogState
import com.mustwake.alarm.ui.design.MwDialogHost
import com.mustwake.alarm.ui.design.MwDialogState
import com.mustwake.alarm.ui.design.MwFab
import com.mustwake.alarm.ui.design.MwScaffold
import com.mustwake.alarm.ui.design.MwTab
import com.mustwake.alarm.ui.design.MwTheme
import com.mustwake.alarm.ui.design.MwTopBar
import com.mustwake.alarm.ui.history.HistoryScreen
import com.mustwake.alarm.ui.permissions.PermissionScreen
import com.mustwake.alarm.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings

internal enum class AppTab(val title: String) {
    Alarms("闹钟"),
    History("历史"),
    Settings("设置"),
}

internal sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val alarmId: Long) : EditorTarget
}

@Composable
fun App(container: AppContainer, actions: AlarmUiActions) {
    MwTheme {
        val settingsState by container.settingsRepository.settings.collectAsState(initial = null)
        val settings = settingsState ?: return@MwTheme
        val glassBackdrop = rememberLayerBackdrop()
        val dialogState = remember { MwDialogState() }
        var tab by remember { mutableStateOf(AppTab.Alarms) }
        var editor by remember { mutableStateOf<EditorTarget?>(null) }
        var showPermissions by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        if (!settings.onboardingCompleted || showPermissions) {
            PermissionScreen(
                onClose = {
                    scope.launch { container.settingsRepository.setOnboardingCompleted(true) }
                    showPermissions = false
                },
            )
            return@MwTheme
        }

        val currentEditor = editor
        if (currentEditor != null) {
            AlarmEditScreen(
                container = container,
                actions = actions,
                alarmId = (currentEditor as? EditorTarget.Existing)?.alarmId,
                onClose = { editor = null },
            )
            return@MwTheme
        }

        CompositionLocalProvider(
            LocalMwGlassEnabled provides settings.liquidGlassEnabled,
            LocalMwDialogState provides dialogState,
        ) {
            MwScaffold(topBar = { MwTopBar(title = tab.title) }) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Only the screen subtree is captured: the bar can blur what scrolls beneath it
                    // without capturing itself (which would feed back into the blur).
                    Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
                        when (tab) {
                            AppTab.Alarms -> AlarmListScreen(
                                container = container,
                                actions = actions,
                                onEdit = { editor = EditorTarget.Existing(it) },
                            )

                            AppTab.History -> HistoryScreen(container = container)
                            AppTab.Settings -> SettingsScreen(
                                container = container,
                                onOpenPermissions = { showPermissions = true },
                            )
                        }
                    }

                    if (tab == AppTab.Alarms) {
                        MwFab(
                            onClick = { editor = EditorTarget.New },
                            icon = MiuixIcons.Add,
                            contentDescription = "新建闹钟",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 24.dp, bottom = 112.dp),
                        )
                    }

                    MwBottomBar(
                        tabs = listOf(
                            MwTab(label = "闹钟", icon = MiuixIcons.Alarm),
                            MwTab(label = "历史", icon = MiuixIcons.ListView),
                            MwTab(label = "设置", icon = MiuixIcons.Settings),
                        ),
                        selectedIndex = tab.ordinal,
                        onSelect = { tab = AppTab.entries[it] },
                        glassBackdrop = if (settings.liquidGlassEnabled) glassBackdrop else null,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    // Hosted outside the captured screen layer so the glass panel refracts
                    // content beneath it (same rule as the bottom bar).
                    MwDialogHost(
                        glassBackdrop = if (settings.liquidGlassEnabled) glassBackdrop else null,
                        state = dialogState,
                    )
                }
            }
        }
    }
}
