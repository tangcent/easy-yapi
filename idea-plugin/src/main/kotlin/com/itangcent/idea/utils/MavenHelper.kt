package com.itangcent.idea.utils

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.safe
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache


/**
 * Utility object for obtaining Maven identifiers of a given PsiClass through Maven or Gradle.
 *
 * @author tangcent
 * @date 2024/08/11
 */
object MavenHelper {

    /**
     * Attempts to retrieve the Maven ID of a PsiClass using Maven or Gradle.
     *
     * @param psiClass the PsiClass for which the Maven ID is to be retrieved.
     * @return MavenIdData if found, null otherwise.
     */
    fun getMavenId(psiClass: PsiClass): MavenIdData? {
        return safe {
            getMavenIdByMaven(psiClass)
        } ?: safe {
            getMavenIdByGradle(psiClass)
        }
    }

    /**
     * Retrieves the Maven ID of a PsiClass using Maven.
     *
     * @param psiClass the PsiClass for which the Maven ID is to be retrieved.
     * @return MavenIdData if found, null otherwise.
     */
    private fun getMavenIdByMaven(psiClass: PsiClass): MavenIdData? {
        val project = psiClass.project
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass)
            ?: return null

        val mavenProjectsManager = MavenProjectsManager.getInstance(project)
        val mavenProject = mavenProjectsManager.findProject(module) ?: return null
        val mavenId = mavenProject.mavenId
        return MavenIdData(
            groupId = mavenId.groupId!!,
            artifactId = mavenId.artifactId!!,
            version = mavenId.version!!
        )
    }

    /**
     * Retrieves the Maven ID of a PsiClass using Gradle.
     *
     * @param psiClass the PsiClass for which the Maven ID is to be retrieved.
     * @return MavenIdData if found, null otherwise.
     */
    private fun getMavenIdByGradle(psiClass: PsiClass): MavenIdData? {
        val project = psiClass.project
        val projectPath = project.basePath ?: return null
        val externalProject = ExternalProjectDataCache.getInstance(project).getRootExternalProject(projectPath)
            ?: return null

        return MavenIdData(
            groupId = externalProject.group,
            artifactId = externalProject.name,
            version = externalProject.version
        )
    }
}

/**
 * Data class representing Maven ID information.
 */
@ScriptTypeName("MavenId")
class MavenIdData(
    val groupId: String,
    val artifactId: String,
    val version: String
) {

    /**
     * Generates a Maven dependency snippet
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
     * Generates a Gradle implementation dependency snippet
     */
    fun gradle(): String {
        return """
            implementation group: '$groupId', name: '$artifactId', version: '$version'
        """.trimIndent()
    }

    /**
     * Generates a Gradle implementation dependency snippet in short form.
     */
    fun gradleShort(): String {
        return """
            implementation '$groupId:$artifactId:$version'
        """.trimIndent()
    }

    /**
     * Generates a Gradle implementation dependency snippet in Kotlin DSL.
     */
    fun gradleKotlin(): String {
        return """
            implementation("$groupId:$artifactId:$version")
        """.trimIndent()
    }

    /**
     * Generates an SBT dependency snippet.
     */
    fun sbt(): String {
        return """
            libraryDependencies += "$groupId" % "$artifactId" % "$version"
        """.trimIndent()
    }

    /**
     * Generates an Ivy dependency snippet.
     */
    fun ivy(): String {
        return """
            <dependency org="$groupId" name="$artifactId" rev="$version" />
        """.trimIndent()
    }

    override fun toString(): String {
        return "$groupId:$artifactId:$version"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MavenIdData) return false

        if (groupId != other.groupId) return false
        if (artifactId != other.artifactId) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + artifactId.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }
}