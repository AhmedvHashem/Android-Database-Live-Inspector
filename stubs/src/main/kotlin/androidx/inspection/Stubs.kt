// Vendored compile-only stubs of androidx.inspection. The real implementations are provided
// by Android Studio at inspector-injection time (see the androidx.inspection README — the
// artifact is NOT on Maven). At runtime, AS-loaded classes win over our stubs because the
// inspector dex doesn't bundle this package; the :inspector module declares these types as
// `compileOnly` so they're not packaged.
//
// Only methods/types our :inspector code actually calls or overrides are declared here.
// JVM signatures (constructor parameters, method names + types) match the upstream source at
// frameworks/support/inspection/inspection/src/main/java/androidx/inspection/ — keeping
// signatures stable is what makes the runtime binding safe.

package androidx.inspection

/**
 * Subclassed by [dev.ahmedvhashem.databaseliveinspector.inspector.DatabaseLiveInspectorInspector].
 * The `connection` property here matches the upstream `protected final Connection getConnection()`
 * accessor — Kotlin's property compiles to a `getConnection()` JVM getter the runtime resolves.
 */
abstract class Inspector protected constructor(connection: Connection) {

    @Suppress("CanBePrimaryConstructorProperty")
    protected val connection: Connection = connection

    abstract fun onReceiveCommand(data: ByteArray, callback: CommandCallback)

    open fun onDispose() {}

    interface CommandCallback {
        fun reply(response: ByteArray)
    }
}

abstract class InspectorFactory<T : Inspector> protected constructor(inspectorId: String) {

    @Suppress("CanBePrimaryConstructorProperty")
    val inspectorId: String = inspectorId

    abstract fun createInspector(connection: Connection, environment: InspectorEnvironment): T
}

abstract class Connection {
    open fun sendEvent(data: ByteArray) {}
}

interface InspectorEnvironment {
    fun artTooling(): ArtTooling
}

interface ArtTooling {
    fun <T> findInstances(clazz: Class<T>): MutableList<T>
}
