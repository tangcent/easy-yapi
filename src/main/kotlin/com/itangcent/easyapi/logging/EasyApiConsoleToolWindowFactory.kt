package com.itangcent.easyapi.logging

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class EasyApiConsoleToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val consoleProvider = IdeaConsoleProvider.getInstance(project)
        val console = consoleProvider.getConsole()
        if (console is DefaultIdeaConsole) {
            console.bindToolWindow(toolWindow)
        }
    }
}
