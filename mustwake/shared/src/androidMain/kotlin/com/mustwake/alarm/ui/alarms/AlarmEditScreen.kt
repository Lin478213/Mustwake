package com.mustwake.alarm.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.ui.AlarmUiActions
import com.mustwake.alarm.ui.design.MwCard
import com.mustwake.alarm.ui.design.MwChip
import com.mustwake.alarm.ui.design.MwIconAction
import com.mustwake.alarm.ui.design.MwNumberPicker
import com.mustwake.alarm.ui.design.MwPrimaryButton
import com.mustwake.alarm.ui.design.MwScaffold
import com.mustwake.alarm.ui.design.MwSectionTitle
import com.mustwake.alarm.ui.design.MwSlider
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTextField
import com.mustwake.alarm.ui.design.MwTopBar
import com.mustwake.alarm.ui.design.MwTypo
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import java.util.Locale

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    container: AppContainer,
    actions: AlarmUiActions,
    alarmId: Long?,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var goalSteps by remember { mutableIntStateOf(50) }
    var label by remember { mutableStateOf("") }
    var repeatMask by remember { mutableIntStateOf(AlarmRepeatDays.EVERY_DAY) }
    var enabled by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        if (alarmId != null) {
            container.alarmRepository.getAlarm(alarmId)?.let { alarm ->
                hour = alarm.hour
                minute = alarm.minute
                goalSteps = alarm.goalSteps
                label = alarm.label
                repeatMask = alarm.repeatDaysMask
                enabled = alarm.enabled
            }
        } else {
            val settings = container.settingsRepository.currentSettings()
            goalSteps = settings.defaultGoalSteps
        }
        loaded = true
    }

    MwScaffold(
        topBar = {
            MwTopBar(
                title = if (alarmId == null) "新建闹钟" else "编辑闹钟",
                onBack = onClose,
                actions = {
                    if (alarmId != null) {
                        MwIconAction(
                            icon = MiuixIcons.Delete,
                            contentDescription = "删除闹钟",
                            onClick = {
                                scope.launch {
                                    container.alarmRepository.delete(alarmId)
                                    actions.cancel(alarmId)
                                    onClose()
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            MwCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MwNumberPicker(
                        value = hour,
                        onValueChange = { hour = it },
                        range = 0..23,
                        modifier = Modifier.weight(1f),
                        label = { String.format(Locale.US, "%02d", it) },
                    )
                    MwText(text = ":", typo = MwTypo.Title)
                    MwNumberPicker(
                        value = minute,
                        onValueChange = { minute = it },
                        range = 0..59,
                        modifier = Modifier.weight(1f),
                        label = { String.format(Locale.US, "%02d", it) },
                    )
                }
                MwTextField(
                    value = label,
                    onValueChange = { if (it.length <= 40) label = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = "标签（可选）",
                )
            }

            MwSectionTitle(text = "目标步数", modifier = Modifier.padding(top = 16.dp))
            MwCard(modifier = Modifier.fillMaxWidth()) {
                MwSlider(
                    value = goalSteps,
                    onValueChange = { goalSteps = it },
                    range = 10..500,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            MwSectionTitle(text = "重复", modifier = Modifier.padding(top = 16.dp))
            MwCard(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DAY_LABELS.forEachIndexed { index, day ->
                        val bit = 1 shl index
                        MwChip(
                            text = day,
                            selected = repeatMask and bit != 0,
                            onClick = { repeatMask = repeatMask xor bit },
                        )
                    }
                    MwChip(
                        text = "每天",
                        selected = repeatMask == AlarmRepeatDays.EVERY_DAY,
                        onClick = { repeatMask = AlarmRepeatDays.EVERY_DAY },
                    )
                }
                MwText(
                    text = if (repeatMask == AlarmRepeatDays.NONE) "不重复：只响一次" else "到点后必须走够目标步数",
                    typo = MwTypo.Caption,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            MwPrimaryButton(
                text = if (saving) "保存中…" else "保存",
                enabled = loaded && !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true
                    scope.launch {
                        val entity = AlarmEntity(
                            id = alarmId ?: 0L,
                            hour = hour,
                            minute = minute,
                            repeatDaysMask = repeatMask,
                            label = label,
                            goalSteps = goalSteps,
                            enabled = enabled,
                        )
                        val savedId = container.alarmRepository.save(entity)
                        container.alarmRepository.getAlarm(savedId)?.let { actions.schedule(it) }
                        saving = false
                        onClose()
                    }
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
