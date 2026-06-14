package dev.ahmedvhashem.databaseliveinspector.agent.query

import android.database.Cursor

/**
 * Builds the bounded result preview attached to `query_finished`: a sample of what a
 * row-returning query returned, read from the **top** of the result independent of how far
 * the app iterated, so the plugin can show the response inline with the request.
 *
 * Bounds: at most [MAX_ROWS] rows, at most [MAX_COLUMNS] columns, each cell capped at
 * [MAX_CELL_CHARS], the whole preview bounded to [MAX_TOTAL_CHARS] of cell text. Hitting any
 * bound sets `truncated` and reports an unknown (`null`) row count — a full
 * `Cursor.getCount()` is never forced.
 *
 * The row-reading core works against [Rows] (no Android types) so it is unit-testable on a
 * plain JVM; the [sample] overload adapts a real [Cursor] and restores its position afterwards.
 */
internal object CursorResultSampler {

    const val MAX_ROWS = 20
    const val MAX_COLUMNS = 64
    const val MAX_CELL_CHARS = 256
    const val MAX_TOTAL_CHARS = 32_768
    private const val TRUNCATION_MARKER = "…(truncated)"

    // Mirror of android.database.Cursor.FIELD_TYPE_* so the core stays Android-free.
    const val TYPE_NULL = 0
    const val TYPE_INTEGER = 1
    const val TYPE_FLOAT = 2
    const val TYPE_STRING = 3
    const val TYPE_BLOB = 4

    /** Minimal forward cursor the sampler reads from. Implemented over a real [Cursor] below. */
    interface Rows {
        val columnNames: List<String>
        val columnCount: Int
        fun moveToFirst(): Boolean
        fun moveToNext(): Boolean
        fun typeOf(col: Int): Int
        fun longAt(col: Int): Long
        fun doubleAt(col: Int): Double
        fun stringAt(col: Int): String?
        fun blobAt(col: Int): ByteArray
    }

    class Sampled(
        val columns: List<String>,
        val rows: List<List<String?>>,
        /** Exact total when the whole result fit under the bounds; null when more rows exist. */
        val rowCount: Int?,
        val truncated: Boolean,
    )

    /**
     * Samples a still-open [cursor] without consuming it: the cursor position is saved and
     * restored, so the app's own traversal is unaffected. Returns null (preview omitted) if the
     * cursor cannot be read. Never throws — capture must not break the app query path.
     */
    fun sample(cursor: Cursor): Sampled? = try {
        val saved = cursor.position
        try {
            sample(CursorRows(cursor))
        } finally {
            cursor.moveToPosition(saved)
        }
    } catch (t: Throwable) {
        null
    }

    fun sample(rows: Rows): Sampled {
        // Cap columns first so even the always-kept first row is bounded: a single row of
        // thousands of (cheap-to-render) NULL/blob columns would otherwise serialize past the
        // 1 MiB frame cap while slipping under a cell-text budget that ignores them.
        val allColumns = rows.columnNames
        val colCount = minOf(rows.columnCount, MAX_COLUMNS)
        val columns = if (allColumns.size > colCount) allColumns.subList(0, colCount).toList() else allColumns
        var truncated = rows.columnCount > MAX_COLUMNS

        val out = ArrayList<List<String?>>(minOf(MAX_ROWS, 16))
        // Seed the budget with the column-name cost so wide-but-short schemas are counted too.
        var totalChars = columns.sumOf { it.length + 2 }
        var capped = false
        if (rows.moveToFirst()) {
            while (true) {
                if (out.size >= MAX_ROWS) {
                    capped = true
                    break
                }
                val rendered = ArrayList<String?>(colCount)
                var rowChars = 0
                for (col in 0 until colCount) {
                    var cell = renderCell(rows, col)
                    if (cell != null && cell.length > MAX_CELL_CHARS) {
                        cell = cell.take(MAX_CELL_CHARS) + TRUNCATION_MARKER
                        truncated = true
                    }
                    rendered.add(cell)
                    // Count EVERY cell toward the budget (null literal, quotes) so the char
                    // count tracks the real encoded size — the byte budget then holds even
                    // under UTF-8/\uXXXX inflation, keeping the frame well under 1 MiB.
                    rowChars += if (cell == null) 4 else cell.length + 2
                }
                // Keep at least one row even if it alone blows the budget; stop before the next.
                if (out.isNotEmpty() && totalChars + rowChars > MAX_TOTAL_CHARS) {
                    capped = true
                    break
                }
                out.add(rendered)
                totalChars += rowChars
                if (!rows.moveToNext()) break // consumed the entire result
            }
        }
        return Sampled(
            columns = columns,
            rows = out,
            rowCount = if (capped) null else out.size,
            truncated = truncated || capped,
        )
    }

    private fun renderCell(rows: Rows, col: Int): String? =
        when (rows.typeOf(col)) {
            TYPE_NULL -> null
            TYPE_INTEGER -> rows.longAt(col).toString()
            TYPE_FLOAT -> CellFormatter.formatReal(rows.doubleAt(col))
            TYPE_BLOB -> CellFormatter.formatBlob(rows.blobAt(col))
            else -> rows.stringAt(col) ?: ""
        }

    private class CursorRows(private val cursor: Cursor) : Rows {
        override val columnNames: List<String> get() = cursor.columnNames.toList()
        override val columnCount: Int get() = cursor.columnCount
        override fun moveToFirst(): Boolean = cursor.moveToFirst()
        override fun moveToNext(): Boolean = cursor.moveToNext()
        override fun typeOf(col: Int): Int = cursor.getType(col)
        override fun longAt(col: Int): Long = cursor.getLong(col)
        override fun doubleAt(col: Int): Double = cursor.getDouble(col)
        override fun stringAt(col: Int): String? = cursor.getString(col)
        override fun blobAt(col: Int): ByteArray = cursor.getBlob(col)
    }
}
