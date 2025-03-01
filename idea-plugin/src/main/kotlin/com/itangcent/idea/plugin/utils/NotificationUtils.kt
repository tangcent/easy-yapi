package com.itangcent.idea.plugin.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent

object NotificationUtils {
    private const val GROUP_ID = "EasyApi Notifications"

    fun notifyInfo(project: Project?, message: String) {
        notify(project, message, NotificationType.INFORMATION)
    }

    fun notifyInfo(project: Project?, message: String, action: () -> Unit) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, NotificationType.INFORMATION)
        notification.addAction(object : NotificationAction("Open in Browser") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                action()
                notification.expire()
            }
        })
        notification.notify(project)
    }

    fun notifyWarning(project: Project?, message: String) {
        notify(project, message, NotificationType.WARNING)
    }

    fun notifyError(project: Project?, message: String) {
        notify(project, message, NotificationType.ERROR)
    }

    private fun notify(project: Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, type)
            .notify(project)
    }
} 