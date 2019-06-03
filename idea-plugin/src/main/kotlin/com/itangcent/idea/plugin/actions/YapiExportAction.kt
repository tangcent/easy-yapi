package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanApiExporter
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.plugin.api.export.yapi.*
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider

class YapiExportAction : ApiExportAction("Export Yapi") {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(ServiceManager.getService(SettingBinder::class.java)) }
        builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }
        builder.bind(PostmanApiExporter::class) { it.singleton() }
        builder.bind(PostmanFormatter::class) { it.singleton() }
        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }
        builder.bind(ParseHandle::class) { it.with(YapiIdeaParseHandle::class).singleton() }
        builder.bind(DocParseHelper::class) { it.with(DefaultDocParseHelper::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(YapiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }
        builder.bind(YapiApiHelper::class) { it.with(YapiCachedApiHelper::class).singleton() }

        builder.bindInstance("file.save.default", "yapi.json")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.yapi.export.path")
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        actionContext.instance(YapiApiExporter::class).export()
    }

}