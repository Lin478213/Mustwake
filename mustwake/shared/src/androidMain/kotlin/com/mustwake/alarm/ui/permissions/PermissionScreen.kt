package com.mustwake.alarm.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mustwake.alarm.ui.design.MwCard
import com.mustwake.alarm.ui.design.MwChip
import com.mustwake.alarm.ui.design.MwPrimaryButton
import com.mustwake.alarm.ui.design.MwScaffold
import com.mustwake.alarm.ui.design.MwSecondaryButton
import com.mustwake.alarm.ui.design.MwSectionTitle
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTopBar
import com.mustwake.alarm.ui.design.MwTypo
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * First-run permission guide and the Settings entry point.
 *
 * Runtime permissions use the system dialog; special accesses (exact alarms, FSI, battery,
 * autostart) deep-link into the matching system pages. Nothing is granted programmatically.
 */
@Composable
fun PermissionScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readPermissionState(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readPermissionState(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { state = readPermissionState(context) }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { state = readPermissionState(context) }

    MwScaffold(topBar = { MwTopBar(title = "权限与后台", onBack = onClose) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            MwCard(modifier = Modifier.fillMaxWidth()) {
                MwText(
                    text = "这些权限决定闹钟能否准时响铃、锁屏时能否全屏提醒。拒绝不会让应用崩溃，但会降低可靠性。",
                    typo = MwTypo.Body,
                )
            }

            MwSectionTitle(text = "必要权限", modifier = Modifier.padding(top = 16.dp))
            MwCard(modifier = Modifier.fillMaxWidth()) {
                PermissionRow(
                    title = "通知",
                    summary = "响铃通知与全屏提醒的基础",
                    granted = state.notifications,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    },
                    onOpenSettings = { openNotificationSettings(context) },
                )
                PermissionRow(
                    title = "身体活动",
                    summary = "读取计步传感器；拒绝后会走摇晃降级",
                    granted = state.activityRecognition,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    },
                    onOpenSettings = { openAppDetails(context) },
                )
            }

            MwSectionTitle(text = "建议开启", modifier = Modifier.padding(top = 16.dp))
            MwCard(modifier = Modifier.fillMaxWidth()) {
                PermissionRow(
                    title = "精确闹钟",
                    summary = "未开启时系统可能延迟响铃",
                    granted = state.exactAlarms,
                    onRequest = { openExactAlarmSettings(context) },
                )
                PermissionRow(
                    title = "全屏通知",
                    summary = "锁屏时直接弹出响铃页；未开启则退回通知横幅",
                    granted = state.fullScreenIntent,
                    onRequest = { openFullScreenIntentSettings(context) },
                )
            }

            MwSectionTitle(text = "小米 / HyperOS 后台", modifier = Modifier.padding(top = 16.dp))
            MwCard(modifier = Modifier.fillMaxWidth()) {
                PermissionRow(
                    title = "电池优化",
                    summary = "将 MustWake 设为不受限制，减少后台限制",
                    granted = state.batteryUnrestricted,
                    onRequest = { openBatteryOptimizationSettings(context) },
                )
                ActionRow(
                    title = "自启动",
                    summary = "在安全中心允许 MustWake 自启动；系统不提供查询接口",
                    actionText = "去设置",
                    onAction = { openAutoStartSettings(context) },
                )
                MwText(
                    text = "不同 HyperOS 版本的入口可能不同，应用无法自动授予这些权限。",
                    typo = MwTypo.Caption,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            MwPrimaryButton(
                text = if (state.allReady) "完成" else "稍后设置",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            MwText(
                text = "之后可在 设置 → 权限与后台 重新打开本页。",
                typo = MwTypo.Caption,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    granted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MwText(text = title, typo = MwTypo.Body)
            MwText(
                text = summary,
                typo = MwTypo.Caption,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            MwText(
                text = if (granted) "已开启" else "未开启",
                typo = MwTypo.Caption,
                color = if (granted) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
            )
        }
        if (!granted) {
            MwSecondaryButton(
                text = "去开启",
                onClick = onRequest,
                modifier = Modifier.width(88.dp),
            )
            if (onOpenSettings != null) {
                MwChip(text = "设置", selected = false, onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MwText(text = title, typo = MwTypo.Body)
            MwText(
                text = summary,
                typo = MwTypo.Caption,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        MwSecondaryButton(
            text = actionText,
            onClick = onAction,
            modifier = Modifier.width(88.dp),
        )
    }
}
