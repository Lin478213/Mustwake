package com.mustwake.alarm.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.alarm.NextTriggerCalculator
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.ui.AlarmUiActions
import com.mustwake.alarm.ui.design.MwCard
import com.mustwake.alarm.ui.design.MwSwitch
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTypo
import com.mustwake.alarm.ui.repeatText
import com.mustwake.alarm.ui.timeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AlarmListScreen(
    container: AppContainer,
    actions: AlarmUiActions,
    onEdit: (Long) -> Unit,
    clock: () -> Instant = { Instant.now() },
) {
    val alarms by container.alarmRepository.observeAlarms().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = clock()
        }
    }

    if (alarms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MwText(text = "还没有闹钟", typo = MwTypo.Title)
                MwText(
                    text = "点右下角 + 创建第一个步数闹钟",
                    typo = MwTypo.Caption,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(alarms, key = { it.id }) { alarm ->
            MwCard(onClick = { onEdit(alarm.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        MwText(text = timeText(alarm.hour, alarm.minute), typo = MwTypo.Display)
                        if (alarm.label.isNotBlank()) {
                            MwText(text = alarm.label, typo = MwTypo.Body)
                        }
                        if (alarm.enabled) {
                            nextRingText(alarm, now)?.let { next ->
                                MwText(
                                    text = "下次 $next",
                                    typo = MwTypo.Caption,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        MwText(
                            text = "${repeatText(alarm.repeatDaysMask)} · ${alarm.goalSteps} 步",
                            typo = MwTypo.Caption,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    MwSwitch(
                        checked = alarm.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                container.alarmRepository.save(alarm.copy(enabled = enabled))
                                if (enabled) {
                                    actions.schedule(alarm.copy(enabled = true))
                                } else {
                                    actions.cancel(alarm.id)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private val NEXT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

private fun nextRingText(alarm: AlarmEntity, now: Instant): String? {
    val next = NextTriggerCalculator.nextTrigger(alarm, now) ?: return null
    val zone = ZoneId.systemDefault()
    val zoned = next.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val day = when (zoned.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "周${WEEKDAYS[zoned.dayOfWeek.value - 1]}"
    }
    return "$day ${zoned.format(NEXT_TIME_FORMAT)}"
}
