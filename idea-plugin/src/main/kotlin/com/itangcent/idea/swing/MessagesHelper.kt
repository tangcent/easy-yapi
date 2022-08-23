package com.itangcent.idea.swing

import com.google.inject.ImplementedBy
import com.intellij.openapi.ui.Messages
import com.itangcent.common.concurrent.ValueHolder
import com.itangcent.intellij.context.ActionContext
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ImplementedBy(DefaultMessagesHelper::class)
interface MessagesHelper {

    /**
     * @return [.YES] if user pressed "Yes" or [.NO] if user pressed "No" button.
     */
    @Messages.YesNoResult
    fun showYesNoDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String,
        icon: Icon?,
    ): Int

    /**
     * @return trimmed input string or `null` if user cancelled dialog.
     */
    fun showInputDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
    ): String?

    fun showEditableChooseDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
        values: Array<String>?,
        initialValue: String? = null,
    ): String?

    fun <T> showEditableChooseDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
        values: Array<T>?,
        showAs: (T) -> String,
        initialValue: T? = null,
    ): T?


    /**
     * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
     */
    fun showInfoDialog(message: String?, @Nls(capitalization = Nls.Capitalization.Title) title: String?)

    fun <T> showChooseWithTipDialog(
        message: String?,
        items: List<T>?,
        showAs: ((T) -> String?)?,
        tipAs: ((T) -> String?)?,
        callBack: ((T?) -> Unit),
    )

    /**
     * @param message tip at the top
     * @param buttonNames [YES,NO,CANCEL]
     * @param callBack callback when button be clicked
     */
    fun showAskWithApplyAllDialog(
        message: String?,
        buttonNames: Array<String>?,
        callBack: (Int, Boolean) -> Unit,
    )
}

fun <T> MessagesHelper.showChooseWithTipDialog(
    message: String?,
    items: List<T>?,
    showAs: ((T) -> String?)?,
    tipAs: ((T) -> String?)?,
): T? {
    val valueHolder = ValueHolder<T>()
    this.showChooseWithTipDialog(message, items, showAs, tipAs) {
        valueHolder.success(it)
    }

    return valueHolder.value()
}

fun MessagesHelper.showAskWithApplyAllDialog(
    message: String?,
    buttonNames: Array<String>?,
    key: String,
    callBack: (Int) -> Unit,
) {
    val actionContext = ActionContext.getContext()
    actionContext?.getCache<Int>(key)
        ?.let {
            callBack(it)
            return
        }

    this.showAskWithApplyAllDialog(message, buttonNames) { ret, applyAll ->
        if (applyAll) {
            actionContext?.cache(key, ret)
        }
        callBack(ret)
    }
}