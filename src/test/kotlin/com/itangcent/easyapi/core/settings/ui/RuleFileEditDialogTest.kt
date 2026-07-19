package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Tests for [RuleFileEditDialog]'s unsaved-change detection, which backs the
 * Cancel / close confirmation prompt ([RuleFileEditDialog.doCancelAction]).
 *
 * The dialog loads its content asynchronously on a background coroutine then
 * snapshots the name/content on the EDT. To avoid depending on `Dispatchers.Main`
 * scheduling (which plain `runBlocking` does not advance), these tests use
 * [RuleFileEditDialog.simulateLoadedForTest] to exercise the detection logic in
 * a fully-synchronised state, plus one case for the pre-load window.
 */
class RuleFileEditDialogTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun newLoadedDialog(
        name: String = "demo.rules",
        content: String = "api.name=demo\n"
    ): RuleFileEditDialog {
        val dialog = RuleFileEditDialog(project, "/tmp/$name")
        dialog.simulateLoadedForTest(name, content)
        return dialog
    }

    fun testNoChangesAfterLoad() {
        val dialog = newLoadedDialog()
        assertFalse("unmodified content should not be flagged", dialog.isContentModified())
        dialog.disposeForTest()
    }

    fun testContentEditDetected() {
        val dialog = newLoadedDialog()
        dialog.setContentForTest("api.name=changed\n")
        assertTrue("edited content should be flagged as modified", dialog.isContentModified())
        dialog.disposeForTest()
    }

    fun testNameEditDetected() {
        val dialog = newLoadedDialog()
        dialog.setNameForTest("renamed.rules")
        assertTrue("edited name should be flagged as modified", dialog.isContentModified())
        dialog.disposeForTest()
    }

    fun testWhitespaceOnlyNameChangeNotFlagged() {
        val dialog = newLoadedDialog(name = "demo.rules")
        // Trailing-space differences in the name are ignored by trim().
        dialog.setNameForTest("demo.rules  ")
        assertFalse(
            "whitespace-only name change should not be flagged",
            dialog.isContentModified()
        )
        dialog.disposeForTest()
    }

    fun testIdenticalContentNotFlagged() {
        val dialog = newLoadedDialog(content = "api.name=demo\n")
        // Re-setting the same content is a no-op for detection.
        dialog.setContentForTest("api.name=demo\n")
        assertFalse("identical content should not be flagged", dialog.isContentModified())
        dialog.disposeForTest()
    }

    fun testNotFlaggedBeforeLoadCompletes() {
        // A fresh dialog whose async load has NOT run yet. loadedContent is null,
        // so isContentModified() must report false — nothing to discard.
        val dialog = RuleFileEditDialog(project, "/tmp/demo.rules")
        assertFalse(
            "should not be flagged before the initial load completes",
            dialog.isContentModified()
        )
        dialog.disposeForTest()
    }
}
