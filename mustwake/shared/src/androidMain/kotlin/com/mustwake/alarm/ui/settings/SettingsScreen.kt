package com.mustwake.alarm.ui.settings

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.settings.AppSettings
import com.mustwake.alarm.ui.design.MwCard
import com.mustwake.alarm.ui.design.MwSecondaryButton
import com.mustwake.alarm.ui.design.MwSectionTitle
import com.mustwake.alarm.ui.design.MwSwitchRow
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTypo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MwSectionTitle(text = "外观")
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwSwitchRow(
                title = "悬浮底栏",
                summary = "主界面使用悬浮导航栏样式",
                checked = settings.floatingBottomBarEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { container.settingsRepository.setFloatingBottomBarEnabled(enabled) }
                },
            )
            MwSwitchRow(
                title = "液态玻璃",
                summary = "半透明模糊表面与高光描边",
                checked = settings.liquidGlassEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { container.settingsRepository.setLiquidGlassEnabled(enabled) }
                },
            )
        }

        MwSectionTitle(text = "步数", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwStepperRow(
                title = "默认目标步数",
                value = settings.defaultGoalSteps,
                range = 10..500,
                step = 10,
                suffix = "步",
            ) { scope.launch { container.settingsRepository.setDefaultGoalSteps(it) } }
        }

        MwSectionTitle(text = "稍后提醒", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwStepperRow(
                title = "间隔",
                value = settings.snoozeMinutes,
                range = 1..15,
                step = 1,
                suffix = "分钟",
            ) { scope.launch { container.settingsRepository.setSnoozeMinutes(it) } }
            MwStepperRow(
                title = "最多次数",
                value = settings.maxSnoozes,
                range = 0..5,
                step = 1,
                suffix = "次",
            ) { scope.launch { container.settingsRepository.setMaxSnoozes(it) } }
        }

        MwSectionTitle(text = "传感器降级", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwStepperRow(
                title = "摇晃次数",
                value = settings.shakeCount,
                range = 10..100,
                step = 10,
                suffix = "次",
            ) { scope.launch { container.settingsRepository.setShakeCount(it) } }
            MwText(
                text = "计步传感器不可用时，摇晃达到该次数后长按停止。",
                typo = MwTypo.Caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MwStepperRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MwText(text = title, typo = MwTypo.Body)
            MwText(text = "$value $suffix", typo = MwTypo.Caption)
        }
        MwSecondaryButton(
            text = "−",
            modifier = Modifier.width(56.dp),
            enabled = value > range.first,
            onClick = { onChange((value - step).coerceAtLeast(range.first)) },
        )
        MwSecondaryButton(
            text = "+",
            modifier = Modifier.width(56.dp),
            enabled = value < range.last,
            onClick = { onChange((value + step).coerceAtMost(range.last)) },
        )
    }
}
