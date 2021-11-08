package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CompositeClassExporter
import com.itangcent.idea.plugin.api.export.core.EasyApiConfigReader
import com.itangcent.idea.plugin.api.export.generic.GenericMethodDocClassExporter
import com.itangcent.idea.plugin.api.export.generic.GenericRequestClassExporter
import com.itangcent.idea.plugin.api.export.markdown.MarkdownApiExporter
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository

class MarkdownExportAction : ApiExportAction("Export Markdown") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

        builder.bind(ClassExporter::class) { it.with(CompositeClassExporter::class).singleton() }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("markdown"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))
        
        //always not read api from cache
        builder.bindInstance("class.exporter.read.cache", false)

        builder.bindInstance("file.save.default", "easy-api.md")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.markdown.export.path")
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        actionContext.instance(MarkdownApiExporter::class).export()
    }

}