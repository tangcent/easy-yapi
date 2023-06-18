package com.itangcent.idea.plugin.ui

import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.util.UIUtils
import java.awt.Dialog

interface UI {

    fun focusUI() {
        (this as? Dialog)?.let { SwingUtils.focus(it) }
    }

    fun showUI() {
        (this as? Dialog)?.let { UIUtils.show(it) }
    }
}