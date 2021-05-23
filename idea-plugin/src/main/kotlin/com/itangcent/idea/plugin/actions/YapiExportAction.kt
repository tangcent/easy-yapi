package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.generic.GenericMethodDocClassExporter
import com.itangcent.idea.plugin.api.export.generic.GenericRequestClassExporter
import com.itangcent.idea.plugin.api.export.yapi.*
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.settings.helper.YapiTokenChecker
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider

class YapiExportAction : ApiExportAction("Export Yapi") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }
        builder.bind(LinkResolver::class) { it.with(YapiLinkResolver::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(YapiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }
        builder.bind(YapiApiHelper::class) { it.with(YapiCachedApiHelper::class).singleton() }

        builder.bind(ClassExporter::class) { it.with(CompositeClassExporter::class).singleton() }
        builder.bindInstance(
            "AVAILABLE_CLASS_EXPORTER",
            arrayOf<Any>(
                YapiSpringRequestClassExporter::class,
                GenericRequestClassExporter::class,
                GenericMethodDocClassExporter::class
            )
        )

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

        builder.bindInstance("file.save.default", "yapi.json")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.yapi.export.path")

        builder.bind(PsiClassHelper::class) { it.with(YapiPsiClassHelper::class).singleton() }

        builder.bind(YapiTokenChecker::class) { it.with(YapiTokenCheckerSupport::class).singleton() }

        builder.bind(AdditionalParseHelper::class) { it.with(YapiAdditionalParseHelper::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        actionContext.instance(YapiApiExporter::class).export()
    }

}