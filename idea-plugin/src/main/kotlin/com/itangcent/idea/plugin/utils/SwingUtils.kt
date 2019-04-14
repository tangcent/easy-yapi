package com.itangcent.idea.plugin.utils

import com.itangcent.intellij.context.ActionContext
import java.awt.Dialog

object SwingUtils {

    fun focus(apiCallDialog: Dialog) {
        ActionContext.getContext()!!.runInSwingUI {
            apiCallDialog.requestFocus()
        }
    }

}