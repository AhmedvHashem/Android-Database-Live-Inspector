package dev.ahmedvhashem.databaseliveinspector.agent.capture

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delegating [SupportSQLiteOpenHelper.Factory] that times queries and statement executions.
 * SELECT durations are measured from dispatch to cursor close (Room always closes cursors);
 * `execSQL` and compiled statements are timed around the execution call.
 */
internal class InspectorOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val dbName: String,
    private val sink: QueryEventSink,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        InspectorOpenHelper(delegate.create(configuration), dbName, sink)
}

internal class InspectorOpenHelper(
    private val delegate: SupportSQLiteOpenHelper,
    private val dbName: String,
    private val sink: QueryEventSink,
) : SupportSQLiteOpenHelper by delegate {

    override val writableDatabase: SupportSQLiteDatabase
        get() = InspectorDatabase(delegate.writableDatabase, dbName, sink)

    override val readableDatabase: SupportSQLiteDatabase
        get() = InspectorDatabase(delegate.readableDatabase, dbName, sink)
}

internal class InspectorDatabase(
    private val delegate: SupportSQLiteDatabase,
    private val dbName: String,
    private val sink: QueryEventSink,
) : SupportSQLiteDatabase by delegate {

    override fun query(query: String): Cursor =
        trackedQuery(query, emptyList()) { delegate.query(query) }

    override fun query(query: String, bindArgs: Array<out Any?>): Cursor =
        trackedQuery(query, recordArray(bindArgs)) { delegate.query(query, bindArgs) }

    override fun query(query: SupportSQLiteQuery): Cursor =
        trackedQuery(query.sql, recordQueryArgs(query)) { delegate.query(query) }

    override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
        trackedQuery(query.sql, recordQueryArgs(query)) { delegate.query(query, cancellationSignal) }

    override fun execSQL(sql: String) =
        trackedExec(sql, emptyList()) { delegate.execSQL(sql) }

    override fun execSQL(sql: String, bindArgs: Array<out Any?>) =
        trackedExec(sql, recordArray(bindArgs)) { delegate.execSQL(sql, bindArgs) }

    override fun compileStatement(sql: String): SupportSQLiteStatement =
        InspectorStatement(delegate.compileStatement(sql), sql, dbName, sink)

    private inline fun trackedQuery(
        sql: String,
        args: List<RecordedBindArg>,
        block: () -> Cursor,
    ): Cursor {
        val token = sink.queryStarted(dbName, sql, args) ?: return block()
        val cursor = try {
            block()
        } catch (t: Throwable) {
            sink.queryFinished(token, t)
            throw t
        }
        // The cursor is passed to query_finished still open, so the sink can sample the result
        // preview before the app's close() tears it down.
        return InspectorCursor(cursor) { open -> sink.queryFinished(token, null, open) }
    }

    private inline fun trackedExec(sql: String, args: List<RecordedBindArg>, block: () -> Unit) {
        val token = sink.queryStarted(dbName, sql, args) ?: return block()
        try {
            block()
            sink.queryFinished(token, null)
        } catch (t: Throwable) {
            sink.queryFinished(token, t)
            throw t
        }
    }

    private fun recordQueryArgs(query: SupportSQLiteQuery): List<RecordedBindArg> = try {
        val recorder = BindArgRecorder()
        query.bindTo(recorder)
        recorder.snapshot()
    } catch (t: Throwable) {
        emptyList()
    }

    private fun recordArray(bindArgs: Array<out Any?>): List<RecordedBindArg> =
        bindArgs.mapIndexed { i, value -> recordValue(i + 1, value) }

    private fun recordValue(index: Int, value: Any?): RecordedBindArg = when (value) {
        null -> RecordedBindArg.NullArg(index)
        is Long -> RecordedBindArg.LongArg(index, value)
        is Int -> RecordedBindArg.LongArg(index, value.toLong())
        is Short -> RecordedBindArg.LongArg(index, value.toLong())
        is Byte -> RecordedBindArg.LongArg(index, value.toLong())
        is Boolean -> RecordedBindArg.LongArg(index, if (value) 1L else 0L)
        is Double -> RecordedBindArg.DoubleArg(index, value)
        is Float -> RecordedBindArg.DoubleArg(index, value.toDouble())
        is ByteArray -> RecordedBindArg.BlobArg(index, value)
        is String -> RecordedBindArg.StringArg(index, value)
        else -> RecordedBindArg.StringArg(index, value.toString())
    }
}

/** Records bind calls issued by [SupportSQLiteQuery.bindTo] without touching a database. */
internal class BindArgRecorder : SupportSQLiteProgram {

    private val args = sortedMapOf<Int, RecordedBindArg>()

    fun snapshot(): List<RecordedBindArg> = args.values.toList()

    override fun bindNull(index: Int) {
        args[index] = RecordedBindArg.NullArg(index)
    }

    override fun bindLong(index: Int, value: Long) {
        args[index] = RecordedBindArg.LongArg(index, value)
    }

    override fun bindDouble(index: Int, value: Double) {
        args[index] = RecordedBindArg.DoubleArg(index, value)
    }

    override fun bindString(index: Int, value: String) {
        args[index] = RecordedBindArg.StringArg(index, value)
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        args[index] = RecordedBindArg.BlobArg(index, value)
    }

    override fun clearBindings() {
        args.clear()
    }

    override fun close() {
        // Nothing to release.
    }
}

internal class InspectorStatement(
    private val delegate: SupportSQLiteStatement,
    private val sql: String,
    private val dbName: String,
    private val sink: QueryEventSink,
) : SupportSQLiteStatement by delegate {

    private val boundArgs = sortedMapOf<Int, RecordedBindArg>()

    override fun bindNull(index: Int) {
        boundArgs[index] = RecordedBindArg.NullArg(index)
        delegate.bindNull(index)
    }

    override fun bindLong(index: Int, value: Long) {
        boundArgs[index] = RecordedBindArg.LongArg(index, value)
        delegate.bindLong(index, value)
    }

    override fun bindDouble(index: Int, value: Double) {
        boundArgs[index] = RecordedBindArg.DoubleArg(index, value)
        delegate.bindDouble(index, value)
    }

    override fun bindString(index: Int, value: String) {
        boundArgs[index] = RecordedBindArg.StringArg(index, value)
        delegate.bindString(index, value)
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        boundArgs[index] = RecordedBindArg.BlobArg(index, value)
        delegate.bindBlob(index, value)
    }

    override fun clearBindings() {
        boundArgs.clear()
        delegate.clearBindings()
    }

    override fun execute() = tracked { delegate.execute() }

    override fun executeInsert(): Long = tracked { delegate.executeInsert() }

    override fun executeUpdateDelete(): Int = tracked { delegate.executeUpdateDelete() }

    override fun simpleQueryForLong(): Long = tracked { delegate.simpleQueryForLong() }

    override fun simpleQueryForString(): String? = tracked { delegate.simpleQueryForString() }

    private inline fun <T> tracked(block: () -> T): T {
        val token = sink.queryStarted(dbName, sql, boundArgs.values.toList()) ?: return block()
        return try {
            val result = block()
            sink.queryFinished(token, null)
            result
        } catch (t: Throwable) {
            sink.queryFinished(token, t)
            throw t
        }
    }
}

/**
 * Emits `query_finished` exactly once when the cursor is closed. [onClose] runs with the
 * delegate **still open** (so the sink can sample the result) and immediately before it is
 * closed; it must not throw — any failure is swallowed so the app's close() always proceeds.
 */
internal class InspectorCursor(
    private val delegate: Cursor,
    private val onClose: (Cursor) -> Unit,
) : Cursor by delegate {

    private val finished = AtomicBoolean(false)

    override fun close() {
        if (finished.compareAndSet(false, true)) {
            try {
                onClose(delegate)
            } catch (_: Throwable) {
                // Sampling / emission must never break the app's cursor lifecycle.
            }
        }
        delegate.close()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getNotificationUris(): List<Uri?>? {
        return delegate.getNotificationUris()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun setNotificationUris(cr: ContentResolver, uris: List<Uri?>) {
        delegate.setNotificationUris(cr, uris)
    }
}
