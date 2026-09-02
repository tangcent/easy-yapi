package com.itangcent.easyapi.core.rule.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import java.util.LinkedHashMap

/**
 * Aggregates rule-evaluation failures so a throwing rule surfaces **once
 * per run** instead of silently skipping endpoints.
 *
 * [RuleEngine] logs every failed evaluation to the console and records it
 * here **while a run window is open**. A throwing `custom.method.is.api`
 * rule would otherwise make each method evaluate to "not an API" with no
 * user-visible sign — the aggregation modes drop
 * [com.itangcent.easyapi.core.rule.RuleResult] failures, so the export is
 * silently empty.
 *
 * A run boundary (e.g. [com.itangcent.easyapi.core.export.ExportOrchestrator])
 * brackets the run with [beginRun] / [endRunAndNotify]: distinct failures are
 * collapsed to one warning balloon, occurrences are counted, and the buffer
 * is cleared. Failures recorded outside a run window (e.g. background
 * dashboard scans) are still logged per occurrence by the engine but never
 * buffered or ballooned, so a later export reports only its own failures.
 */
@Service(Service.Level.PROJECT)
class RuleFailureMonitor(private val project: Project) {

    /** One distinct failure, identified by rule key + message, with its occurrence count. */
    data class RuleFailure(val key: String, val message: String, var count: Int = 0)

    private val lock = Any()
    private val failures = LinkedHashMap<String, RuleFailure>()

    /** `true` while a run window is open (see [beginRun]). */
    @Volatile
    private var recording = false

    companion object {
        fun getInstance(project: Project): RuleFailureMonitor = project.service()
    }

    /**
     * Opens a run window and clears any leftover buffer. Rule failures
     * recorded from now on belong to this run.
     */
    fun beginRun() {
        synchronized(lock) {
            failures.clear()
            recording = true
        }
    }

    /**
     * Closes the run window, drains the recorded failures, and shows **one**
     * aggregated warning balloon when any were recorded; a no-op otherwise.
     *
     * @param scope the user-visible scope name, e.g. `"Export"`.
     */
    fun endRunAndNotify(scope: String) {
        val snapshot = synchronized(lock) {
            recording = false
            val copy = failures.values.toList()
            failures.clear()
            copy
        }
        if (snapshot.isEmpty()) return

        val total = snapshot.sumOf(RuleFailure::count)
        val first = snapshot.first()
        NotificationUtils.notifyWarning(
            project,
            scope,
            "$total rule evaluation failure(s) — e.g. ${first.key}: ${first.message}. " +
                "Affected rules were skipped; see the EasyApi log for details."
        )
    }

    /**
     * Records one failed evaluation of rule [key]. Thread-safe; failures with
     * the same key and root message are collapsed into one entry. Only
     * recorded while a run window is open.
     */
    fun record(key: String, error: Throwable) {
        if (!recording) return
        val message = error.cause?.message ?: error.message ?: error.javaClass.simpleName
        synchronized(lock) {
            failures.getOrPut("$key:$message") { RuleFailure(key, message) }.count++
        }
    }
}
