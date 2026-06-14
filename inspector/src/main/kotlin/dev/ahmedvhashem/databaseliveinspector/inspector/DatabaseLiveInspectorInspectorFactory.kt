package dev.ahmedvhashem.databaseliveinspector.inspector

import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorFactory

/**
 * The InspectorFactory the framework discovers via ServiceLoader. The factory's inspector ID
 * **must** match what the plugin's `AppInspectorLaunchConfig` declares
 * (`dev.ahmedvhashem.databaseliveinspector.inspector`).
 */
internal class DatabaseLiveInspectorInspectorFactory :
    InspectorFactory<DatabaseLiveInspectorInspector>(INSPECTOR_ID) {

    override fun createInspector(
        connection: Connection,
        environment: InspectorEnvironment,
    ): DatabaseLiveInspectorInspector = DatabaseLiveInspectorInspector(connection, environment)

    companion object {
        const val INSPECTOR_ID = "dev.ahmedvhashem.databaseliveinspector.inspector"
    }
}
