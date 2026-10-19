package com.mustwake.alarm.data.history

/** History retention limits from the spec: newest 200 events or 90 days, whichever is smaller. */
object HistoryRetention {
    const val MAX_EVENTS = 200
    const val RETENTION_DAYS = 90

    const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
