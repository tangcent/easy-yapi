package com.itangcent.easyapi.core.ide.support

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Centralized notification utility for posting balloon notifications.
 *
 * ## Mirror to idea.log
 *
 * `notifyWarning` and `notifyError` are mirrored to `idea.log` via [IdeaLog.LOG] so the
 * durable triage record is created automatically — callers do **not** need to also write
 * a `LOG.*` call. `notifyInfo`/`notifyInfoWithLinks` are not mirrored (success balloons
 * are not triage material).
 *
 * ## Group id
 *
 * All notifications use the group id `"EasyApi Notifications"` (matches the id registered
 * in `plugin.xml`). Direct `Notifications.Bus.notify` / `NotificationGroupManager` calls
 * elsewhere in production code MUST be routed through this object.
 */
object NotificationUtils : IdeaLog {

    private const val GROUP_ID = "EasyApi Notifications"

    fun notifyInfo(project: Project?, title: String, content: String) {
        // no mirror — success balloons aren't triage material
        notify(Notification(GROUP_ID, title, content, NotificationType.INFORMATION), project)
    }

    /**
     * Show an info notification with clickable HTML links.
     *
     * The [content] should contain `<a href="url">label</a>` tags.
     * Clicking a link opens it in the default browser.
     */
    fun notifyInfoWithLinks(project: Project?, title: String, content: String) {
        // no mirror — success balloons aren't triage material
        val notification = Notification(
            GROUP_ID,
            title,
            content,
            NotificationType.INFORMATION
        ).apply {
            isSuggestionType = true
        }
        notify(notification, project)
    }

    /**
     * Show an info notification with caller-supplied configuration (e.g. action buttons).
     *
     * Use this when the balloon needs action buttons that can't be expressed as HTML links.
     * The [configure] lambda runs on the [Notification] before it is published.
     */
    fun notifyInfoWithConfig(
        project: Project?,
        title: String,
        content: String,
        configure: Notification.() -> Unit
    ) {
        // no mirror — success balloons aren't triage material
        val notification = Notification(GROUP_ID, title, content, NotificationType.INFORMATION)
        notification.configure()
        notify(notification, project)
    }

    fun notifyWarning(project: Project?, title: String, content: String, t: Throwable? = null) {
        // Mirror to idea.log. Throwable is passed as the last arg so the stacktrace survives.
        if (t != null) {
            LOG.warn("$title: $content", t)
        } else {
            LOG.warn("$title: $content")
        }
        notify(Notification(GROUP_ID, title, content, NotificationType.WARNING), project)
    }

    fun notifyError(project: Project?, title: String, content: String, t: Throwable? = null) {
        // Mirror to idea.log. Use warn level: LOG.error is prohibited (triggers intrusive popup);
        // warn preserves severity without popup.
        if (t != null) {
            LOG.warn("$title: $content", t)
        } else {
            LOG.warn("$title: $content")
        }
        notify(Notification(GROUP_ID, title, content, NotificationType.ERROR), project)
    }

    private fun notify(notification: Notification, project: Project?) {
        Notifications.Bus.notify(notification, project)
    }
}
