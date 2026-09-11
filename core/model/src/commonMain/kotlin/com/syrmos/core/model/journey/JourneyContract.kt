package com.syrmos.core.model.journey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Versioning and persistence entry point for the Journeys contract. Persist
 * everything through here so decoding is atomic and versioned: an unsupported or
 * corrupt blob returns a typed result the UI routes to recovery, instead of
 * throwing and losing a valid saved trip elsewhere.
 */
object JourneyContract {
    /** Bumped only with a real migration. Every persisted root carries it. */
    const val SCHEMA_VERSION: Int = 1

    /** All schedule evaluation happens here. Not every date is 24 hours long. */
    const val SERVICE_TIME_ZONE: String = "Europe/Athens"

    /**
     * Lenient by design: `ignoreUnknownKeys` lets a newer minor payload (extra
     * fields) still decode, and encodes defaults so a re-read is stable. Nulls
     * are preserved (unknown stays unknown); we do not coerce them to defaults.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
        explicitNulls = true
    }

    /** Outcome of decoding a persisted journey root. */
    sealed interface DecodeResult<out T> {
        data class Ok<T>(val value: T) : DecodeResult<T>
        /** Newer schema than this build understands; offer recovery, keep the blob. */
        data class Unsupported(val foundVersion: Int) : DecodeResult<Nothing>
        /** Unparseable/invalid; open recovery UI, never overwrite a valid saved trip. */
        data class Corrupt(val reason: String) : DecodeResult<Nothing>
    }

    private fun peekVersion(raw: String): Int? =
        runCatching {
            (json.parseToJsonElement(raw) as? JsonObject)
                ?.get("schemaVersion")?.jsonPrimitive?.content?.toIntOrNull()
        }.getOrNull()

    fun decodeActiveJourney(raw: String): DecodeResult<ActiveJourney> =
        decode(raw) { json.decodeFromString(ActiveJourney.serializer(), it) }

    fun decodeSavedJourney(raw: String): DecodeResult<SavedJourney> =
        decode(raw) { json.decodeFromString(SavedJourney.serializer(), it) }

    /**
     * Decode the saved-journeys list root. An empty/blank blob is a fresh, valid
     * empty list (not corrupt), so a first run never opens recovery.
     */
    fun decodeSavedJourneysRoot(raw: String): DecodeResult<SavedJourneysRoot> {
        if (raw.isBlank()) return DecodeResult.Ok(SavedJourneysRoot())
        return decode(raw) { json.decodeFromString(SavedJourneysRoot.serializer(), it) }
    }

    fun encodeActiveJourney(value: ActiveJourney): String =
        json.encodeToString(ActiveJourney.serializer(), value)

    fun encodeSavedJourney(value: SavedJourney): String =
        json.encodeToString(SavedJourney.serializer(), value)

    fun encodeSavedJourneysRoot(value: SavedJourneysRoot): String =
        json.encodeToString(SavedJourneysRoot.serializer(), value)

    private inline fun <T> decode(raw: String, parse: (String) -> T): DecodeResult<T> {
        val version = peekVersion(raw)
        if (version != null && version > SCHEMA_VERSION) {
            return DecodeResult.Unsupported(version)
        }
        return runCatching { parse(raw) }
            .fold(
                onSuccess = { DecodeResult.Ok(it) },
                onFailure = { DecodeResult.Corrupt(it.message ?: it::class.simpleName ?: "decode failed") },
            )
    }
}
