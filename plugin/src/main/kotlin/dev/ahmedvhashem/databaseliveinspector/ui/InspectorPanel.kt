package dev.ahmedvhashem.databaseliveinspector.ui

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import dev.ahmedvhashem.databaseliveinspector.protocol.AgentError
import dev.ahmedvhashem.databaseliveinspector.protocol.AppInfo
import dev.ahmedvhashem.databaseliveinspector.protocol.DroppedEvents
import dev.ahmedvhashem.databaseliveinspector.protocol.ProtocolMessage
import dev.ahmedvhashem.databaseliveinspector.protocol.QueryFinished
import dev.ahmedvhashem.databaseliveinspector.protocol.QueryStarted
import dev.ahmedvhashem.databaseliveinspector.session.MessengerSession
import dev.ahmedvhashem.databaseliveinspector.session.TimelineModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel

/**
 * The Database Live Inspector tab content. Top pane is the query list; bottom pane is a
 * tabbed view (Request | Response) bound to the selected row. Toolbar has Pause/Resume,
 * Clear, and a status label that reflects connection/agent state.
 *
 * Threading: messenger events arrive on the coroutine collector thread (see
 * [MessengerSession]) and hop to EDT here before touching Swing.
 */
class InspectorPanel(
    @Suppress("UNUSED_PARAMETER") project: Project,
    messenger: AppInspectorMessenger,
    private val scope: CoroutineScope,
) : MessengerSession.Listener {

    private val timeline = TimelineModel()
    private val tableModel = QueryTableModel()
    private val table = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = false
    }

    private val pauseButton = JToggleButton("Pause capture")
    private val clearButton = JButton("Clear", AllIcons.Actions.GC)
    private val statusLabel = JBLabel("Connecting…")

    private val requestTab = RequestTab()
    private val responseTab = ResponseTab()
    private val tabs = JBTabbedPane().apply {
        addTab("Request", requestTab.component)
        addTab("Response", responseTab.component)
    }

    private val refreshPending = AtomicBoolean(false)
    private var lastRenderedRow: TimelineModel.Row? = null

    val component: JComponent = buildLayout()

    init {
        wireActions()
    }

    // Initialized LAST so every field above is constructed by the time the messenger coroutine
    // starts firing onEvent on this listener.
    @Suppress("unused")
    private val session: MessengerSession = MessengerSession(messenger, scope, this)

    // ----- layout / actions ----------------------------------------------------

    private fun buildLayout(): JComponent {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(pauseButton)
            add(clearButton)
            add(statusLabel)
        }
        val split = JBSplitter(true, 0.55f).apply {
            firstComponent = JBScrollPane(table)
            secondComponent = tabs
        }
        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
        }
    }

    private fun wireActions() {
        pauseButton.addActionListener {
            val newEnabled = !pauseButton.isSelected
            scope.launch {
                val state = session.setCapture(newEnabled)
                onEdt {
                    if (state != null) {
                        pauseButton.isSelected = !state.enabled
                        statusLabel.text = if (state.enabled) "Capturing" else "Paused"
                    }
                }
            }
        }
        clearButton.addActionListener {
            timeline.clear()
            refreshTable()
            lastRenderedRow = null
            requestTab.show(null)
            responseTab.show(null)
        }
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) showSelectedDetails()
        }
    }

    // ----- MessengerSession.Listener (called on the coroutine collector thread) -----

    override fun onEvent(message: ProtocolMessage) {
        when (message) {
            is AppInfo -> onEdt {
                statusLabel.text = "Capturing — ${message.appId} (pid ${message.pid})"
            }
            is QueryStarted -> {
                timeline.onQueryStarted(message)
                requestRefresh()
            }
            is QueryFinished -> {
                timeline.onQueryFinished(message)
                requestRefresh()
            }
            is DroppedEvents -> onEdt {
                statusLabel.text = "Capturing — ${message.count} event(s) dropped"
            }
            is AgentError -> onEdt {
                statusLabel.text = "Agent: ${message.message}"
            }
            else -> Unit  // CaptureState reply is consumed by setCapture's suspend call.
        }
    }

    // ----- EDT plumbing -------------------------------------------------------

    private fun requestRefresh() {
        if (refreshPending.compareAndSet(false, true)) {
            onEdt {
                refreshPending.set(false)
                refreshTable()
                val selected = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.rowAt(it) }
                // Re-render only when the selected row's content actually changed, so unrelated
                // stream events don't clobber the user's text selection / scroll position in
                // the Request/Response tabs.
                if (selected != lastRenderedRow) showSelectedDetails()
            }
        }
    }

    private fun refreshTable() {
        val selectedQueryId = table.selectedRow.takeIf { it >= 0 }
            ?.let { tableModel.rowAt(it)?.queryId }
        tableModel.setRows(timeline.snapshotRows())
        if (selectedQueryId != null) {
            val index = tableModel.indexOfQueryId(selectedQueryId)
            if (index >= 0) table.setRowSelectionInterval(index, index)
        }
    }

    private fun showSelectedDetails() {
        val row = table.selectedRow.takeIf { it >= 0 }?.let { tableModel.rowAt(it) }
        lastRenderedRow = row
        requestTab.show(row)
        responseTab.show(row)
    }

    private fun onEdt(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) block() else app.invokeLater(block)
    }
}
