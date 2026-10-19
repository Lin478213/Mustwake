package com.mustwake.alarm.ui

import com.mustwake.alarm.data.db.AlarmEntity

/**
 * Native actions the UI needs from the app module (scheduling lives there because it owns the
 * receivers and the show-intent activity).
 */
class AlarmUiActions(
    val schedule: suspend (AlarmEntity) -> Unit,
    val cancel: suspend (Long) -> Unit,
)
