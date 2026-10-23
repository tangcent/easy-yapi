package com.itangcent.easyapi.testFramework

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog

/**
 * Runs [block] with every message and input dialog recorded instead of shown: a message dialog is
 * answered OK, an input dialog with nothing (`null`). The previous test dialogs are put back afterwards.
 *
 * Inlined, so [block] may call `suspend` functions when the caller is itself suspending; a non-suspending
 * caller wraps them in `runBlocking` inside [block].
 *
 * @return what [block] returned, and the messages of the dialogs it showed, in order
 */
inline fun <T> recordDialogs(block: () -> T): Pair<T, List<String>> {
    val shown = mutableListOf<String>()
    val previousDialog = TestDialogManager.setTestDialog(TestDialog { message -> shown += message; Messages.OK })
    val previousInput = TestDialogManager.setTestInputDialog(TestInputDialog { message -> shown += message; null })
    try {
        return block() to shown
    } finally {
        TestDialogManager.setTestDialog(previousDialog)
        TestDialogManager.setTestInputDialog(previousInput)
    }
}
