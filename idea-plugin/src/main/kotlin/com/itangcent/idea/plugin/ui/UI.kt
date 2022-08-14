package com.itangcent.idea.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.itangcent.common.utils.cast
import com.itangcent.idea.swing.ActiveWindowProvider
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.UIUtils
import java.awt.Dialog
import java.awt.Window

interface UI {

    open fun focusUI() {
        (this as? Dialog)?.let { SwingUtils.focus(it) }
    }

    open fun showUI() {
        (this as? Dialog)?.let { UIUtils.show(it) }
    }
}