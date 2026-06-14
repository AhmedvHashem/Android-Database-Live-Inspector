package dev.ahmedvhashem.databaseliveinspector.agent.capture

import android.database.Cursor

/** A bind argument recorded at capture time. */
internal sealed class RecordedBindArg(val index: Int) {
    class NullArg(index: Int) : RecordedBindArg(index)
    class LongArg(index: Int, val value: Long) : RecordedBindArg(index)
    class DoubleArg(index: Int, val value: Double) : RecordedBindArg(index)
    class StringArg(index: Int, val value: String) : RecordedBindArg(index)
    class BlobArg(index: Int, val value: ByteArray) : RecordedBindArg(index)
}

/** Correlates a `query_started` with its `query_finished`. */
internal class QueryToken(val queryId: String, val startedNanos: Long)

/**
 * Receives capture callbacks from the SQLite wrappers. Implementations must never throw and
 * must never block: callbacks only enqueue.
 */
internal interface QueryEventSink {

    /**
     * Emits `query_started` and returns a token, or null when this query is not captured
     * (no session, capture paused, agent disabled).
     */
    fun queryStarted(dbName: String, sql: String, args: List<RecordedBindArg>): QueryToken?

    /**
     * Emits `query_finished` with status "ok" (null [error]) or "error".
     *
     * [resultCursor], when non-null, is the row-returning query's cursor, still open and
     * positioned wherever the app left it. The sink may sample a bounded result preview from
     * it before returning; it must not consume or close it. Non-row statements (execSQL,
     * insert/update/delete) and the error path pass null.
     */
    fun queryFinished(token: QueryToken, error: Throwable?, resultCursor: Cursor? = null)
}
