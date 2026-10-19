package com.mustwake.alarm.ui.history

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.db.AlarmEventEntity
import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.ui.design.MwCard
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTypo
import com.mustwake.alarm.ui.timeText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun HistoryScreen(container: AppContainer) {
    val events by container.historyRepository.observeRecent(100).collectAsState(initial = emptyList())
    val alarms by container.alarmRepository.observeAlarms().collectAsState(initial = emptyList())
    val labels = remember(alarms) {
        alarms.associate { it.id to "${timeText(it.hour, it.minute)} ${it.label}".trim() }
    }

    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MwText(
                text = "还没有记录",
                typo = MwTypo.Title,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            MwCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        MwText(
                            text = labels[event.alarmId] ?: "已删除的闹钟",
                            typo = MwTypo.Body,
                        )
                        MwText(
                            text = "${formatTime(event)} · ${eventTitle(event.type)}" +
                                if (event.steps > 0) " · ${event.steps} 步" else "",
                            typo = MwTypo.Caption,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        event.reason?.let { reason ->
                            MwText(
                                text = reason,
                                typo = MwTypo.Caption,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(event: AlarmEventEntity): String =
    Instant.ofEpochMilli(event.timestamp)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMAT)

private fun eventTitle(type: AlarmEventType): String = when (type) {
    AlarmEventType.SCHEDULED -> "已安排"
    AlarmEventType.RINGING -> "开始响铃"
    AlarmEventType.SNOOZED -> "稍后提醒"
    AlarmEventType.COMPLETED -> "完成"
    AlarmEventType.EMERGENCY -> "紧急停止"
    AlarmEventType.DEGRADED -> "降级完成"
    AlarmEventType.MISSED -> "错过"
    AlarmEventType.CANCELLED -> "取消"
}
