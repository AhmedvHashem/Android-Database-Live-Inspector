package dev.ahmedvhashem.databaseliveinspector.agent.internal

/**
 * Runtime kill switch. `debug.dbliveinspector.enabled` unset or `1` means enabled, `0`
 * disables. Read via reflection on `android.os.SystemProperties` with a safe enabled fallback
 * (e.g. on a plain JVM where the class does not exist — needed for unit tests).
 */
internal object KillSwitch {

    private const val PROPERTY = "debug.dbliveinspector.enabled"

    fun isEnabled(): Boolean = readSystemProperty(PROPERTY) != "0"

    private fun readSystemProperty(name: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        get.invoke(null, name, "") as? String
    } catch (t: Throwable) {
        null
    }
}
