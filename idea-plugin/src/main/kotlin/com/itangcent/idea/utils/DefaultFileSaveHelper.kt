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
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.FileUtils
import com.itangcent.intellij.util.ToolUtils
import java.io.File
import java.nio.charset.Charset
import kotlin.text.Charsets

class DefaultFileSaveHelper : FileSaveHelper {

    @Inject(optional = true)
    @Named("file.save.default")
    private val defaultExportedFile: String? = null

    @Inject(optional = true)
    @Named("file.save.last.location.key")
    private val lastImportedLocation: String? = null

    @Inject
    private val project: Project? = null

    @Inject
    private val actionContext: ActionContext? = null

    override fun saveOrCopy(content: String?,
                            onCopy: () -> Unit,
                            onSaveSuccess: (String) -> Unit,
                            onSaveFailed: () -> Unit) {
        saveOrCopy(content, Charsets.UTF_8, onCopy, onSaveSuccess, onSaveFailed)
    }

    override fun saveOrCopy(content: String?,
                            charset: Charset,
                            onCopy: () -> Unit,
                            onSaveSuccess: (String) -> Unit,
                            onSaveFailed: () -> Unit) {
        if (content == null) return

        saveOrCopy(content, charset, {
            getDefaultExportedFile()
        }, onCopy, onSaveSuccess, onSaveFailed)
    }

    override fun saveOrCopy(content: String?,
                            charset: Charset,
                            defaultFileName: () -> String?,
                            onCopy: () -> Unit,
                            onSaveSuccess: (String) -> Unit,
                            onSaveFailed: () -> Unit) {
        if (content == null) return

        actionContext!!.runInSwingUI {
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
                            val defaultFile = defaultFileName() ?: getDefaultExportedFile()
                            val filePath = "${file.path}${File.separator}$defaultFile"
                            FileUtils.forceSave(filePath, content.toByteArray(charset))
                            onSaveSuccess(filePath)
                        } catch (e: Exception) {
                            onSaveFailed()
                            actionContext.runAsync {
                                copyAndLog(content, onCopy)
                            }
                        }
                    } else {
                        try {
                            FileUtils.forceSave(file, content.toByteArray(charset))
                            onSaveSuccess(file.path)
                        } catch (e: Exception) {
                            onSaveFailed()
                            actionContext.runAsync {
                                copyAndLog(content, onCopy)
                            }
                        }
                    }
                }
            } else {
                copyAndLog(content, onCopy)
            }
        }
    }

    override fun saveBytes(content: (String) -> ByteArray,
                           defaultFileName: () -> String?,
                           onSaveSuccess: () -> Unit,
                           onSaveFailed: () -> Unit,
                           onSaveCancel: () -> Unit) {
        actionContext!!.runInSwingUI {
            val descriptor = FileChooserDescriptorFactory
                    .createSingleFileOrFolderDescriptor()
                    .withTitle("Select location")
                    .withDescription("Choose directory to save")
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
                            val defaultFile = defaultFileName() ?: "Untitled"
                            var path = "${file.path}${File.separator}$defaultFile"
                            try {
                                if (FileUtil.exists(path)) {
                                    var index = 1
                                    var pathWithIndex: String?
                                    while (true) {
                                        pathWithIndex = "$path-$index"
                                        if (!FileUtil.exists(pathWithIndex)) {
                                            break
                                        }
                                        ++index
                                    }
                                    pathWithIndex?.let { path = it }
                                }
                            } catch (e: Exception) {
                            }

                            FileUtils.forceSave(path, content(path))
                            onSaveSuccess()
                        } catch (e: Exception) {
                            onSaveFailed()
                        }
                    } else {
                        try {
                            FileUtils.forceSave(file, content(file.path))
                            onSaveSuccess()
                        } catch (e: Exception) {
                            onSaveFailed()
                        }
                    }
                }
            } else {
                onSaveCancel()
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
        return defaultExportedFile ?: "untitled"
    }

    private fun getLastImportedLocation(): String {
        return lastImportedLocation ?: "com.itangcent.api.export.path"
    }
}