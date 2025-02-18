package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.postman.PostmanApiExporter
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatFolderHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository

class PostmanExportAction : ApiExportAction("Export Postman") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(FormatFolderHelper::class) { it.with(PostmanFormatFolderHelper::class).singleton() }

        builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }

        builder.bindInstance(ExportChannel::class, ExportChannel.of("postman"))
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))

        builder.bind(RequestBuilderListener::class) { it.with(CompositeRequestBuilderListener::class).singleton() }

        builder.bind(MethodFilter::class) { it.with(ConfigurableMethodFilter::class).singleton() }

        builder.bindInstance("file.save.default", "postman.json")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.postman.export.path")
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        actionContext.instance(PostmanApiExporter::class).export()
    }

}