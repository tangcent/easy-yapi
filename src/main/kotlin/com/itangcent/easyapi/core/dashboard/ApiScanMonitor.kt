package com.itangcent.easyapi.core.dashboard

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.feature.publishFeatureStateChange
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.read
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Observes API scans to surface slowness and hangs to the user.
 *
 * This service sits between [ApiScanner] and its callers, recording:
 * - whole-scan duration and class count ([onScanStart] / [onScanEnd])
 * - per-class progress ([onClassStart] / [onClassEnd])
 * - a per-class watchdog that flags a class as `STUCK` when it exceeds
 *   [PER_CLASS_STUCK_TIMEOUT_MS] without returning.
 *
 * When a class is flagged stuck, a non-modal notification with actions lets the
 * user decide whether to keep waiting, abort the scan, or disable automatic
 * scanning. Groovy rule scripts are blocking JSR-223 calls that cannot be
 * interrupted from a coroutine, so a "skip this class" action is intentionally
 * not offered — the only safe escapes are aborting the scan or leaving it to
 * finish. This mirrors the decision documented in the scan-performance spec.
 *
 * Statistics are emitted to the EasyAPI console (visible when the log level is
 * lowered), and stuck/slow events mirror to `idea.log` via [NotificationUtils].
 */
@Service(Service.Level.PROJECT)
class ApiScanMonitor(private val project: Project) : IdeaLog {

    private val console get() = project.console

    private val scanStartMs = AtomicLong(0L)
    private val scannedClassCount = AtomicLong(0L)
    private val classIdCounter = AtomicLong(0L)

    /** In-flight classes: id -> (className, startMs). Emptied at scan end. */
    private val inFlight = ConcurrentHashMap<Long, Pair<String, Long>>()

    /** Slowest classes: className -> duration ms. */
    private val slowClasses = ConcurrentHashMap<String, Long>()

    /** Classes whose export hit the per-class timeout: className -> occurrences. */
    private val timedOutClasses = ConcurrentHashMap<String, Long>()

    /** Set once per scan to avoid re-notifying for every stuck class. */
    private val stuckNotified = AtomicBoolean(false)

    private val activeIndicator = AtomicReference<ProgressIndicator?>(null)

    /**
     * Guards the watchdog loop: true while the current scan is active, flipped
     * to false at scan end so the loop terminates on its next tick. Reuses a
     * single flag instead of a per-scan [Job], so repeated scans cannot leak
     * watchers even though [com.itangcent.easyapi.core.internal.threading.IdeDispatchers.backgroundAsync]
     * is fire-and-forget.
     */
    private val watchdogActive = AtomicBoolean(false)

    companion object {
        fun getInstance(project: Project): ApiScanMonitor = project.service()

        /** Per-class duration after which a class is flagged as stuck. */
        private const val PER_CLASS_STUCK_TIMEOUT_MS = 30_000L

        /** Classes slower than this are recorded into the slow-class ranking. */
        private const val SLOW_CLASS_THRESHOLD_MS = 5_000L

        private const val MAX_SLOW_CLASSES = 20

        private const val STUCK_POLL_INTERVAL_MS = 1_000L
    }

    /** Called when a full/incremental scan begins. */
    fun onScanStart(indicator: ProgressIndicator?) {
        scanStartMs.set(System.currentTimeMillis())
        scannedClassCount.set(0L)
        slowClasses.clear()
        timedOutClasses.clear()
        inFlight.clear()
        stuckNotified.set(false)
        activeIndicator.set(indicator)
    }

    /** Called when a scan finishes (successfully or not). */
    fun onScanEnd() {
        stopWatchdog()
        val elapsed = System.currentTimeMillis() - scanStartMs.get()
        activeIndicator.set(null)
        inFlight.clear()

        // The console defaults to logLevel=SILENT, so the summary also goes to
        // idea.log — otherwise scan statistics are invisible at default settings
        // and the whole monitor is dead weight when it is needed most.
        val summary = scanSummary(elapsed)
        LOG.info(summary)
        console.info(summary)
    }

    /**
     * Called immediately before exporting a single class.
     *
     * @return an opaque token to pass to [onClassEnd].
     */
    fun onClassStart(className: String): Long {
        val id = classIdCounter.incrementAndGet()
        inFlight[id] = className to System.currentTimeMillis()
        return id
    }

    /** Called immediately after a class export completes (or times out). */
    fun onClassEnd(token: Long) {
        val entry = inFlight.remove(token) ?: return
        val (className, startMs) = entry
        val elapsed = System.currentTimeMillis() - startMs
        scannedClassCount.incrementAndGet()
        if (elapsed >= SLOW_CLASS_THRESHOLD_MS) {
            slowClasses[className] = elapsed
        }
    }

    /**
     * Records a class whose export exceeded the per-class timeout.
     *
     * Timed-out classes are the actionable signal from a scan (usually a slow or
     * looping rule script), so they are aggregated into the scan summary instead
     * of only producing a standalone warning that is easy to miss.
     */
    fun onClassTimeout(className: String) {
        timedOutClasses.merge(className, 1L, Long::plus)
    }

    /**
     * Watches in-flight classes for a stuck condition and, once per scan,
     * prompts the user. Runs on a single long-lived background coroutine whose
     * loop is gated by [watchdogActive], so [onScanEnd] can stop it cleanly and
     * repeated scans never accumulate watchers.
     */
    fun watchForStuck() {
        if (watchdogActive.compareAndSet(false, true)) {
            backgroundAsync {
                while (currentCoroutineContext().isActive && watchdogActive.get()) {
                    val now = System.currentTimeMillis()
                    val stuck = inFlight.entries.firstOrNull { (_, entry) ->
                        now - entry.second >= PER_CLASS_STUCK_TIMEOUT_MS
                    }
                    if (stuck != null && stuckNotified.compareAndSet(false, true)) {
                        notifyStuck(stuck.value.first, now - stuck.value.second)
                    }
                    delay(STUCK_POLL_INTERVAL_MS)
                }
            }
        }
    }

    /** Stops the current scan's watchdog loop. */
    private fun stopWatchdog() {
        watchdogActive.set(false)
    }

    /** Aborts the active scan (cancels its progress indicator). */
    fun abortScan() {
        activeIndicator.get()?.cancel()
        console.warn("API scan aborted by user from the stuck-class notification")
    }

    /** Disables automatic scanning and stops the continuous session. */
    fun disableAutoScan() {
        try {
            val binder = SettingBinder.getInstance(project)
            val settings = binder.read<GeneralSettings>()
            val transaction = FeatureSettingsTransaction(
                FeatureRegistry.getInstance(project).snapshot(),
                settings
            )
            transaction.setDesiredState(CoreFeatureIds.AUTO_SCANNING, false)
            transaction.commit(settings)?.let { change ->
                // Persist before publishing: commit() only mutates the in-memory
                // settings object. Without save() the change never reaches the
                // PersistentStateComponent, so auto-scanning would come back after
                // an IDE restart and the Settings UI would still show it as enabled.
                // Order mirrors EasyApiSettingsConfigurable.apply(): commit -> save -> publish.
                binder.save(settings)
                project.publishFeatureStateChange(change)
            }
        } catch (e: Exception) {
            LOG.warn("Failed to disable automatic scanning from stuck-class notification", e)
        }
        console.warn("Automatic API scanning disabled by user from the stuck-class notification")
    }

    private fun notifyStuck(className: String, elapsedMs: Long) {
        val seconds = elapsedMs / 1000
        LOG.warn("API scan appears stuck on class '$className' for ${seconds}s")
        try {
            NotificationUtils.notifyInfoWithConfig(
                project = project,
                title = "API Scan Is Slow",
                content = "Scanning '$className' has been running for ${seconds}s. " +
                    "It may be a slow rule script or a large class."
            ) {
                addAction(object : NotificationAction("Keep Waiting") {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        // No-op: let the scan continue.
                    }
                })
                addAction(object : NotificationAction("Abort Scan") {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        abortScan()
                    }
                })
                addAction(object : NotificationAction("Disable Auto-Scan") {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        disableAutoScan()
                    }
                })
            }
        } catch (e: Exception) {
            LOG.warn("Failed to show stuck-class notification", e)
        }
    }

    /**
     * One-line scan summary: class count, duration, slowest and timed-out classes.
     *
     * `internal` so tests can assert timeouts and slow classes are aggregated
     * rather than only logged one-by-one.
     */
    internal fun scanSummary(elapsedMs: Long): String = buildString {
        append("API scan finished: ${scannedClassCount.get()} classes in ${elapsedMs}ms")
        if (slowClasses.isNotEmpty()) {
            append(". Slowest classes: ").append(
                slowClasses.entries
                    .sortedByDescending { it.value }
                    .take(MAX_SLOW_CLASSES)
                    .joinToString("; ") { "${it.key}=${it.value}ms" }
            )
        }
        if (timedOutClasses.isNotEmpty()) {
            append(". Timed out: ").append(
                timedOutClasses.entries
                    .sortedByDescending { it.value }
                    .joinToString("; ") { "${it.key}x${it.value}" }
            )
        }
    }
}
