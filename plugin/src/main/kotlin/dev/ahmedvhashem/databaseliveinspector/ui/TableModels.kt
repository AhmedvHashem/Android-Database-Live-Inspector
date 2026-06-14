package dev.ahmedvhashem.databaseliveinspector.ui

import dev.ahmedvhashem.databaseliveinspector.session.TimelineModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.table.AbstractTableModel

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

internal fun formatTsMs(tsMs: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(tsMs))

internal fun ellipsize(text: String, max: Int = 160): String {
    val singleLine = text.replace(Regex("\\s+"), " ").trim()
    return if (singleLine.length <= max) singleLine else singleLine.take(max - 1) + "…"
}

/** Top-list table: [Time, DB, SQL, Thread, Duration, Status]. */
internal class QueryTableModel : AbstractTableModel() {

    private val columns = listOf("Time", "DB", "SQL", "Thread", "Duration (ms)", "Status")
    private var rows: List<TimelineModel.Row> = emptyList()

    fun setRows(newRows: List<TimelineModel.Row>) {
        rows = newRows
        fireTableDataChanged()
    }

    fun rowAt(index: Int): TimelineModel.Row? = rows.getOrNull(index)

    fun indexOfQueryId(queryId: String): Int = rows.indexOfFirst { it.queryId == queryId }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> formatTsMs(row.startTsMs)
            1 -> row.dbName
            2 -> ellipsize(row.sql)
            3 -> row.threadName
            4 -> row.durationMs?.toString() ?: "…"
            5 -> row.status ?: "running"
            else -> ""
        }
    }
}

/** Grid that backs the Response tab; columns vary per selected query. */
internal class ResultsTableModel : AbstractTableModel() {

    private var columns: List<String> = emptyList()
    private var rows: List<List<String?>> = emptyList()

    fun setData(newColumns: List<String>, newRows: List<List<String?>>) {
        columns = newColumns
        rows = newRows
        fireTableStructureChanged()
    }

    fun clear() = setData(emptyList(), emptyList())

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns.getOrElse(column) { "" }
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
        rows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: "NULL"
}
