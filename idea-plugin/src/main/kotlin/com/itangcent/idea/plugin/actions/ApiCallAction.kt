package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.dashboard.ApiDashboardService
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.util.ActionUtils

class ApiCallAction : ApiExportAction("Call Api") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))
        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)

        project?.let { p ->
            // Get the current class from the editor and navigate to it in the dashboard
            val currentClass = actionContext.callInReadUI { ActionUtils.findCurrentClass() }
            currentClass?.let { cls ->
                ApiDashboardService.getInstance(p).navigateToClass(cls)
            }
        }
    }
}

