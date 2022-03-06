package com.itangcent.idea.utils

import com.google.inject.Singleton
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import java.io.File

@Singleton
class DefaultFileSelectHelper : FileSelectHelper {

    override fun selectFile(onSelect: (File?) -> Unit) {
        IdeaFileChooserHelper.create(FileChooserDescriptorFactory.createSingleFileDescriptor())
            .selectFile({
                onSelect(File(it.path))
            }, {
                onSelect(null)
            })
    }

    override fun selectFile(onSelect: (File) -> Unit, onCancel: () -> Unit) {
        IdeaFileChooserHelper.create(FileChooserDescriptorFactory.createSingleFileDescriptor())
            .selectFile({
                onSelect(File(it.path))
            }, onCancel)
    }
}