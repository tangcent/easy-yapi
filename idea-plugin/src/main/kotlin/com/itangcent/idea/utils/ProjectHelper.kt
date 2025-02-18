package com.itangcent.idea.utils

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.itangcent.idea.psi.PsiClassFinder
import java.awt.Component
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

object ProjectHelper {

    fun getCurrentProject(generalPanel: Component?): Project? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.size == 1) {
            return projects[0]
        }

        val wm = WindowManager.getInstance()
        if (generalPanel?.parent != null) {
            for (project in projects) {
                if (SwingUtilities.isDescendingFrom(
                        generalPanel,
                        wm.suggestParentWindow(project)
                    )
                ) {
                    return project
                }
            }
        }

        for (project in projects) {
            val window = wm.suggestParentWindow(project)
            if (window != null && window.isActive) {
                return project
            }
        }

        try {
            val dataContext = DataManager.getInstance()?.getDataContext(generalPanel)
            val project = dataContext?.getData(CommonDataKeys.PROJECT)
            if (project != null) {
                return project
            }
        } catch (e: Exception) {
            ///ignore
        }

        return null
    }
}

fun Project.waiteUtilIndexReady() {
    while (!this.isInitialized) {
        Thread.sleep(500)
    }
    var indexReady = false
    val maxWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
    while (System.currentTimeMillis() < maxWait && !indexReady) {
        ApplicationManager.getApplication().runReadAction {
            try {
                val cls = PsiClassFinder.findClass("java.lang.Object", this)
                if (cls != null) {
                    indexReady = true
                }
            } catch (e: IndexNotReadyException) {
                //ignore
            }
        }
        Thread.sleep(500)
    }
}