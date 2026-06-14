package dev.ahmedvhashem.databaseliveinspector.agent

import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.Cursor
import androidx.annotation.Keep
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import dev.ahmedvhashem.databaseliveinspector.agent.capture.InspectorOpenHelperFactory
import dev.ahmedvhashem.databaseliveinspector.agent.capture.QueryEventSink
import dev.ahmedvhashem.databaseliveinspector.agent.capture.QueryToken
import dev.ahmedvhashem.databaseliveinspector.agent.capture.RecordedBindArg
import dev.ahmedvhashem.databaseliveinspector.agent.internal.BoundedEventQueue
import dev.ahmedvhashem.databaseliveinspector.agent.internal.KillSwitch
import dev.ahmedvhashem.databaseliveinspector.agent.internal.Limits
import dev.ahmedvhashem.databaseliveinspector.agent.query.CursorResultSampler
import dev.ahmedvhashem.databaseliveinspector.protocol.ArgPreview
import dev.ahmedvhashem.databaseliveinspector.protocol.ProtocolCodec
import dev.ahmedvhashem.databaseliveinspector.protocol.ProtocolMessage
import dev.ahmedvhashem.databaseliveinspector.protocol.QueryFinished
import dev.ahmedvhashem.databaseliveinspector.protocol.QueryStarted
import java.util.concurrent.atomic.AtomicLong

/**
 * Public app-facing API for Database Live Inspector.
 *
 * Apps integrate with two calls at startup:
 * ```
 * DatabaseLiveInspector.install(context)
 * val db = Room.databaseBuilder(context, MyDb::class.java, "mydb")
 *     .also { DatabaseLiveInspector.attachTo(it, "mydb") }
 *     .build()
 * ```
 *
 * Every entry point is a no-op unless [install] ran in a process with `FLAG_DEBUGGABLE` set
 * and the `debug.dbliveinspector.enabled` kill switch is not `0`. The agent only buffers
 * events — the injected inspector dex (when present) pulls them via the reflective bridge in
 * [pollEvent] and [takeDroppedCount].
 */
@Keep
object DatabaseLiveInspector {

    @Volatile private var active = false
    @Volatile private var captureEnabled = true
    private val eventQueue = BoundedEventQueue<ByteArray>(Limits.EVENT_QUEUE_CAPACITY)
    private val queryIds = AtomicLong(0)

    /** Activates the agent (idempotent). No-op on release builds or when the kill switch is off. */
    @JvmStatic
    fun install(context: Context) {
        if (active) return
        synchronized(this) {
            if (active) return
            try {
                val appContext = context.applicationContext ?: context
                if (!isDebuggable(appContext)) return
                if (!KillSwitch.isEnabled()) return
                active = true
            } catch (t: Throwable) {
                // Installation failure must never break the app.
            }
        }
    }

    /**
     * Wraps the builder's open-helper factory so queries against [dbName] are captured.
     *
     * [delegateFactory] defaults to the framework SQLite factory — pass an explicit factory
     * (e.g. SQLCipher's `SupportFactory`, or a test fake) if the app already uses one, since
     * Room's builder API offers no way to read back a previously-set factory and a silent
     * overwrite would be a real correctness/security regression.
     */
    @JvmStatic
    @JvmOverloads
    fun <T : RoomDatabase> attachTo(
        builder: RoomDatabase.Builder<T>,
        dbName: String,
        delegateFactory: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
    ): RoomDatabase.Builder<T> {
        if (!active) return builder
        return try {
            builder.openHelperFactory(InspectorOpenHelperFactory(delegateFactory, dbName, sink))
        } catch (t: Throwable) {
            builder
        }
    }

    /** Toggles the capture stream without dropping queued events. */
    @JvmStatic
    fun setEnabled(value: Boolean) {
        captureEnabled = value
    }

    // ----- Bridge surface (called via reflection by the injected inspector dex) -----------
    // These are part of the stable reflective contract: do NOT rename or change signatures
    // without updating the inspector module's AgentBridge.

    /** Pulls the next queued event payload (JSON bytes), blocking up to [timeoutMs]. */
    @JvmStatic
    fun pollEvent(timeoutMs: Long): ByteArray? = eventQueue.poll(timeoutMs)

    /** Returns the number of events dropped since the last call, resetting the counter. */
    @JvmStatic
    fun takeDroppedCount(): Int = eventQueue.takeDroppedCount()

    /** Records an out-of-band dropped event (e.g. the writer skipped an oversize frame). */
    @JvmStatic
    fun recordDropped() = eventQueue.recordDropped()

    // ----- Internal: the capture sink that builds protocol events from SQL callbacks -----

    private val sink = object : QueryEventSink {
        override fun queryStarted(
            dbName: String,
            sql: String,
            args: List<RecordedBindArg>,
        ): QueryToken? {
            if (!isCapturing()) return null
            val token = QueryToken("q-${queryIds.incrementAndGet()}", System.nanoTime())
            enqueue(
                QueryStarted(
                    queryId = token.queryId,
                    dbName = dbName,
                    sql = sql.take(Limits.MAX_SQL_CHARS),
                    args = args.map(::argPreview),
                    threadName = Thread.currentThread().name,
                    tsMs = System.currentTimeMillis(),
                ),
            )
            return token
        }

        override fun queryFinished(token: QueryToken, error: Throwable?, resultCursor: Cursor?) {
            // Stamp the finish time BEFORE sampling so sampling doesn't inflate durationMs.
            val finishedNanos = System.nanoTime()
            try {
                val sample = if (error == null && resultCursor != null && isCapturing()) {
                    CursorResultSampler.sample(resultCursor)
                } else {
                    null
                }
                enqueue(
                    QueryFinished(
                        queryId = token.queryId,
                        status = if (error == null) "ok" else "error",
                        durationMs = (finishedNanos - token.startedNanos) / 1_000_000,
                        errorMessage = error?.let { it.message ?: it.javaClass.simpleName },
                        tsMs = System.currentTimeMillis(),
                        resultColumns = sample?.columns,
                        resultRows = sample?.rows,
                        resultRowCount = sample?.rowCount,
                        resultTruncated = sample?.truncated ?: false,
                    ),
                )
            } catch (t: Throwable) {
                // Capture must never break the app's query path.
            }
        }
    }

    private fun isCapturing(): Boolean = active && captureEnabled

    private fun enqueue(message: ProtocolMessage) {
        try {
            eventQueue.offer(ProtocolCodec.encode(message))
        } catch (t: Throwable) {
            // Encoding/queuing failure must not break capture.
        }
    }

    private fun argPreview(arg: RecordedBindArg): ArgPreview = when (arg) {
        is RecordedBindArg.NullArg -> ArgPreview(arg.index, "null", "null")
        is RecordedBindArg.LongArg -> ArgPreview(arg.index, "long", arg.value.toString())
        is RecordedBindArg.DoubleArg -> ArgPreview(arg.index, "double", arg.value.toString())
        is RecordedBindArg.StringArg ->
            ArgPreview(arg.index, "string", arg.value.take(Limits.MAX_ARG_PREVIEW_CHARS))
        is RecordedBindArg.BlobArg ->
            ArgPreview(arg.index, "blob", "blob(${arg.value.size} bytes)")
    }

    private fun isDebuggable(context: Context): Boolean {
        val info = context.applicationInfo ?: return false
        return (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
