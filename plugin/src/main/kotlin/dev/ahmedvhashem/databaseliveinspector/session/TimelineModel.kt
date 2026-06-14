package dev.ahmedvhashem.databaseliveinspector.session

import dev.ahmedvhashem.databaseliveinspector.protocol.QueryFinished
import dev.ahmedvhashem.databaseliveinspector.protocol.QueryStarted

/**
 * Bounded (drop-oldest) log of timeline rows. `query_started`/`query_finished` are paired by
 * **exact `queryId`** — the standalone tool's §5.3 time-window snapshot pairing is gone with
 * tracing. Thread-safe and IntelliJ-platform-free, so it's unit-testable.
 */
class TimelineModel(private val capacity: Int = 10_000) {

    data class Row(
        val queryId: String,
        val dbName: String,
        val sql: String,
        val argsText: String,
        val threadName: String,
        val startTsMs: Long,
        val durationMs: Long?,
        val status: String?,
        val errorMessage: String?,
        val resultColumns: List<String>?,
        val resultRows: List<List<String?>>?,
        val resultRowCount: Int?,
        val resultTruncated: Boolean,
    )

    private class Entry(
        val queryId: String,
        val dbName: String,
        val sql: String,
        val argsText: String,
        val threadName: String,
        val startTsMs: Long,
        var durationMs: Long? = null,
        var status: String? = null,
        var errorMessage: String? = null,
        var resultColumns: List<String>? = null,
        var resultRows: List<List<String?>>? = null,
        var resultRowCount: Int? = null,
        var resultTruncated: Boolean = false,
    ) {
        fun toRow() = Row(
            queryId, dbName, sql, argsText, threadName, startTsMs,
            durationMs, status, errorMessage,
            resultColumns, resultRows, resultRowCount, resultTruncated,
        )
    }

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private val byQueryId = HashMap<String, Entry>()

    fun onQueryStarted(event: QueryStarted) {
        val argsText = event.args.joinToString(", ") { "#${it.index} ${it.kind}=${it.preview}" }
        val entry = Entry(
            queryId = event.queryId,
            dbName = event.dbName,
            sql = event.sql,
            argsText = argsText,
            threadName = event.threadName,
            startTsMs = event.tsMs,
        )
        synchronized(lock) {
            if (entries.size >= capacity) {
                val evicted = entries.removeFirst()
                byQueryId.remove(evicted.queryId)
            }
            entries.addLast(entry)
            byQueryId[event.queryId] = entry
        }
    }

    /** Returns true when the finish was paired with a known started row. */
    fun onQueryFinished(event: QueryFinished): Boolean {
        synchronized(lock) {
            val entry = byQueryId[event.queryId] ?: return false
            entry.durationMs = event.durationMs
            entry.status = event.status
            entry.errorMessage = event.errorMessage
            entry.resultColumns = event.resultColumns
            entry.resultRows = event.resultRows
            entry.resultRowCount = event.resultRowCount
            entry.resultTruncated = event.resultTruncated
            return true
        }
    }

    fun snapshotRows(): List<Row> = synchronized(lock) { entries.map { it.toRow() } }
    fun size(): Int = synchronized(lock) { entries.size }
    fun clear() = synchronized(lock) {
        entries.clear()
        byQueryId.clear()
    }
}
