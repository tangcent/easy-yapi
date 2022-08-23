package com.itangcent.idea.swing

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoResult
import com.itangcent.idea.plugin.dialog.AskWithApplyAllDialog
import com.itangcent.idea.plugin.dialog.ChooseWithTipDialog
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.UIUtils
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@Singleton
class DefaultMessagesHelper : MessagesHelper {

    @Inject
    private lateinit var project: Project

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject(optional = true)
    private var activeWindowProvider: ActiveWindowProvider? = null

    /**
     * @return [.YES] if user pressed "Yes" or [.NO] if user pressed "No" button.
     */
    @YesNoResult
    override fun showYesNoDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String,
        icon: Icon?,
    ): Int {
        val activeWindow = activeWindowProvider?.activeWindow()
        return actionContext.callInSwingUI {
            if (activeWindow == null) {
                Messages.showYesNoDialog(project, message, title, icon)
            } else {
                Messages.showYesNoDialog(activeWindow, message, title, icon)
            }
        }!!
    }

    /**
     * @return trimmed input string or `null` if user cancelled dialog.
     */
    override fun showInputDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
    ): String? {
        val activeWindow = activeWindowProvider?.activeWindow()
        return actionContext.callInSwingUI {
            if (activeWindow == null) {
                Messages.showInputDialog(project, message, title, icon)
            } else {
                Messages.showInputDialog(activeWindow, message, title, icon)
            }
        }
    }

    override fun showEditableChooseDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
        values: Array<String>?,
        initialValue: String?,
    ): String? {
        return actionContext.callInSwingUI {
            Messages.showEditableChooseDialog(message, title, icon, values, initialValue ?: "", null)
        }
    }

    override fun <T> showEditableChooseDialog(
        message: String?,
        title: String?,
        icon: Icon?,
        values: Array<T>?,
        showAs: (T) -> String,
        initialValue: T?,
    ): T? {
        if (values.isNullOrEmpty()) {
            return null
        }
        var showValues = values.map(showAs)
        var initialShowValue: String? = null
        if (showValues.distinct().size == showValues.size) {
            initialShowValue = initialValue?.let { showAs(it) }
        } else {
            showValues = showValues.mapIndexed { index, showValue -> "${index + 1}: $showValue" }
            if (initialValue != null) {
                val index = values.indexOf(initialValue)
                if (index != -1) {
                    initialShowValue = "${index + 1}: ${showAs(initialValue)}"
                }
            }
        }
        val chooseValue =
            showEditableChooseDialog(message, title, icon, showValues.toTypedArray(), initialShowValue) ?: return null
        val chooseIndex = showValues.indexOf(chooseValue)
        if (chooseIndex == -1) {
            return null
        }
        return values[chooseIndex]
    }

    /**
     * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
     */
    override fun showInfoDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
    ) {
        val activeWindow = activeWindowProvider?.activeWindow()
        actionContext.runInSwingUI {
            if (activeWindow == null) {
                Messages.showInfoMessage(project, message, title ?: "")
            } else {
                Messages.showInfoMessage(activeWindow, message, title ?: "")
            }
        }
    }

    override fun <T> showChooseWithTipDialog(
        message: String?,
        items: List<T>?,
        showAs: ((T) -> String?)?,
        tipAs: ((T) -> String?)?,
        callBack: ((T?) -> Unit),
    ) {
        actionContext.runInSwingUI {
            val chooseWithTipDialog = ChooseWithTipDialog<T>(SwingUtils.preferableWindow())
            UIUtils.show(chooseWithTipDialog)
            chooseWithTipDialog.updateItems(message, items, showAs, tipAs, callBack)
        }
    }

    override fun showAskWithApplyAllDialog(
        message: String?,
        buttonNames: Array<String>?,
        callBack: (Int, Boolean) -> Unit,
    ) {
        actionContext.runInSwingUI {
            val chooseWithTipDialog = AskWithApplyAllDialog(SwingUtils.preferableWindow())
            buttonNames?.let { chooseWithTipDialog.updateButtons(buttonNames) }
            chooseWithTipDialog.updateMessage(message ?: "Yes or No?")
            UIUtils.show(chooseWithTipDialog)
            chooseWithTipDialog.setCallBack(callBack)
        }
    }
}