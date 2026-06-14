package dev.ahmedvhashem.databaseliveinspector.agent.internal

/**
 * Frame-safety length caps. Replaces the redaction module's length-capping responsibilities —
 * these are *not* PII protection, they're guards that keep a single oversized arg/SQL from
 * blowing past App Inspection's transport frame budget.
 *
 * The result-cell + total-byte budgets for captured result rows live inside
 * `CursorResultSampler` since they're tied to its sampling loop; do not re-derive them here.
 */
internal object Limits {
    const val MAX_SQL_CHARS = 8192
    const val MAX_ARG_PREVIEW_CHARS = 256
    const val EVENT_QUEUE_CAPACITY = 1000
}
