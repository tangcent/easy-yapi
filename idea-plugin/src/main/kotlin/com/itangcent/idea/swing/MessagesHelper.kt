package com.itangcent.idea.swing

import com.google.inject.ImplementedBy
import com.intellij.openapi.ui.Messages
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
        icon: Icon?
    ): Int

    /**
     * @return trimmed input string or `null` if user cancelled dialog.
     */
    fun showInputDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?
    ): String?

    fun showEditableChooseDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
        values: Array<String>?,
        initialValue: String? = null
    ): String?

    fun <T> showEditableChooseDialog(
        message: String?,
        @Nls(capitalization = Nls.Capitalization.Title) title: String?,
        icon: Icon?,
        values: Array<T>?,
        showAs: (T) -> String,
        initialValue: T? = null
    ): T?


    /**
     * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
     */
    fun showInfoDialog(message: String?, @Nls(capitalization = Nls.Capitalization.Title) title: String?)
}