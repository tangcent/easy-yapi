package com.itangcent.easyapi.ide.support

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

object NotificationUtils {

    fun notifyInfo(project: Project?, title: String, content: String) {
        Notifications.Bus.notify(
            Notification("EasyAPI Notifications", title, content, NotificationType.INFORMATION),
            project
        )
    }

    /**
     * Show an info notification with clickable HTML links.
     *
     * The [content] should contain `<a href="url">label</a>` tags.
     * Clicking a link opens it in the default browser.
     */
    fun notifyInfoWithLinks(project: Project?, title: String, content: String) {
        val notification = Notification(
            "EasyAPI Notifications",
            title,
            content,
            NotificationType.INFORMATION
        ).apply {
            isSuggestionType = true
        }
        Notifications.Bus.notify(notification, project)
    }

    fun notifyWarning(project: Project?, title: String, content: String) {
        Notifications.Bus.notify(
            Notification("EasyAPI Notifications", title, content, NotificationType.WARNING),
            project
        )
    }

    fun notifyError(project: Project?, title: String, content: String) {
        Notifications.Bus.notify(
            Notification("EasyAPI Notifications", title, content, NotificationType.ERROR),
            project
        )
    }
}
