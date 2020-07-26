package com.itangcent.idea.utils

import com.google.inject.Inject
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.itangcent.intellij.context.ActionContext
import java.io.File

class DefaultFileSelectHelper : FileSelectHelper {

    @Inject
    private val actionContext: ActionContext? = null

    override fun selectFile(onSelect: (File?) -> Unit) {
        IdeaFileChooserHelper(actionContext!!, FileChooserDescriptorFactory.createSingleFileDescriptor())
                .selectFile({
                    onSelect(File(it.path))
                }, {
                    onSelect(null)
                })
    }

    override fun selectFile(onSelect: (File) -> Unit, onCancel: () -> Unit) {
        IdeaFileChooserHelper(actionContext!!, FileChooserDescriptorFactory.createSingleFileDescriptor())
                .selectFile({
                    onSelect(File(it.path))
                }, onCancel)
    }
}