package com.itangcent.easyapi.util.ide

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass

/**
 * Resolves Maven coordinates using the IntelliJ Maven plugin integration.
 *
 * Uses reflection to access [MavenProjectsManager] and [MavenProject] classes
 * from the optional Maven plugin, avoiding a hard compile-time dependency.
 */
class MavenProjectIdResolver : ProjectIdResolver {

    override val available: Boolean by lazy {
        runCatching {
            Class.forName(MAVEN_PROJECTS_MANAGER_CLASS)
            true
        }.getOrDefault(false) as Boolean
    }

    override fun resolve(psiClass: PsiClass): MavenIdData? {
        val project = psiClass.project
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass) ?: return null

        val managerClass = Class.forName(MAVEN_PROJECTS_MANAGER_CLASS)
        val manager = managerClass.getMethod("getInstance", com.intellij.openapi.project.Project::class.java)
            .invoke(null, project)

        val mavenProject = managerClass.getMethod("findProject", com.intellij.openapi.module.Module::class.java)
            .invoke(manager, module) ?: return null

        val mavenProjectClass = Class.forName(MAVEN_PROJECT_CLASS)
        val mavenId = mavenProjectClass.getMethod("getMavenId")
            .invoke(mavenProject) ?: return null

        val mavenIdClass = Class.forName(MAVEN_ID_CLASS)
        val groupId = mavenIdClass.getMethod("getGroupId").invoke(mavenId) as? String ?: return null
        val artifactId = mavenIdClass.getMethod("getArtifactId").invoke(mavenId) as? String ?: return null
        val version = mavenIdClass.getMethod("getVersion").invoke(mavenId) as? String ?: return null

        return MavenIdData(groupId = groupId, artifactId = artifactId, version = version)
    }

    companion object {
        private const val MAVEN_PROJECTS_MANAGER_CLASS = "org.jetbrains.idea.maven.project.MavenProjectsManager"
        private const val MAVEN_PROJECT_CLASS = "org.jetbrains.idea.maven.project.MavenProject"
        private const val MAVEN_ID_CLASS = "org.jetbrains.idea.maven.model.MavenId"
    }
}
