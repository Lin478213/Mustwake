package com.mustwake.alarm.data.db

/** Lifecycle of one ringing session. Terminal states never come back to life. */
enum class AlarmSessionState {
    RINGING,
    SNOOZED,
    COMPLETED,
    EMERGENCY,
    DEGRADED,
    MISSED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this != RINGING && this != SNOOZED

    val isActive: Boolean
        get() = !isTerminal
}
