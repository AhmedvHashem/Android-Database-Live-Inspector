package dev.ahmedvhashem.databaseliveinspector.ui

import dev.ahmedvhashem.databaseliveinspector.session.TimelineModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Renders the SQL request half of the selected row: SQL text, args, db, thread, timings. */
internal class RequestTab {

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    fun show(row: TimelineModel.Row?) {
        if (row == null) {
            textArea.text = ""
            return
        }
        textArea.text = buildString {
            appendLine("queryId: ${row.queryId}    db: ${row.dbName}    thread: ${row.threadName}")
            appendLine(
                "started: ${formatTsMs(row.startTsMs)}    " +
                    "duration: ${row.durationMs ?: "…"} ms    " +
                    "status: ${row.status ?: "running"}",
            )
            row.errorMessage?.let { appendLine("error: $it") }
            appendLine()
            appendLine("SQL:")
            appendLine(row.sql)
            if (row.argsText.isNotEmpty()) {
                appendLine()
                appendLine("Args: ${row.argsText}")
            }
        }
        textArea.caretPosition = 0
    }
}
