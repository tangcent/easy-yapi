package com.itangcent.easyapi.ide.support

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class NotificationUtilsTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testNotifyInfoDoesNotThrow() {
        NotificationUtils.notifyInfo(project, "Test Title", "Test Content")
    }

    fun testNotifyWarningDoesNotThrow() {
        NotificationUtils.notifyWarning(project, "Test Warning", "Warning Content")
    }

    fun testNotifyErrorDoesNotThrow() {
        NotificationUtils.notifyError(project, "Test Error", "Error Content")
    }

    fun testNotifyInfoWithLinksDoesNotThrow() {
        NotificationUtils.notifyInfoWithLinks(
            project,
            "Test Links",
            "Click <a href=\"https://example.com\">here</a>"
        )
    }
}
