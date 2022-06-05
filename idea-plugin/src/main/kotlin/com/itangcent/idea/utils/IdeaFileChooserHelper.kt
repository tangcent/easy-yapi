package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.intellij.context.ActionContext

class IdeaFileChooserHelper private constructor(
    private val actionContext: ActionContext,
    private val descriptor: FileChooserDescriptor,
) {

    @Named("file.select.last.location.key")
    @Inject(optional = true)
    private var lastSelectedLocation: String? = null

    @Inject
    private lateinit var project: Project

    fun lastSelectedLocation(lastSelectedLocation: String?): IdeaFileChooserHelper {
        this.lastSelectedLocation = lastSelectedLocation
        return this
    }

    fun withDesc(desc: (FileChooserDescriptor) -> Unit): IdeaFileChooserHelper {
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

    fun selectFile(
        onSelect: (VirtualFile) -> Unit,
        onCancel: () -> Unit,
    ) {
        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(getLastImportedLocation())
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }
        actionContext.runInSwingUI {
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val files = chooser.choose(project, toSelect)
            if (files.isNotEmpty()) {
                val file = files[0]
                PropertiesComponent.getInstance().setValue(getLastImportedLocation(), file.path)
                actionContext.runAsync {
                    onSelect(file)
                }
            } else {
                actionContext.runAsync {
                    onCancel()
                }
            }
        }
    }

    companion object {

        fun create(descriptor: FileChooserDescriptor): IdeaFileChooserHelper {
            return create(ActionContext.getContext()!!, descriptor)
        }

        fun create(actionContext: ActionContext, descriptor: FileChooserDescriptor): IdeaFileChooserHelper {
            return IdeaFileChooserHelper(actionContext, descriptor)
        }
    }

    init {
        actionContext.init(this)
    }
}