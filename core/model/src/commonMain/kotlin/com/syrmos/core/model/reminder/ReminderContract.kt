package com.syrmos.core.model.reminder

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Versioning and persistence entry point for the leave-by reminder contract
 * (Phase N J09). Persist the saved-departure board through here so decoding is
 * atomic and versioned: an unsupported or corrupt blob returns a typed result
 * the caller routes to recovery instead of throwing and losing a valid list.
 *
 * Mirrors the JourneyContract shape exactly so both persistence paths behave the
 * same (lenient unknown keys, defaults encoded, a blank blob is a fresh empty
 * list, a newer schema is Unsupported rather than Corrupt).
 */
object ReminderContract {
    /** Bumped only with a real migration. Every persisted root carries it. */
    const val SCHEMA_VERSION: Int = 1

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
        explicitNulls = true
    }

    sealed interface DecodeResult<out T> {
        data class Ok<T>(val value: T) : DecodeResult<T>
        data class Unsupported(val foundVersion: Int) : DecodeResult<Nothing>
        data class Corrupt(val reason: String) : DecodeResult<Nothing>
    }

    private fun peekVersion(raw: String): Int? =
        runCatching {
            (json.parseToJsonElement(raw) as? JsonObject)
                ?.get("schemaVersion")?.jsonPrimitive?.content?.toIntOrNull()
        }.getOrNull()

    /**
     * Decode the saved-departures list root. An empty/blank blob is a fresh, valid
     * empty list (not corrupt), so a first run never opens recovery.
     */
    fun decodeSavedDeparturesRoot(raw: String): DecodeResult<SavedDeparturesRoot> {
        if (raw.isBlank()) return DecodeResult.Ok(SavedDeparturesRoot())
        return decode(raw) { json.decodeFromString(SavedDeparturesRoot.serializer(), it) }
    }

    fun encodeSavedDeparturesRoot(value: SavedDeparturesRoot): String =
        json.encodeToString(SavedDeparturesRoot.serializer(), value)

    private inline fun <T> decode(raw: String, parse: (String) -> T): DecodeResult<T> {
        val version = peekVersion(raw)
        if (version != null && version > SCHEMA_VERSION) return DecodeResult.Unsupported(version)
        return runCatching { parse(raw) }
            .fold(
                onSuccess = { DecodeResult.Ok(it) },
                onFailure = { DecodeResult.Corrupt(it.message ?: it::class.simpleName ?: "decode failed") },
            )
    }
}
