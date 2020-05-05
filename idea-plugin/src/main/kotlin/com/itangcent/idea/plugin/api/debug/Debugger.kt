package com.itangcent.idea.plugin.api.debug

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.itangcent.idea.plugin.dialog.DebugDialog
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.UIUtils
import java.lang.ref.WeakReference

class Debugger {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val project: Project? = null

    fun showDebugWindow() {

        var debugDialog = project!!.getUserData(DEBUG_DIALOG)?.get()
        if (debugDialog != null) {
            SwingUtils.focus(debugDialog)
            return
        }

        debugDialog = actionContext!!.instance { DebugDialog() }
        UIUtils.show(debugDialog)
        project.putUserData(DEBUG_DIALOG, WeakReference(debugDialog))
        actionContext.on(EventKey.ON_COMPLETED) {
            project.putUserData(DEBUG_DIALOG, null)
        }
    }

    companion object {
        private val DEBUG_DIALOG = Key.create<WeakReference<DebugDialog>>("DEBUG_DIALOG")
    }

}