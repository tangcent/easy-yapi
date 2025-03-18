package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.yapi.*
import com.itangcent.idea.plugin.settings.helper.YapiTokenChecker
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.PsiClassHelper

class YapiExportAction : ApiExportAction("Export Yapi") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(LinkResolver::class) { it.with(YapiLinkResolver::class).singleton() }

        builder.bind(YapiApiHelper::class) { it.with(CachedYapiApiHelper::class).singleton() }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("yapi"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))

        builder.bind(MethodFilter::class) { it.with(ConfigurableMethodFilter::class).singleton() }

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