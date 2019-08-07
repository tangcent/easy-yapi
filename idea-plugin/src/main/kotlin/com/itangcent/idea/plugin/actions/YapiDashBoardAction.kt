package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.RequestHelper
import com.itangcent.idea.plugin.api.dashboard.ApiDashBoard
import com.itangcent.idea.plugin.api.dashboard.YapiDashBoard
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.plugin.api.export.DefaultRequestHelper
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.plugin.api.export.yapi.*
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.psi.ClassRuleConfig
import com.itangcent.intellij.psi.DefaultClassRuleConfig
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider

class YapiDashBoardAction : ApiExportAction("YapiDashBoard") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(RequestHelper::class) { it.with(DefaultRequestHelper::class).singleton() }
        builder.bind(DocParseHelper::class) { it.with(DefaultDocParseHelper::class).singleton() }
        builder.bind(ClassRuleConfig::class) { it.with(DefaultClassRuleConfig::class).singleton() }
        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(YapiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }
        builder.bind(ApiDashBoard::class) { it.singleton() }
        builder.bind(PostmanCachedApiHelper::class) { it.singleton() }
        builder.bind(PostmanFormatter::class) { it.singleton() }
        builder.bind(YapiApiDashBoardExporter::class) { it.singleton() }
        builder.bind(YapiApiHelper::class) { it.with(YapiCachedApiHelper::class).singleton() }
        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }

        builder.bind(ClassExporter::class, "delegate_classExporter") { it.with(YapiSpringClassExporter::class).singleton() }


    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        val apiDashBoard = actionContext.instance(YapiDashBoard::class)
        apiDashBoard.showDashBoardWindow()
    }
}

