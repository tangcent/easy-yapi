package com.itangcent.easyapi.util.file

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DefaultFileSelectHelper : FileSelectHelper {
    override suspend fun selectFile(title: String, project: Project): VirtualFile? {
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(title)
        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        return chooser.choose(project, null).firstOrNull()
    }

    override suspend fun selectDirectory(title: String, project: Project): VirtualFile? {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(title)
        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        return chooser.choose(project, null).firstOrNull()
    }
}
