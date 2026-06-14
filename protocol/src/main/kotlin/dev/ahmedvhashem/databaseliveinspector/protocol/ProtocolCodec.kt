package dev.ahmedvhashem.databaseliveinspector.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Encode/decode for [ProtocolMessage] frames.
 *
 * Unknown JSON fields are ignored; frames whose `"type"` discriminator isn't known are
 * reported as [DecodeResult.UnknownType] so a session can count them in diagnostics rather
 * than throwing through its reader loop. `encodeDefaults = false` so optional fields
 * (notably [QueryFinished]'s `resultColumns`/`resultRows`/`resultRowCount`/`resultTruncated`)
 * are omitted from the wire at their default values.
 */
object ProtocolCodec {

    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    val KNOWN_TYPES: Set<String> = setOf(
        "app_info",
        "query_started", "query_finished",
        "dropped_events", "agent_error",
        "set_capture", "capture_state",
    )

    sealed interface DecodeResult {
        data class Success(val message: ProtocolMessage) : DecodeResult
        data class UnknownType(val type: String) : DecodeResult
        data class Failure(val error: String) : DecodeResult
    }

    fun encodeToString(message: ProtocolMessage): String =
        json.encodeToString(ProtocolMessage.serializer(), message)

    fun encode(message: ProtocolMessage): ByteArray =
        encodeToString(message).toByteArray(Charsets.UTF_8)

    fun decode(payload: ByteArray): DecodeResult = decode(String(payload, Charsets.UTF_8))

    fun decode(text: String): DecodeResult {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return DecodeResult.Failure("malformed JSON: ${e.message}")
        }
        val obj = element as? JsonObject
            ?: return DecodeResult.Failure("frame payload is not a JSON object")
        val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return DecodeResult.Failure("missing string discriminator field \"type\"")
        if (type !in KNOWN_TYPES) {
            return DecodeResult.UnknownType(type)
        }
        return try {
            DecodeResult.Success(json.decodeFromJsonElement(ProtocolMessage.serializer(), obj))
        } catch (e: Exception) {
            DecodeResult.Failure("cannot decode \"$type\": ${e.message}")
        }
    }
}
