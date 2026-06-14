package dev.ahmedvhashem.databaseliveinspector.inspector

/**
 * Reflective bridge from the injected inspector DEX into the agent AAR shipped with the app.
 *
 * The inspector dex has no compile-time dependency on `:agent` — it looks the agent up by FQN
 * at runtime so the dex still loads (and the App Inspection tab still appears) on apps that
 * don't include the agent. When the agent is present, [bind] returns a typed view of its
 * reflective surface; when absent, [bind] returns null and the inspector reports an
 * `agent_error` event for the plugin's status label.
 *
 * The bound surface is the stable reflective contract — three @JvmStatic methods on
 * `dev.ahmedvhashem.databaseliveinspector.agent.DatabaseLiveInspector`. Changing any of these
 * signatures breaks every shipped agent AAR.
 */
internal class AgentBridge private constructor(
    private val pollEvent: (Long) -> ByteArray?,
    private val takeDroppedCount: () -> Int,
    private val setEnabled: (Boolean) -> Unit,
) {

    fun pollEvent(timeoutMs: Long): ByteArray? = pollEvent.invoke(timeoutMs)
    fun takeDroppedCount(): Int = takeDroppedCount.invoke()
    fun setEnabled(enabled: Boolean) = setEnabled.invoke(enabled)

    companion object {
        private const val AGENT_FQN = "dev.ahmedvhashem.databaseliveinspector.agent.DatabaseLiveInspector"

        fun bind(): AgentBridge? = try {
            val agentClass = Class.forName(AGENT_FQN)
            val pollMethod = agentClass.getMethod("pollEvent", java.lang.Long.TYPE)
            val dropCountMethod = agentClass.getMethod("takeDroppedCount")
            val setEnabledMethod = agentClass.getMethod("setEnabled", java.lang.Boolean.TYPE)
            AgentBridge(
                pollEvent = { ms -> unwrap { pollMethod.invoke(null, ms) as ByteArray? } },
                takeDroppedCount = { unwrap { dropCountMethod.invoke(null) as Int } },
                setEnabled = { enabled -> unwrap { setEnabledMethod.invoke(null, enabled) } },
            )
        } catch (t: Throwable) {
            null
        }

        /**
         * Unwraps [java.lang.reflect.InvocationTargetException] so the agent's underlying
         * exception (notably [InterruptedException] during a blocked poll on dispose) reaches
         * the caller's catch clauses unchanged. Without this, drainLoop's
         * `catch (e: InterruptedException)` would miss interrupts and log uncaught-exception
         * stack traces at shutdown.
         */
        private inline fun <T> unwrap(block: () -> T): T = try {
            block()
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            if (cause is InterruptedException) Thread.currentThread().interrupt()
            throw cause ?: e
        }
    }
}
