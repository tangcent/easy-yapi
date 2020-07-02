package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.dialog.DebugDialog
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.FileUtils
import com.itangcent.intellij.util.ToolUtils
import java.io.File
import java.nio.charset.Charset
import kotlin.text.Charsets

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