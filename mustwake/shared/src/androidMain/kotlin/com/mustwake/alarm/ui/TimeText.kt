package com.mustwake.alarm.ui

import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import java.util.Locale

internal fun timeText(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

internal fun repeatText(mask: Int): String {
    if (mask == AlarmRepeatDays.NONE) return "仅一次"
    if (mask == AlarmRepeatDays.EVERY_DAY) return "每天"
    val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return labels.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
        .joinToString("、")
}
