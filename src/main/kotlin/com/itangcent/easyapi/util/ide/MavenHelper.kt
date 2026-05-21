package com.itangcent.easyapi.util.ide

import com.intellij.psi.PsiClass

/**
 * Utility object for extracting Maven/Gradle project coordinates from PSI classes.
 *
 * Tries each registered [ProjectIdResolver] in order (Maven first, then Gradle)
 * and returns the first successful result. Resolvers use reflection to access
 * optional IntelliJ plugin APIs, so they gracefully degrade when a plugin is absent.
 */
object MavenHelper {

    private val resolvers: List<ProjectIdResolver> = listOf(
        MavenProjectIdResolver(),
        GradleProjectIdResolver()
    )

    /**
     * Resolves Maven coordinates for a PSI class.
     * Tries Maven first, then Gradle.
     *
     * @param psiClass The class to resolve coordinates for
     * @return MavenIdData with coordinates, or null if not found
     */
    fun getMavenId(psiClass: PsiClass): MavenIdData? {
        return resolvers.asSequence()
            .filter { it.available }
            .mapNotNull { runCatching { it.resolve(psiClass) }.getOrNull() }
            .firstOrNull()
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
    /** Formats coordinates as Maven dependency XML. */
    fun maven(): String {
        return """
            <dependency>
                <groupId>$groupId</groupId>
                <artifactId>$artifactId</artifactId>
                <version>$version</version>
            </dependency>
        """.trimIndent()
    }

    /** Formats coordinates as Gradle Kotlin DSL dependency. */
    fun gradleKotlin(): String {
        return """implementation("$groupId:$artifactId:$version")"""
    }

    /** Formats coordinates as Gradle Groovy DSL dependency. */
    fun gradleGroovy(): String {
        return """implementation '$groupId:$artifactId:$version'"""
    }

    /** Formats coordinates as SBT dependency. */
    fun sbt(): String {
        return "libraryDependencies += \"$groupId\" % \"$artifactId\" % \"$version\""
    }
}
