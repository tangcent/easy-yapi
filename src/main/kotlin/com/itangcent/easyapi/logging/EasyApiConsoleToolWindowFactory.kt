package com.itangcent.easyapi.logging

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class EasyApiConsoleToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val console = project.console
        if (console is DefaultIdeaConsole) {
            console.bindToolWindow(toolWindow)
        }
    }
}
