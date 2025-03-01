package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.ConfigurableMethodFilter
import com.itangcent.idea.plugin.api.export.core.MethodFilter
import com.itangcent.idea.plugin.api.export.markdown.MarkdownApiExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository

class MarkdownExportAction : ApiExportAction("Export Markdown") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("markdown"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))

        builder.bind(MethodFilter::class) { it.with(ConfigurableMethodFilter::class).singleton() }

        builder.bindInstance("file.save.default", "easy-api.md")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.markdown.export.path")
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        actionContext.instance(MarkdownApiExporter::class).export()
    }

}