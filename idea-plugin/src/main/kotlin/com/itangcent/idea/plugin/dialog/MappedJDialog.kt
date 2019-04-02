package com.itangcent.idea.plugin.dialog

import java.util.function.Function
import javax.swing.JDialog

abstract class MappedJDialog : JDialog() {

    protected var resultHandle: Function<Map<String, String>?, Boolean>? = null

    protected fun onOK() {
        if (this.resultHandle != null) {
            if (!this.resultHandle!!.apply(collectInfo())) {
                return
            }
        }

        // add your code here
        dispose()
    }

    protected fun onCancel() {

        if (this.resultHandle != null) {
            this.resultHandle!!.apply(null)
        }

        // add your code here if necessary
        dispose()
    }

    open fun onResult(resultHandle: Function<Map<String, String>?, Boolean>) {
        this.resultHandle = resultHandle
    }

    internal abstract fun collectInfo(): Map<String, String>

}
