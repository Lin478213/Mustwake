package com.mustwake.alarm.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON encoding/decoding for [BackupData].
 *
 * Pure and IO-free so it can be unit-tested on the JVM. Malformed JSON or an unknown
 * [BackupData.schemaVersion] produce a failed [Result]; nothing is thrown to the caller.
 */
object BackupCodec {

    /** Only version understood so far; bumped when the format gains fields. */
    const val SCHEMA_VERSION = 1

    private val json = Json {
        // Forward compatibility: backups written by newer app versions may carry extra keys.
        ignoreUnknownKeys = true
        // Emit every field so the on-disk format is explicit and stable.
        encodeDefaults = true
    }

    /** Serialises [data] to the canonical backup JSON. */
    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)

    /**
     * Parses [text] as a backup document.
     *
     * @return the parsed data, or a failed result (never throws) for malformed JSON,
     *   a missing/unknown schema version or any other decode error.
     */
    fun decode(text: String): Result<BackupData> = runCatching {
        val data = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("malformed backup JSON: ${e.message}", e)
        }
        require(data.schemaVersion == SCHEMA_VERSION) {
            "unknown schemaVersion ${data.schemaVersion}, expected $SCHEMA_VERSION"
        }
        data
    }
}
