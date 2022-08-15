package com.itangcent.mock

import com.intellij.openapi.ui.Messages
import com.itangcent.idea.swing.MessagesHelper
import javax.swing.Icon

class EmptyMessagesHelper : MessagesHelper {
    override fun showYesNoDialog(message: String?, title: String, icon: Icon?): Int {
        return Messages.YES
    }

    override fun showInputDialog(message: String?, title: String?, icon: Icon?): String? {
        return null
    }

    override fun showEditableChooseDialog(
        message: String?,
        title: String?,
        icon: Icon?,
        values: Array<String>?,
        initialValue: String?,
    ): String? {
        return null
    }

    override fun <T> showEditableChooseDialog(
        message: String?,
        title: String?,
        icon: Icon?,
        values: Array<T>?,
        showAs: (T) -> String,
        initialValue: T?,
    ): T? {
        return null
    }

    override fun showInfoDialog(message: String?, title: String?) {
    }

    override fun <T> showChooseWithTipDialog(
        message: String?,
        items: List<T>?,
        showAs: ((T) -> String?)?,
        tipAs: ((T) -> String?)?,
        callBack: ((T?) -> Unit)?,
    ) {
        callBack?.invoke(null)
    }
}