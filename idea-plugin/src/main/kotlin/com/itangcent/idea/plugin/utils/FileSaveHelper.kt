package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.FileUtils
import com.itangcent.intellij.util.ToolUtils
import java.nio.charset.Charset

class FileSaveHelper {


    @Inject(optional = true)
    @Named("file.save.default")
    private val defaultExportedFile: String? = null

    @Inject(optional = true)
    @Named("file.save.last.location.key")
    private val lastImportedLocation: String? = null

    @Inject
    private val project: Project? = null

    fun saveOrCopy(info: String?,
                   onCopy: () -> Unit,
                   onSaveSuccess: () -> Unit,
                   onSaveFailed: () -> Unit) {

        if (info == null) return
        val actionContext = ActionContext.getContext()!!

        actionContext.runInSwingUI {
            val descriptor = FileChooserDescriptorFactory
                    .createSingleFileOrFolderDescriptor()
                    .withTitle("Export location")
                    .withDescription("Choose directory to export api to")
                    .withHideIgnored(false)
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            var toSelect: VirtualFile? = null
            val lastLocation = PropertiesComponent.getInstance().getValue(getLastImportedLocation())
            if (lastLocation != null) {
                toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
            }
            val files = chooser.choose(project, toSelect)
            if (files.isNotEmpty()) {
                actionContext.runInWriteUI {
                    val file = files[0]
                    PropertiesComponent.getInstance().setValue(getLastImportedLocation(), file.path)
                    if (file.isDirectory) {
                        try {
                            val defaultFile = getDefaultExportedFile()
                            FileUtils.forceSave("${file.path}/$defaultFile", info.toByteArray(Charset.defaultCharset()))
                            onSaveSuccess()
                        } catch (e: Exception) {
                            onSaveFailed()
                            actionContext.runAsync {
                                copyAndLog(info, onCopy)
                            }
                        }
                    } else {
                        try {
                            FileUtils.forceSave(file, info.toByteArray(Charset.defaultCharset()))
                            onSaveSuccess()
                        } catch (e: Exception) {
                            onSaveFailed()
                            actionContext.runAsync {
                                copyAndLog(info, onCopy)
                            }
                        }
                    }
                }
            } else {
                copyAndLog(info, onCopy)
            }
        }
    }

    private fun copyAndLog(info: String, onCopy: () -> Unit) {
        val logger: Logger = ActionContext.getContext()!!.instance(Logger::class)
        ToolUtils.copy2Clipboard(info)
        onCopy()
        if (info.length > 10000) {
            logger.info("Api data is too lager to show in console!")
        } else {
            logger.log(info)
        }
    }

    private fun getDefaultExportedFile(): String {
        return defaultExportedFile ?: "api.json"
    }

    private fun getLastImportedLocation(): String {
        return lastImportedLocation ?: "com.itangcent.api.export.path"
    }
}