package dev.ahmedvhashem.databaseliveinspector.ui

import dev.ahmedvhashem.databaseliveinspector.session.TimelineModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/** Renders the result-row half of the selected row: captured columns + cells, or error/state text. */
internal class ResponseTab {

    private val summaryLabel = JBLabel(" ").apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }
    private val tableModel = ResultsTableModel()
    private val table = JBTable(tableModel)

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(summaryLabel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun show(row: TimelineModel.Row?) {
        when {
            row == null -> {
                summaryLabel.text = " "
                tableModel.clear()
            }
            row.errorMessage != null -> {
                summaryLabel.text = "Error: ${row.errorMessage}"
                tableModel.clear()
            }
            row.status == null -> {
                summaryLabel.text = "Running…"
                tableModel.clear()
            }
            row.resultColumns == null -> {
                summaryLabel.text = "No rows returned (non-row statement)"
                tableModel.clear()
            }
            else -> {
                val count = row.resultRowCount?.toString() ?: "${row.resultRows?.size ?: 0}+"
                summaryLabel.text = "$count row(s) captured" +
                    if (row.resultTruncated) "  (preview truncated)" else ""
                tableModel.setData(row.resultColumns, row.resultRows ?: emptyList())
            }
        }
    }
}
