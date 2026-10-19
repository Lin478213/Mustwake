package com.mustwake.alarm.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun alarm(
        id: Long = 1L,
        hour: Int = 7,
        minute: Int = 0,
        repeatDaysMask: Int = 127,
        label: String = "Work",
        goalSteps: Int = 50,
        enabled: Boolean = true,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = BackupAlarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        label = label,
        goalSteps = goalSteps,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `round trip preserves every field`() {
        val data = BackupData(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            exportedAt = 1_760_000_000_000L,
            alarms = listOf(
                alarm(id = 1, hour = 7, minute = 0, repeatDaysMask = 127, label = "起床", goalSteps = 50, enabled = true, createdAt = 11L, updatedAt = 22L),
                alarm(id = 2, hour = 23, minute = 59, repeatDaysMask = 0, label = "", goalSteps = 500, enabled = false, createdAt = 33L, updatedAt = 44L),
            ),
            settings = BackupSettings(
                floatingBottomBarEnabled = true,
                liquidGlassEnabled = true,
                defaultGoalSteps = 120,
                snoozeMinutes = 15,
                maxSnoozes = 0,
                shakeCount = 100,
            ),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(data))

        assertEquals(data, decoded.getOrThrow())
    }

    @Test
    fun `round trip preserves empty alarms and default settings`() {
        val data = BackupData(schemaVersion = 1, exportedAt = 5L)

        val decoded = BackupCodec.decode(BackupCodec.encode(data))

        assertEquals(data, decoded.getOrThrow())
    }

    @Test
    fun `malformed json returns failure without throwing`() {
        listOf("", "not json", "{]", "{\"schemaVersion\":1,\"alarms\":\"oops\"}").forEach { bad ->
            val result = BackupCodec.decode(bad)
            assertTrue("expected failure for $bad", result.isFailure)
        }
    }

    @Test
    fun `unknown schemaVersion returns failure without throwing`() {
        listOf(
            "{\"schemaVersion\":2,\"exportedAt\":0,\"alarms\":[],\"settings\":{}}",
            "{\"schemaVersion\":0,\"exportedAt\":0,\"alarms\":[],\"settings\":{}}",
            "{\"exportedAt\":0,\"alarms\":[],\"settings\":{}}",
        ).forEach { bad ->
            val result = BackupCodec.decode(bad)
            assertTrue("expected failure for $bad", result.isFailure)
        }
    }

    @Test
    fun `out of range values survive decode for the repository to reject`() {
        val json = """
            {"schemaVersion":1,"exportedAt":0,"alarms":[
              {"id":1,"hour":7,"minute":0,"repeat_days_mask":0,"label":"a","goal_steps":501,"enabled":true,"created_at":0,"updated_at":0},
              {"id":2,"hour":7,"minute":1,"repeat_days_mask":0,"label":"b","goal_steps":9,"enabled":true,"created_at":0,"updated_at":0}
            ],"settings":{}}
        """.trimIndent()

        val decoded = BackupCodec.decode(json).getOrThrow()

        assertEquals(501, decoded.alarms[0].goalSteps)
        assertEquals(9, decoded.alarms[1].goalSteps)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val json = """{"schemaVersion":1,"exportedAt":0,"alarms":[{"id":3,"hour":6,"minute":30}],"settings":{}}"""

        val decoded = BackupCodec.decode(json).getOrThrow()
        val parsed = decoded.alarms.single()

        assertEquals(3L, parsed.id)
        assertEquals(0, parsed.repeatDaysMask)
        assertEquals("", parsed.label)
        assertEquals(50, parsed.goalSteps)
        assertTrue(parsed.enabled)
        assertEquals(0L, parsed.createdAt)
        assertEquals(0L, parsed.updatedAt)
        assertEquals(BackupSettings(), decoded.settings)
    }

    @Test
    fun `json field names are stable`() {
        val data = BackupData(
            schemaVersion = 1,
            exportedAt = 123L,
            alarms = listOf(alarm()),
            settings = BackupSettings(),
        )

        val encoded = BackupCodec.encode(data)

        assertEquals(
            """{"schemaVersion":1,"exportedAt":123,"alarms":[{"id":1,"hour":7,"minute":0,""" +
                """"repeat_days_mask":127,"label":"Work","goal_steps":50,"enabled":true,""" +
                """"created_at":0,"updated_at":0}],"settings":{"floating_bottom_bar_enabled":false,""" +
                """"liquid_glass_enabled":false,"default_goal_steps":50,"snooze_minutes":5,""" +
                """"max_snoozes":3,"shake_count":30}}""",
            encoded,
        )
    }

    @Test
    fun `onboarding state is never exported`() {
        val encoded = BackupCodec.encode(BackupData(schemaVersion = 1, exportedAt = 1L))

        assertFalse(encoded.contains("onboarding"))
    }
}
