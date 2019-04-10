package com.itangcent.idea.plugin.actions

import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.idea.plugin.api.cache.CachedClassExporter
import com.itangcent.idea.plugin.api.cache.DefaultFileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.FileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.api.export.CommonRules
import com.itangcent.idea.plugin.api.export.IdeaParseHandle
import com.itangcent.idea.plugin.api.export.SpringClassExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.TmTypeHelper

abstract class ApiExportAction(text: String) : BasicAnAction(text) {

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(CommonRules::class) { it.singleton() }
        builder.bind(PsiClassHelper::class) { it.singleton() }
        builder.bind(TmTypeHelper::class) { it.singleton() }
        builder.bind(ParseHandle::class) { it.with(IdeaParseHandle::class).singleton() }
        builder.bind(ClassExporter::class, "delegate_classExporter") { it.with(SpringClassExporter::class).singleton() }
        builder.bind(ClassExporter::class) { it.with(CachedClassExporter::class).singleton() }

        builder.bind(FileApiCacheRepository::class) { it.with(DefaultFileApiCacheRepository::class).singleton() }
        builder.bind(LocalFileRepository::class, "projectCacheRepository") {
            it.with(ProjectCacheRepository::class).singleton()
        }
    }
}