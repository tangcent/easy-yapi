package com.itangcent.idea.plugin.actions

import com.itangcent.common.exporter.ClassExporter
import com.itangcent.idea.plugin.api.cache.CachedClassExporter
import com.itangcent.idea.plugin.api.cache.DefaultFileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.FileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.api.export.CommonRules
import com.itangcent.idea.plugin.api.export.SpringClassExporter
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.SimpleRuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.intellij.psi.DuckTypeHelper
import com.itangcent.intellij.psi.PsiClassHelper

abstract class ApiExportAction(text: String) : BasicAnAction(text) {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(RuleParser::class) { it.with(SimpleRuleParser::class) }
        builder.bind(CommonRules::class) { it.singleton() }
        builder.bind(PsiClassHelper::class) { it.with(DefaultPsiClassHelper::class).singleton() }
        builder.bind(DuckTypeHelper::class) { it.singleton() }
        builder.bind(ClassExporter::class, "delegate_classExporter") { it.with(SpringClassExporter::class).singleton() }
        builder.bind(ClassExporter::class) { it.with(CachedClassExporter::class).singleton() }
        builder.bind(ModuleHelper::class) { it.singleton() }

        builder.bind(FileApiCacheRepository::class) { it.with(DefaultFileApiCacheRepository::class).singleton() }
        builder.bind(LocalFileRepository::class, "projectCacheRepository") {
            it.with(ProjectCacheRepository::class).singleton()
        }
    }
}