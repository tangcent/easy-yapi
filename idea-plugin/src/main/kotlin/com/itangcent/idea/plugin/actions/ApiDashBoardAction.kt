package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.dashboard.ApiDashBoard
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanConfigReader
import com.itangcent.idea.plugin.api.export.postman.PostmanRequestBuilderListener
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.swing.ActiveWindowProvider
import com.itangcent.idea.swing.SimpleActiveWindowProvider
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider

class ApiDashBoardAction : ApiExportAction("ApiDashBoard") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        //allow cache api
        builder.bind(ClassExporter::class, "delegate_classExporter") {
            it.with(CompositeClassExporter::class).singleton()
        }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("postman"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))
        
        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(PostmanConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }
        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }

        builder.bind(RequestBuilderListener::class) { it.with(CompositeRequestBuilderListener::class).singleton() }
        builder.bind(ActiveWindowProvider::class) { it.with(SimpleActiveWindowProvider::class) }
        builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }

    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        val apiDashBoard = actionContext.instance(ApiDashBoard::class)
        apiDashBoard.showDashBoardWindow()
    }
}

