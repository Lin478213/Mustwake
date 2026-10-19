package com.mustwake.alarm.data.db

/**
 * Persisted history event types.
 *
 * Mirrors the spec state machine plus [SNOOZED], which is needed to reconstruct a session
 * after process death (T4) and to show the full history (T7).
 */
enum class AlarmEventType {
    /** Alarm was registered with AlarmManager. */
    SCHEDULED,

    /** Ringing session started. */
    RINGING,

    /** User snoozed the ringing session. */
    SNOOZED,

    /** Target steps reached. */
    COMPLETED,

    /** Long-press emergency stop; counts as not completed. */
    EMERGENCY,

    /** Sensor fallback (shake) path completed. */
    DEGRADED,

    /** Session window elapsed without a stop, or a zombie session was swept. */
    MISSED,

    /** Alarm or session cancelled. */
    CANCELLED,
}
