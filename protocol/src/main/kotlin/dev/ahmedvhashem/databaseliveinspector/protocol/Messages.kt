package dev.ahmedvhashem.databaseliveinspector.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for Database Live Inspector v1.
 *
 * Transport is App Inspection's messenger; payload bytes are opaque to the framework, so the
 * format stays entirely under our control. Encoded as JSON via kotlinx-serialization with
 * `encodeDefaults = false`, so optional fields are omitted from the wire at their default
 * values — load-bearing for [QueryFinished]'s result-row preview.
 *
 * Conventions:
 * - sealed hierarchy with discriminator field `"type"` (snake_case values);
 * - other field names camelCase; timestamps epoch millis as numbers;
 * - receivers ignore unknown fields and skip frames with unknown `type` (forward-compat).
 */
@Serializable
sealed class ProtocolMessage

/** §5.1 args[] element. `kind` ∈ `"null" | "long" | "double" | "string" | "blob"`. */
@Serializable
data class ArgPreview(
    val index: Int,
    val kind: String,
    val preview: String,
)

// ----- Events: agent → plugin --------------------------------------------------

/**
 * Emitted once when the inspector binds. Replaces the standalone tool's hello/hello_ack
 * handshake — App Inspection already negotiates the inspector version, so we only need to
 * convey the app's identity for display.
 */
@Serializable
@SerialName("app_info")
data class AppInfo(
    val appId: String,
    val pid: Int,
    val tsMs: Long,
) : ProtocolMessage()

@Serializable
@SerialName("query_started")
data class QueryStarted(
    val queryId: String,
    val dbName: String,
    val sql: String,
    val args: List<ArgPreview>,
    val threadName: String,
    val tsMs: Long,
) : ProtocolMessage()

/**
 * `status` ∈ `"ok" | "error"`. The `result*` fields are an additive preview of what a
 * row-returning query returned, paired to the request by **exact `queryId`** (not the
 * standalone's §5.3 time-window heuristic, which is gone with tracing). Absent (default
 * values, omitted from the wire) for non-row statements and on error.
 */
@Serializable
@SerialName("query_finished")
data class QueryFinished(
    val queryId: String,
    val status: String,
    val durationMs: Long,
    val errorMessage: String?,
    val tsMs: Long,
    val resultColumns: List<String>? = null,
    val resultRows: List<List<String?>>? = null,
    val resultRowCount: Int? = null,
    val resultTruncated: Boolean = false,
) : ProtocolMessage()

/** Emitted after the bounded queue's drop-oldest evicts events the writer couldn't drain. */
@Serializable
@SerialName("dropped_events")
data class DroppedEvents(
    val count: Int,
    val tsMs: Long,
) : ProtocolMessage()

/** `fatal: true` means the agent is about to close the session. */
@Serializable
@SerialName("agent_error")
data class AgentError(
    val message: String,
    val fatal: Boolean,
    val tsMs: Long,
) : ProtocolMessage()

// ----- Commands: plugin → agent (and their responses) --------------------------

/** Toggles the capture stream. v1 is just one boolean — no redaction, no sample rate. */
@Serializable
@SerialName("set_capture")
data class SetCapture(
    val enabled: Boolean,
) : ProtocolMessage()

@Serializable
@SerialName("capture_state")
data class CaptureState(
    val enabled: Boolean,
) : ProtocolMessage()
