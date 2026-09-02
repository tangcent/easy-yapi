package com.itangcent.easyapi.core.rule.engine

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Tests for [RuleFailureMonitor] — the per-run aggregation behind rule
 * failures: a throwing rule must surface once per export run, not skip
 * endpoints silently.
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass).
 */
class RuleFailureMonitorTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var monitor: RuleFailureMonitor
    private val notifications = mutableListOf<Notification>()

    override fun setUp() {
        super.setUp()
        monitor = RuleFailureMonitor.getInstance(project)
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notifications += notification
                }
            }
        )
    }

    fun testRunWithoutFailuresNotifiesNothing() {
        monitor.beginRun()
        monitor.endRunAndNotify("Export")
        assertTrue(notifications.isEmpty())
    }

    fun testFailureOutsideRunWindowIsNotBuffered() {
        // No beginRun — e.g. a background dashboard scan evaluating rules.
        monitor.record("api.name", RuntimeException("boom"))

        monitor.beginRun()
        monitor.endRunAndNotify("Export")
        assertTrue(notifications.isEmpty())
    }

    fun testEndRunClosesTheWindow() {
        monitor.beginRun()
        monitor.endRunAndNotify("Export")

        // Recorded after the run closed — must not leak into the next run.
        monitor.record("api.name", RuntimeException("boom"))

        monitor.beginRun()
        monitor.endRunAndNotify("Export")
        assertTrue(notifications.isEmpty())
    }

    fun testFailuresCollapseIntoOneNotificationWithCounts() {
        monitor.beginRun()
        monitor.record("api.name", RuntimeException("boom"))
        monitor.record("api.name", RuntimeException("boom"))
        // Root cause message is preferred over the wrapper's own message.
        monitor.record("ignore", IllegalStateException("wrapper", RuntimeException("inner")))
        monitor.endRunAndNotify("Export")

        assertEquals(1, notifications.size)
        val notification = notifications.single()
        assertEquals("Export", notification.title)
        assertTrue(
            "Expected total count in: ${notification.content}",
            notification.content.contains("3 rule evaluation failure(s)")
        )
        assertTrue(
            "Expected first failure as example in: ${notification.content}",
            notification.content.contains("api.name: boom")
        )
    }

    fun testSameFailureKeyWithDifferentMessagesStaysDistinct() {
        monitor.beginRun()
        monitor.record("api.name", RuntimeException("boom"))
        monitor.record("api.name", RuntimeException("other"))
        monitor.endRunAndNotify("Export")

        assertEquals(1, notifications.size)
        assertTrue(notifications.single().content.contains("2 rule evaluation failure(s)"))
    }

    fun testBufferIsClearedBetweenRuns() {
        monitor.beginRun()
        monitor.record("api.name", RuntimeException("boom"))
        monitor.endRunAndNotify("Export")

        monitor.beginRun()
        monitor.record("api.name", RuntimeException("boom"))
        monitor.endRunAndNotify("Export")

        // One balloon per run — the first run's buffer must not leak.
        assertEquals(2, notifications.size)
        notifications.forEach { assertTrue(it.content.contains("1 rule evaluation failure(s)")) }
    }
}
