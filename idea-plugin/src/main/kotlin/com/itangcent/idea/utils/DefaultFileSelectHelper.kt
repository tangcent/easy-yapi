package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.itangcent.intellij.context.ActionContext
import java.io.File

@Singleton
class DefaultFileSelectHelper : FileSelectHelper {

    @Named("file.select.last.location.key")
    @Inject(optional = true)
    private var lastSelectedLocation: String? = null

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var project: Project

    private fun getLastImportedLocation(): String {
        return lastSelectedLocation ?: "com.itangcent.select.path"
    }

    override fun selectFile(onSelect: (File?) -> Unit) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
        
        var toSelect: com.intellij.openapi.vfs.VirtualFile? = null
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
                    onSelect(File(file.path))
                }
            } else {
                actionContext.runAsync {
                    onSelect(null)
                }
            }
        }
    }

    override fun selectFile(onSelect: (File) -> Unit, onCancel: () -> Unit) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
        
        var toSelect: com.intellij.openapi.vfs.VirtualFile? = null
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
                    onSelect(File(file.path))
                }
            } else {
                actionContext.runAsync {
                    onCancel()
                }
            }
        }
    }
}