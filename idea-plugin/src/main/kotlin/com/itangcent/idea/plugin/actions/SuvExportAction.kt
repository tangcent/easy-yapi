package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.condition.markAsSimple
import com.itangcent.idea.plugin.api.export.core.ConfigurableMethodFilter
import com.itangcent.idea.plugin.api.export.core.MethodFilter
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.suv.SuvApiExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.psi.DataContextProvider

class SuvExportAction : ApiExportAction("Export Api") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {

        super.afterBuildActionContext(event, builder)

        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))
        builder.markAsSimple()

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(SuvApiExporter::class) { it.singleton() }

        builder.bind(MethodFilter::class) { it.with(ConfigurableMethodFilter::class).singleton() }

        builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }
    }

    private fun DataContextProvider.preLoadData() {
        getData(CommonDataKeys.PROJECT)
        getData(CommonDataKeys.PSI_FILE)
        getData(CommonDataKeys.NAVIGATABLE_ARRAY)
        getData(CommonDataKeys.NAVIGATABLE)
        getData(CommonDataKeys.EDITOR)
        getData(CommonDataKeys.PSI_ELEMENT)
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        actionContext.instance(DataContextProvider::class).preLoadData()
        val multipleApiExporter = actionContext.instance(SuvApiExporter::class)
        multipleApiExporter.showExportWindow()
    }
}

