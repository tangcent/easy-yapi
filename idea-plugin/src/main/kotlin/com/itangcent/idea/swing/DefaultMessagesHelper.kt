package com.itangcent.idea.swing

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoResult
import com.itangcent.intellij.context.ActionContext
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
        icon: Icon?
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
        icon: Icon?
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
        initialValue: String?
    ): String? {
        return actionContext.callInSwingUI {
            Messages.showEditableChooseDialog(message, title, icon, values, initialValue ?: "", null)
        }
    }

    /**
     * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
     */
    override fun showInfoDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?
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

}