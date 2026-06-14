package dev.ahmedvhashem.databaseliveinspector.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden serialization tests. Pin the exact JSON bytes for every wire type so:
 * - the kept message shapes stay byte-identical to the standalone tool's PROTOCOL.md (so the
 *   shipped capture logic remains reusable verbatim), and
 * - the additive `query_finished` result-row fields stay omitted on the wire at default
 *   values (load-bearing for backward compatibility with older receivers).
 */
class ProtocolGoldenTest {

    private fun assertGolden(expectedJson: String, message: ProtocolMessage) {
        assertEquals(expectedJson, ProtocolCodec.encodeToString(message))
        val decoded = ProtocolCodec.decode(expectedJson)
        assertTrue("decode of golden JSON must succeed: $decoded", decoded is ProtocolCodec.DecodeResult.Success)
        assertEquals(message, (decoded as ProtocolCodec.DecodeResult.Success).message)
    }

    @Test
    fun `app_info`() = assertGolden(
        """{"type":"app_info","appId":"com.challenge.feed","pid":12345,"tsMs":0}""",
        AppInfo(appId = "com.challenge.feed", pid = 12345, tsMs = 0),
    )

    @Test
    fun `query_started`() = assertGolden(
        """{"type":"query_started","queryId":"q-1","dbName":"orders_database",""" +
            """"sql":"SELECT * FROM order_items","args":[{"index":1,"kind":"string","preview":"ab"}],""" +
            """"threadName":"arch_disk_io_0","tsMs":0}""",
        QueryStarted(
            queryId = "q-1",
            dbName = "orders_database",
            sql = "SELECT * FROM order_items",
            args = listOf(ArgPreview(index = 1, kind = "string", preview = "ab")),
            threadName = "arch_disk_io_0",
            tsMs = 0,
        ),
    )

    @Test
    fun `query_finished without result fields`() = assertGolden(
        // Default-value result fields must be omitted from the wire (encodeDefaults = false).
        """{"type":"query_finished","queryId":"q-1","status":"ok","durationMs":3,""" +
            """"errorMessage":null,"tsMs":0}""",
        QueryFinished(queryId = "q-1", status = "ok", durationMs = 3, errorMessage = null, tsMs = 0),
    )

    @Test
    fun `query_finished with result preview`() = assertGolden(
        """{"type":"query_finished","queryId":"q-1","status":"ok","durationMs":3,""" +
            """"errorMessage":null,"tsMs":0,"resultColumns":["id","name"],""" +
            """"resultRows":[["1","Pizza"],["2",null]],"resultRowCount":2}""",
        QueryFinished(
            queryId = "q-1",
            status = "ok",
            durationMs = 3,
            errorMessage = null,
            tsMs = 0,
            resultColumns = listOf("id", "name"),
            resultRows = listOf(listOf("1", "Pizza"), listOf("2", null)),
            resultRowCount = 2,
            resultTruncated = false,
        ),
    )

    @Test
    fun `query_finished truncated preview omits row count`() = assertGolden(
        // Truncated preview: resultRowCount = null (omitted), resultTruncated = true (present).
        """{"type":"query_finished","queryId":"q-9","status":"ok","durationMs":1,""" +
            """"errorMessage":null,"tsMs":0,"resultColumns":["v"],"resultRows":[["x"]],""" +
            """"resultTruncated":true}""",
        QueryFinished(
            queryId = "q-9",
            status = "ok",
            durationMs = 1,
            errorMessage = null,
            tsMs = 0,
            resultColumns = listOf("v"),
            resultRows = listOf(listOf("x")),
            resultRowCount = null,
            resultTruncated = true,
        ),
    )

    @Test
    fun `dropped_events`() = assertGolden(
        """{"type":"dropped_events","count":17,"tsMs":0}""",
        DroppedEvents(count = 17, tsMs = 0),
    )

    @Test
    fun `agent_error`() = assertGolden(
        """{"type":"agent_error","message":"...","fatal":false,"tsMs":0}""",
        AgentError(message = "...", fatal = false, tsMs = 0),
    )

    @Test
    fun `set_capture`() = assertGolden(
        """{"type":"set_capture","enabled":true}""",
        SetCapture(enabled = true),
    )

    @Test
    fun `capture_state`() = assertGolden(
        """{"type":"capture_state","enabled":false}""",
        CaptureState(enabled = false),
    )

    @Test
    fun `unknown message type is reported as UnknownType, not fatal`() {
        val result = ProtocolCodec.decode("""{"type":"future_event","foo":1}""")
        assertTrue("expected UnknownType, got $result", result is ProtocolCodec.DecodeResult.UnknownType)
        assertEquals("future_event", (result as ProtocolCodec.DecodeResult.UnknownType).type)
    }

    @Test
    fun `unknown fields on a known type are ignored`() {
        val result = ProtocolCodec.decode(
            """{"type":"set_capture","enabled":true,"futureField":{"nested":42}}""",
        )
        assertTrue("expected Success, got $result", result is ProtocolCodec.DecodeResult.Success)
        assertEquals(SetCapture(enabled = true), (result as ProtocolCodec.DecodeResult.Success).message)
    }

    @Test
    fun `malformed JSON is a Failure, not a throw`() {
        val result = ProtocolCodec.decode("not-json")
        assertTrue("expected Failure, got $result", result is ProtocolCodec.DecodeResult.Failure)
    }
}
