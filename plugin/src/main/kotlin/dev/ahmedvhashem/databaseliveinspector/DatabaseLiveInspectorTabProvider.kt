package dev.ahmedvhashem.databaseliveinspector

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorLaunchConfig
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorMessengerTarget
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import dev.ahmedvhashem.databaseliveinspector.ui.InspectorPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.io.File
import java.nio.file.Files
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Contributes the "Database Live Inspector" tab to Android Studio's App Inspection window.
 *
 * Pairs with two artifacts produced by the same Gradle build:
 *  - `inspector.jar` — the injected on-device DEX (the `AppInspectorJar` below points at it);
 *    bundled into this plugin's resources by `plugin/build.gradle.kts` from `:inspector`.
 *  - the `:agent` AAR — the app opts in by depending on it and calling
 *    `DatabaseLiveInspector.attachTo(builder, dbName)` at Room-builder time. When the agent
 *    isn't present, the inspector still launches and shows an "agent library not present"
 *    notice via `agent_error` (rendered in the panel's status label).
 */
class DatabaseLiveInspectorTabProvider : AppInspectorTabProvider {

    /**
     * Bundle path: the plugin's processResources copies the dex to `inspector/inspector.jar`
     * **inside** plugin-1.0.0.jar. AppInspectorJar.releaseDirectory is resolved by AS as a path
     * on disk (relative to <AS_HOME>, or used as-is when absolute) — it does NOT look up
     * classpath resources. So we extract the bundled jar to a temp file once per IDE session
     * and hand AppInspectorJar the absolute parent directory.
     */
    private val extractedInspectorJar: File by lazy {
        val tmp = Files.createTempFile("dbliveinspector-", ".jar").toFile().apply {
            deleteOnExit()
        }
        DatabaseLiveInspectorTabProvider::class.java
            .getResourceAsStream("/inspector/inspector.jar")
            ?.use { it.copyTo(tmp.outputStream()) }
            ?: error("Bundled inspector.jar resource missing from plugin classpath")
        tmp
    }

    override val launchConfigs: List<AppInspectorLaunchConfig>
        get() = listOf(
            AppInspectorLaunchConfig(
                id = INSPECTOR_ID,
                params = FrameworkInspectorLaunchParams(
                    inspectorAgentJar = AppInspectorJar(
                        name = extractedInspectorJar.name,
                        // Absolute paths short-circuit DeployableFile.getDir's <AS_HOME>
                        // prefixing — AS uses the path verbatim.
                        developmentDirectory = extractedInspectorJar.parentFile.absolutePath,
                        releaseDirectory = extractedInspectorJar.parentFile.absolutePath,
                    ),
                ),
            ),
        )

    override val displayName: String = "Database Live Inspector"
    override val icon: Icon = StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR
    override fun isApplicable(): Boolean = true

    override fun createTab(
        project: Project,
        ideServices: AppInspectionIdeServices,
        processDescriptor: ProcessDescriptor,
        messengerTargets: List<AppInspectorMessengerTarget>,
        parentDisposable: Disposable,
    ): AppInspectorTab {
        val resolved = messengerTargets.singleOrNull() as? AppInspectorMessengerTarget.Resolved
        if (resolved == null) {
            val unresolved = messengerTargets.singleOrNull() as? AppInspectorMessengerTarget.Unresolved
            return placeholderTab(
                "Database Live Inspector could not start: ${unresolved?.error ?: "unknown error"}",
            )
        }
        val scope = createScope(parentDisposable)
        val panel = InspectorPanel(project, resolved.messenger, scope)
        return object : AppInspectorTab {
            override val messengers: Iterable<AppInspectorMessenger> = listOf(resolved.messenger)
            override val component: JComponent = panel.component
        }
    }

    private fun placeholderTab(text: String): AppInspectorTab = object : AppInspectorTab {
        override val messengers = emptyList<AppInspectorMessenger>()
        override val component: JComponent = JPanel(BorderLayout()).apply {
            add(JBLabel(text), BorderLayout.CENTER)
        }
    }

    private fun createScope(parentDisposable: Disposable): CoroutineScope {
        val job = SupervisorJob()
        Disposer.register(parentDisposable) { job.cancel() }
        return CoroutineScope(job + Dispatchers.IO)
    }

    companion object {
        /** Must match the inspector dex's `InspectorFactory.inspectorId`. */
        const val INSPECTOR_ID = "dev.ahmedvhashem.databaseliveinspector.inspector"
    }
}
