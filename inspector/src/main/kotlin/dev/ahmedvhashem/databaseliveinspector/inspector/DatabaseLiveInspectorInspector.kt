package dev.ahmedvhashem.databaseliveinspector.inspector

import android.app.Application
import android.os.Process
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device half of Database Live Inspector. Bound at inspection time; runs in the app's
 * process. Pulls capture-event byte arrays from the agent's bounded queue on a dedicated
 * thread and forwards them via App Inspection's [Connection.sendEvent]. Handles one command:
 * `set_capture`.
 *
 * Why raw JSON instead of ProtocolCodec? When the app omits the agent AAR, neither
 * `:protocol` nor `kotlinx-serialization` are reachable from the app's classloader (the
 * parent of the inspector dex's classloader). Referencing those types in the inspector dex's
 * bytecode would make ART fail to verify our classes. So:
 *   - Capture events from the agent are byte arrays already (the agent's [ProtocolCodec]
 *     encodes them on the in-process side, where :protocol IS reachable). We forward bytes
 *     verbatim — no decode/re-encode.
 *   - `app_info`, `agent_error`, and `capture_state` replies are tiny hand-rolled JSON
 *     literals with one or two interpolated values; safe to format manually because their
 *     fields are integers / booleans / fixed package names.
 *   - The only inbound command is `set_capture`; we extract its `enabled` field with a
 *     dumb string scan rather than carrying a full JSON parser in the dex.
 */
internal class DatabaseLiveInspectorInspector(
    connection: Connection,
    private val environment: InspectorEnvironment,
) : Inspector(connection) {

    private val agent = AgentBridge.bind()
    private val stopped = AtomicBoolean(false)
    private val writerThread: Thread

    init {
        if (agent == null) {
            sendBytes(agentMissingErrorJson())
        } else {
            sendBytes(buildAppInfoJson())
        }
        writerThread = Thread(::drainLoop, "dbliveinspector-writer").apply {
            isDaemon = true
            start()
        }
    }

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        if (agent == null) {
            callback.reply(agentMissingErrorJson())
            return
        }
        val text = String(data, Charsets.UTF_8)
        // Only set_capture is supported in v1. Anything else gets a soft agent_error reply.
        if (!text.contains("\"type\":\"set_capture\"")) {
            callback.reply(jsonAgentError("Unsupported command"))
            return
        }
        val enabled = text.contains("\"enabled\":true")
        agent.setEnabled(enabled)
        callback.reply("""{"type":"capture_state","enabled":$enabled}""".toByteArray(Charsets.UTF_8))
    }

    override fun onDispose() {
        stopped.set(true)
        writerThread.interrupt()
        // Bounded join: lets a poll-just-returned-bytes window flush via sendEvent before the
        // framework tears down the Connection. Avoids a half-disposed-connection write race.
        try {
            writerThread.join(WRITER_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Pulls agent events and forwards them. Runs on its own daemon thread. */
    private fun drainLoop() {
        val bridge = agent ?: return
        while (!stopped.get()) {
            val dropped = bridge.takeDroppedCount()
            if (dropped > 0) {
                sendBytes(droppedEventsJson(dropped))
            }
            val bytes = try {
                bridge.pollEvent(WRITER_POLL_MS) ?: continue
            } catch (_: InterruptedException) {
                break
            }
            try {
                connection.sendEvent(bytes)
            } catch (_: Throwable) {
                // Connection write failure: AS will detach us. Stop the loop.
                break
            }
        }
    }

    private fun sendBytes(bytes: ByteArray) {
        try {
            connection.sendEvent(bytes)
        } catch (_: Throwable) {
            // Connection may already be torn down; silently ignore.
        }
    }

    // ----- Hand-rolled JSON helpers (see class kdoc for rationale) ---------------------------

    private fun buildAppInfoJson(): ByteArray {
        val app = environment.artTooling().findInstances(Application::class.java).firstOrNull()
        val appId = app?.packageName ?: "unknown"
        return ("""{"type":"app_info","appId":"$appId","pid":${Process.myPid()},""" +
            """"tsMs":${System.currentTimeMillis()}}""").toByteArray(Charsets.UTF_8)
    }

    private fun agentMissingErrorJson(): ByteArray = jsonAgentError(
        "Database Live Inspector agent library not present in the app — add " +
            "implementation(\\\"dev.ahmedvhashem.databaseliveinspector:agent:1.0.0\\\") " +
            "and call DatabaseLiveInspector.attachTo() at Room-builder time.",
    )

    private fun jsonAgentError(message: String): ByteArray =
        ("""{"type":"agent_error","message":"$message","fatal":false,""" +
            """"tsMs":${System.currentTimeMillis()}}""").toByteArray(Charsets.UTF_8)

    private fun droppedEventsJson(count: Int): ByteArray =
        ("""{"type":"dropped_events","count":$count,""" +
            """"tsMs":${System.currentTimeMillis()}}""").toByteArray(Charsets.UTF_8)

    companion object {
        private const val WRITER_POLL_MS = 100L
        private const val WRITER_JOIN_TIMEOUT_MS = 500L
    }
}
