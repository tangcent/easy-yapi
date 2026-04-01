package com.itangcent.easyapi.util.ide

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache

/**
 * Utility object for extracting Maven/Gradle project information.
 * 
 * Provides methods to resolve Maven coordinates (groupId, artifactId, version)
 * from PSI classes using either Maven or Gradle project integration.
 */
object MavenHelper {
    /**
     * Resolves Maven coordinates for a PSI class.
     * Tries Maven first, then Gradle.
     * 
     * @param psiClass The class to resolve coordinates for
     * @return MavenIdData with coordinates, or null if not found
     */
    fun getMavenId(psiClass: PsiClass): MavenIdData? {
        return runCatching { getMavenIdByMaven(psiClass) }.getOrNull()
            ?: runCatching { getMavenIdByGradle(psiClass) }.getOrNull()
    }

    /**
     * Resolves Maven coordinates using Maven project integration.
     */
    private fun getMavenIdByMaven(psiClass: PsiClass): MavenIdData? {
        val project = psiClass.project
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass) ?: return null
        val mavenProject = MavenProjectsManager.getInstance(project).findProject(module) ?: return null
        val mavenId = mavenProject.mavenId
        return MavenIdData(
            groupId = mavenId.groupId ?: return null,
            artifactId = mavenId.artifactId ?: return null,
            version = mavenId.version ?: return null
        )
    }

    /**
     * Resolves Maven coordinates using Gradle project integration.
     */
    private fun getMavenIdByGradle(psiClass: PsiClass): MavenIdData? {
        val project = psiClass.project
        val projectPath = project.basePath ?: return null
        val externalProject = ExternalProjectDataCache.getInstance(project).getRootExternalProject(projectPath) ?: return null
        return MavenIdData(groupId = externalProject.group, artifactId = externalProject.name, version = externalProject.version)
    }
}

/**
 * Data class holding Maven coordinates.
 * Provides methods to format coordinates for different build systems.
 * 
 * @property groupId The Maven group ID
 * @property artifactId The Maven artifact ID
 * @property version The version string
 */
data class MavenIdData(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    /**
     * Formats coordinates as Maven dependency XML.
     */
    fun maven(): String {
        return """
            <dependency>
                <groupId>$groupId</groupId>
                <artifactId>$artifactId</artifactId>
                <version>$version</version>
            </dependency>
        """.trimIndent()
    }

    /**
     * Formats coordinates as Gradle Kotlin DSL dependency.
     */
    fun gradleKotlin(): String {
        return """implementation("$groupId:$artifactId:$version")"""
    }

    /**
     * Formats coordinates as Gradle Groovy DSL dependency.
     */
    fun gradleGroovy(): String {
        return """implementation '$groupId:$artifactId:$version'"""
    }

    /**
     * Formats coordinates as SBT dependency.
     */
    fun sbt(): String {
        return "libraryDependencies += \"$groupId\" % \"$artifactId\" % \"$version\""
    }
}
