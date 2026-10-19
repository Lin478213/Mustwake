package com.mustwake.alarm.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mustwake.alarm.data.stop.StopPolicy
import com.mustwake.alarm.ui.design.MwPrimaryButton
import com.mustwake.alarm.ui.design.MwProgressBar
import com.mustwake.alarm.ui.design.MwSecondaryButton
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTypo
import com.mustwake.alarm.ui.timeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Immutable UI state for the ringing screen, fed by service broadcasts. */
data class RingingUiState(
    val label: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val steps: Int = 0,
    val goalSteps: Int = 50,
    val snoozeCount: Int = 0,
    val maxSnoozes: Int = 3,
    val snoozeMinutes: Int = 5,
    val degradedMode: Boolean = false,
    val shakeCount: Int = 0,
    val shakeTarget: Int = 30,
)

@Composable
fun RingingScreen(
    state: RingingUiState,
    onSnooze: () -> Unit,
    onEmergency: () -> Unit,
    onShortPress: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MwText(
            text = timeText(state.hour, state.minute),
            typo = MwTypo.Display,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MwText(
            text = state.label.ifBlank { "闹钟" },
            typo = MwTypo.Headline,
            color = colors.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (state.degradedMode) {
            MwText(
                text = "摇晃 ${state.shakeCount} / ${state.shakeTarget} 次",
                typo = MwTypo.Title,
                color = colors.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MwProgressBar(
                progress = if (state.shakeTarget > 0) {
                    state.shakeCount.toFloat() / state.shakeTarget
                } else {
                    0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MwText(
                text = "计步传感器不可用：摇够次数后长按停止",
                typo = MwTypo.Caption,
                color = colors.onBackgroundVariant,
            )
        } else {
            MwText(
                text = "已走 ${state.steps} / ${state.goalSteps} 步",
                typo = MwTypo.Title,
                color = colors.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MwProgressBar(
                progress = if (state.goalSteps > 0) {
                    state.steps.toFloat() / state.goalSteps
                } else {
                    0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MwText(
                text = "还需 ${StopPolicy.remainingSteps(state.steps, state.goalSteps)} 步，走够自动停止",
                typo = MwTypo.Caption,
                color = colors.onBackgroundVariant,
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val canSnooze = StopPolicy.canSnooze(state.snoozeCount, state.maxSnoozes)
            MwSecondaryButton(
                text = if (canSnooze) "稍后 ${state.snoozeMinutes} 分钟" else "稍后提醒已用完",
                onClick = onSnooze,
                enabled = canSnooze,
                modifier = Modifier.weight(1f),
            )
            MwHoldToStopButton(
                text = "长按停止",
                holdingText = "继续按住…",
                holdMillis = 10_000L,
                onHold = onEmergency,
                onShortPress = onShortPress,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        MwText(
            text = "长按 10 秒紧急停止，本次会记为未完成",
            typo = MwTypo.Caption,
            color = colors.onBackgroundVariant,
        )
    }
}

/**
 * A destructive button that only triggers after a continuous press, so an accidental tap cannot
 * bypass the step gate.
 */
@Composable
private fun MwHoldToStopButton(
    text: String,
    holdingText: String,
    holdMillis: Long,
    onHold: () -> Unit,
    onShortPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var holding by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (fired) colors.disabledPrimaryButton else colors.error)
            .pointerInput(fired) {
                if (fired) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        val job = scope.launch {
                            delay(holdMillis)
                            fired = true
                            holding = false
                            onHold()
                        }
                        val released = tryAwaitRelease()
                        val completed = !job.isActive
                        job.cancel()
                        holding = false
                        if (released && !completed) onShortPress()
                    },
                )
            }
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        MwText(
            text = if (holding) holdingText else text,
            typo = MwTypo.Label,
            color = Color.White,
        )
    }
}
