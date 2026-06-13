package com.hashem.databaseliveinspector

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorLaunchConfig
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorMessengerTarget
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Contributes the "Database Live Inspector" tab to Android Studio's App Inspection window.
 */
class DatabaseLiveInspectorTabProvider : AppInspectorTabProvider {
    override val launchConfigs: List<AppInspectorLaunchConfig> = emptyList()

    override val displayName: String = "Database Live Inspector"
    override val icon: Icon = StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR
    override fun isApplicable(): Boolean = true

    override fun createTab(
        project: Project,
        ideServices: AppInspectionIdeServices,
        processDescriptor: ProcessDescriptor,
        messengerTargets: List<AppInspectorMessengerTarget>,
        parentDisposable: Disposable,
    ): AppInspectorTab = object : AppInspectorTab {
        override val messengers: Iterable<AppInspectorMessenger> = emptyList()
        override val component: JComponent = JPanel(BorderLayout()).apply {
            add(
                JBLabel("Database Live Inspector — running inside App Inspection ✅"),
                BorderLayout.CENTER,
            )
        }
    }
}