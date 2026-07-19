package com.itangcent.easyapi.core.ide.support

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

    fun testNotifyErrorWithThrowableDoesNotThrow() {
        val error = RuntimeException("boom")
        NotificationUtils.notifyError(project, "Test Error", "Error Content", error)
    }

    fun testNotifyErrorWithNullThrowableDoesNotThrow() {
        NotificationUtils.notifyError(project, "Test Error", "Error Content", null)
    }

    fun testNotifyInfoWithLinksDoesNotThrow() {
        NotificationUtils.notifyInfoWithLinks(
            project,
            "Test Links",
            "Click <a href=\"https://example.com\">here</a>"
        )
    }
}
