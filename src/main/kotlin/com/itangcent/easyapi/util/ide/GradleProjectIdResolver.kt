package com.itangcent.easyapi.util.ide

import com.intellij.psi.PsiClass

/**
 * Resolves Maven coordinates using the IntelliJ Gradle plugin integration.
 *
 * Uses reflection to access [ExternalProjectDataCache] and [ExternalProject] classes
 * from the optional Gradle plugin, avoiding a hard compile-time dependency.
 */
class GradleProjectIdResolver : ProjectIdResolver {

    override val available: Boolean by lazy {
        runCatching {
            Class.forName(EXTERNAL_PROJECT_DATA_CACHE_CLASS)
            true
        }.getOrDefault(false) as Boolean
    }

    override fun resolve(psiClass: PsiClass): MavenIdData? {
        val project = psiClass.project
        val projectPath = project.basePath ?: return null

        val cacheClass = Class.forName(EXTERNAL_PROJECT_DATA_CACHE_CLASS)
        val cache = cacheClass.getMethod("getInstance", com.intellij.openapi.project.Project::class.java)
            .invoke(null, project)

        val externalProject = cacheClass.getMethod("getRootExternalProject", String::class.java)
            .invoke(cache, projectPath) ?: return null

        val externalProjectClass = Class.forName(EXTERNAL_PROJECT_CLASS)
        val group = externalProjectClass.getMethod("getGroup").invoke(externalProject) as? String ?: return null
        val name = externalProjectClass.getMethod("getName").invoke(externalProject) as? String ?: return null
        val version = externalProjectClass.getMethod("getVersion").invoke(externalProject) as? String ?: return null

        return MavenIdData(groupId = group, artifactId = name, version = version)
    }

    companion object {
        private const val EXTERNAL_PROJECT_DATA_CACHE_CLASS =
            "org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache"
        private const val EXTERNAL_PROJECT_CLASS =
            "org.jetbrains.plugins.gradle.model.ExternalProject"
    }
}
