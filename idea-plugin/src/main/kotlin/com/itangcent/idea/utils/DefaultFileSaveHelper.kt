package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.Singleton
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
import com.itangcent.utils.localPath
import java.awt.HeadlessException
import java.io.File
import java.nio.charset.Charset
import kotlin.text.Charsets

@Singleton
class DefaultFileSaveHelper : FileSaveHelper {

    @Inject(optional = true)
    @Named("file.save.default")
    private val defaultExportedFile: String? = null

    @Inject(optional = true)
    @Named("file.save.last.location.key")
    private val lastImportedLocation: String? = null

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var project: Project

    @Inject
    private lateinit var logger: Logger

    override fun saveOrCopy(
        content: String?,
        onCopy: () -> Unit,
        onSaveSuccess: (String) -> Unit,
        onSaveFailed: (String?) -> Unit,
    ) {
        saveOrCopy(content, Charsets.UTF_8, onCopy, onSaveSuccess, onSaveFailed)
    }

    override fun saveOrCopy(
        content: String?,
        charset: Charset,
        onCopy: () -> Unit,
        onSaveSuccess: (String) -> Unit,
        onSaveFailed: (String?) -> Unit,
    ) {
        if (content == null) return

        saveOrCopy(content, charset, {
            getDefaultExportedFile()
        }, onCopy, onSaveSuccess, onSaveFailed)
    }

    override fun saveOrCopy(
        content: String?,
        charset: Charset,
        defaultFileName: () -> String?,
        onCopy: () -> Unit,
        onSaveSuccess: (String) -> Unit,
        onSaveFailed: (String?) -> Unit,
    ) {
        if (content == null) return

        val descriptor = FileChooserDescriptorFactory
            .createSingleFileOrFolderDescriptor()
            .withTitle("Export Location")
            .withDescription("Choose directory to export api to")
            .withHideIgnored(false)

        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(getLastImportedLocation())
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }

        actionContext.runInAWT {
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val files = chooser.choose(project, toSelect)
            if (files.isNotEmpty()) {
                val file = files[0]
                PropertiesComponent.getInstance().setValue(getLastImportedLocation(), file.path)
                actionContext.runAsync {
                    if (file.isDirectory) {
                        try {
                            val defaultFile = defaultFileName() ?: getDefaultExportedFile()
                            var filePath = "${file.path}${File.separator}$defaultFile"
                            filePath = availablePath(filePath)
                            FileUtils.forceSave(filePath, content.toByteArray(charset))
                            onSaveSuccess(filePath.localPath())
                        } catch (e: Exception) {
                            onSaveFailed(e.message)
                            actionContext.runAsync {
                                copyAndLog(content, onCopy)
                            }
                        }
                    } else {
                        try {
                            FileUtils.forceSave(file, content.toByteArray(charset))
                            onSaveSuccess(file.path.localPath())
                        } catch (e: Exception) {
                            onSaveFailed(e.message)
                            actionContext.runAsync {
                                copyAndLog(content, onCopy)
                            }
                        }
                    }
                }
            } else {
                actionContext.runAsync {
                    copyAndLog(content, onCopy)
                }
            }
        }
    }

    override fun saveBytes(
        content: (String) -> ByteArray,
        defaultFileName: () -> String?,
        onSaveSuccess: () -> Unit,
        onSaveFailed: (String?) -> Unit,
        onSaveCancel: () -> Unit,
    ) {
        val descriptor = FileChooserDescriptorFactory
            .createSingleFileOrFolderDescriptor()
            .withTitle("Select Location")
            .withDescription("Choose folder/file to save")
            .withHideIgnored(false)

        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(getLastImportedLocation())
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }

        actionContext.runInAWT {
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val files = chooser.choose(project, toSelect)
            if (files.isNotEmpty()) {
                val file = files[0]
                PropertiesComponent.getInstance().setValue(getLastImportedLocation(), file.path)
                actionContext.runAsync {
                    if (file.isDirectory) {
                        try {
                            val defaultFile = defaultFileName() ?: "untitled"
                            var path = "${file.path}${File.separator}$defaultFile"
                            path = availablePath(path)

                            FileUtils.forceSave(path, content(path))
                            onSaveSuccess()
                        } catch (e: Exception) {
                            onSaveFailed(e.message)
                        }
                    } else {
                        try {
                            FileUtils.forceSave(file, content(file.path))
                            onSaveSuccess()
                        } catch (e: Exception) {
                            onSaveFailed(e.message)
                        }
                    }
                }
            } else {
                actionContext.runAsync {
                    onSaveCancel()
                }
            }
        }
    }

    private fun availablePath(path: String): String {
        try {
            if (FileUtil.exists(path)) {
                var index = 1
                var pathWithIndex: String
                while (true) {
                    pathWithIndex = pathWithIndex(path, index)
                    if (!FileUtil.exists(pathWithIndex)) {
                        break
                    }
                    ++index
                }
                pathWithIndex.let { return it }
            }
        } catch (_: Exception) {
        }
        return path
    }

    /**
     * xxx -> xxx-$index
     * xxx.txt -> xxx-$index.txt
     */
    private fun pathWithIndex(path: String, index: Int): String {
        val dot = path.indexOf('.')
        return if (dot == -1) {
            "$path-$index"
        } else {
            path.substring(0, dot) + "-$index" + path.substring(dot)
        }
    }

    private fun copyAndLog(info: String, onCopy: () -> Unit) {
        try {
            ToolUtils.copy2Clipboard(info)
        } catch (_: HeadlessException) {
        }
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