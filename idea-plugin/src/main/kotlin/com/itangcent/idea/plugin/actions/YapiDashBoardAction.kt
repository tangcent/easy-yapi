package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.dashboard.YapiDashBoard
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.yapi.*
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

class YapiDashBoardAction : ApiExportAction("YapiDashBoard") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(LinkResolver::class) { it.with(YapiLinkResolver::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(YapiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }
        builder.bind(YapiDashBoard::class) { it.singleton() }

        builder.bind(YapiApiDashBoardExporter::class) { it.singleton() }
        builder.bind(YapiApiHelper::class) { it.with(YapiCachedApiHelper::class).singleton() }
        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }

        //allow cache api
        builder.bind(ClassExporter::class, "delegate_classExporter") {
            it.with(CompositeClassExporter::class).singleton()
        }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("yapi"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))
        
        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }

        builder.bind(RequestBuilderListener::class) { it.with(ComponentRequestBuilderListener::class).singleton() }
        builder.bindInstance(
            "AVAILABLE_REQUEST_BUILDER_LISTENER",
            arrayOf<Any>(DefaultRequestBuilderListener::class, YapiRequestBuilderListener::class)
        )

        builder.bind(MethodDocBuilderListener::class) { it.with(ComponentMethodDocBuilderListener::class).singleton() }
        builder.bindInstance(
            "AVAILABLE_METHOD_DOC_BUILDER_LISTENER",
            arrayOf<Any>(DefaultMethodDocBuilderListener::class, YapiMethodDocBuilderListener::class)
        )

        builder.bind(ActiveWindowProvider::class) { it.with(SimpleActiveWindowProvider::class) }

        builder.bind(AdditionalParseHelper::class) { it.with(YapiAdditionalParseHelper::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        val apiDashBoard = actionContext.instance(YapiDashBoard::class)
        apiDashBoard.showDashBoardWindow()
    }
}

