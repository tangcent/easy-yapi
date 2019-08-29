package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.RequestHelper
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.call.ApiCaller
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.DefaultLinkResolver
import com.itangcent.idea.plugin.api.export.EasyApiConfigReader
import com.itangcent.idea.plugin.api.export.SpringRequestClassExporter
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository

class ApiCallAction : ApiExportAction("Call Api") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        //allow cache api
        builder.bind(ClassExporter::class, "delegate_classExporter") { it.with(SpringRequestClassExporter::class).singleton() }
        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

        builder.bind(ApiCaller::class) { it.singleton() }

    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        val apiCaller = actionContext.instance(ApiCaller::class)
        apiCaller.showCallWindow()
    }
}

