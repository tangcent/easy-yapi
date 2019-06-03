package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.idea.plugin.api.export.EasyApiConfigReader
import com.itangcent.idea.plugin.api.export.IdeaParseHandle
import com.itangcent.idea.plugin.api.export.markdown.MarkdownApiExporter
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.psi.ClassRuleConfig
import com.itangcent.intellij.psi.DefaultClassRuleConfig

class MarkdownExportAction : ApiExportAction("Export Markdown") {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(ServiceManager.getService(SettingBinder::class.java)) }
        builder.bind(MarkdownApiExporter::class) { it.singleton() }
        builder.bind(ParseHandle::class) { it.with(IdeaParseHandle::class).singleton() }
        builder.bind(DocParseHelper::class) { it.with(DefaultDocParseHelper::class).singleton() }
        builder.bind(ClassRuleConfig::class) { it.with(DefaultClassRuleConfig::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

        builder.bindInstance("file.save.default", "easy-api.md")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.markdown.export.path")
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        actionContext.instance(MarkdownApiExporter::class).export()
    }

}