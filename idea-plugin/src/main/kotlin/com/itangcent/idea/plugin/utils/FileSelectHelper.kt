package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.intellij.context.ActionContext

class FileSelectHelper {

    private val descriptor: FileChooserDescriptor

    constructor(descriptor: FileChooserDescriptor) {
        this.descriptor = descriptor
        ActionContext.getContext()!!.init(this)
    }

    constructor(actionContext: ActionContext, descriptor: FileChooserDescriptor) {
        this.descriptor = descriptor
        actionContext.init(this)
    }

    @Named("file.select.last.location.key")
    @Inject(optional = true)
    private var lastSelectedLocation: String? = null

    @Inject(optional = true)
    private var project: Project? = null

    @Inject
    private var actionContext: ActionContext? = null

    fun lastSelectedLocation(lastSelectedLocation: String?): FileSelectHelper {
        this.lastSelectedLocation = lastSelectedLocation
        return this
    }

    fun project(project: Project?): FileSelectHelper {
        this.project = project
        return this
    }

    fun actionContext(actionContext: ActionContext?): FileSelectHelper {
        this.actionContext = actionContext
        return this
    }

    fun withDesc(desc: (FileChooserDescriptor) -> Unit): FileSelectHelper {
        desc(descriptor)
        return this
    }

    private fun getLastImportedLocation(): String {
        return lastSelectedLocation ?: "com.itangcent.select.path"
    }

    fun selectFile(onSelect: (VirtualFile?) -> Unit) {
        selectFile({
            onSelect(it)
        }, {
            onSelect(null)
        })
    }

    fun selectFile(onSelect: (VirtualFile) -> Unit,
                   onCancel: () -> Unit) {
        actionContext!!.runInSwingUI {
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            var toSelect: VirtualFile? = null
            val lastLocation = PropertiesComponent.getInstance().getValue(getLastImportedLocation())
            if (lastLocation != null) {
                toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
            }
            val files = chooser.choose(project, toSelect)
            if (files.isNotEmpty()) {
                actionContext!!.runInWriteUI {
                    val file = files[0]
                    PropertiesComponent.getInstance().setValue(getLastImportedLocation(), file.path)
                    onSelect(file)
                }
            } else {
                onCancel()
            }
        }
    }
}