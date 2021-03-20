package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.AbstractLocalFileRepository
import java.io.File

@Singleton
class ProjectCacheRepository : AbstractLocalFileRepository() {

    @Inject
    private val project: Project? = null

    @Inject
    private val actionContext: ActionContext? = null

    override fun basePath(): String {
        val basePath = actionContext!!.cacheOrCompute("project_path") { project!!.basePath }
        return basePath!! + "${File.separator}.idea${File.separator}.cache"
    }
}