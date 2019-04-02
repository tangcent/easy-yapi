package com.itangcent.idea.plugin.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.idea.plugin.api.export.CommonRules
import com.itangcent.intellij.actions.KotlinAnAction
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.TmTypeHelper
import com.itangcent.intellij.util.FileUtils
import com.itangcent.intellij.util.ToolUtils
import java.nio.charset.Charset
import com.itangcent.intellij.extend.guice.singleton

abstract class ApiExportAction(text: String) : KotlinAnAction(text) {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(CommonRules::class) { it.singleton() }
        builder.bind(PsiClassHelper::class) { it.singleton() }
        builder.bind(TmTypeHelper::class) { it.singleton() }
    }

    protected fun saveOrCopy(project: Project?, info: String?,
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
            val lastLocation = PropertiesComponent.getInstance().getValue(lastImportedLocation())
            if (lastLocation != null) {
                toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
            }
            val files = chooser.choose(project, toSelect)
            if (files.isNotEmpty()) {
                actionContext.runInWriteUI {
                    val file = files[0]
                    PropertiesComponent.getInstance().setValue(lastImportedLocation(), file.path)
                    if (file.isDirectory) {
                        try {
                            val defaultFile = defaultExportedFile()
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

    open fun defaultExportedFile(): String {
        return "api.json"
    }

    open fun lastImportedLocation(): String {
        return "com.itangcent.api.export.path"
    }
}