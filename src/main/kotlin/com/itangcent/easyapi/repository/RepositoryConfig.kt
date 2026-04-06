package com.itangcent.easyapi.repository

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Types of artifact repositories supported by the plugin.
 */
enum class RepositoryType {
    /** Standard Maven local repository (~/.m2/repository) */
    MAVEN_LOCAL,
    /** Gradle dependency cache (~/.gradle/caches/modules-2/files-2.1) */
    GRADLE_CACHE,
    /** User-specified custom repository path */
    CUSTOM
}

/**
 * Configuration for a single artifact repository.
 * 
 * Represents a location where the plugin can search for dependency JARs,
 * such as gRPC runtime libraries.
 * 
 * @property type The repository type
 * @property path The filesystem path to the repository
 * @property enabled Whether this repository is active for searching
 */
data class RepositoryConfig(
    val type: RepositoryType,
    var path: String,
    var enabled: Boolean = true
) {
    /** Converts the path string to a [Path] object. */
    fun toPath(): Path = Paths.get(path)

    /** Returns a human-readable display name for the repository. */
    fun displayName(): String = when (type) {
        RepositoryType.MAVEN_LOCAL -> "Maven Local"
        RepositoryType.GRADLE_CACHE -> "Gradle Cache"
        RepositoryType.CUSTOM -> "Custom: ${path.substringAfterLast('/')}"
    }

    companion object {
        /**
         * Parses a repository configuration string.
         * 
         * Supported formats:
         * - `maven:/path/to/.m2/repository` (enabled by default)
         * - `maven:true:/path/to/.m2/repository` (explicit enabled)
         * - `maven:false:/path/to/.m2/repository` (disabled)
         * - Same patterns for `gradle` and `custom` types
         * 
         * @param str The configuration string to parse
         * @return [RepositoryConfig] or null if the format is invalid
         */
        fun parse(str: String): RepositoryConfig? {
            val parts = str.split(":", limit = 3)
            return when {
                parts.size >= 3 -> {
                    val type = when (parts[0]) {
                        "maven" -> RepositoryType.MAVEN_LOCAL
                        "gradle" -> RepositoryType.GRADLE_CACHE
                        "custom" -> RepositoryType.CUSTOM
                        else -> return null
                    }
                    val enabled = parts[1].toBooleanStrictOrNull() ?: true
                    RepositoryConfig(type, parts[2], enabled)
                }
                parts.size == 2 -> {
                    val type = when (parts[0]) {
                        "maven" -> RepositoryType.MAVEN_LOCAL
                        "gradle" -> RepositoryType.GRADLE_CACHE
                        "custom" -> RepositoryType.CUSTOM
                        else -> return null
                    }
                    RepositoryConfig(type, parts[1])
                }
                else -> null
            }
        }

        /**
         * Serializes a repository configuration to a string.
         * 
         * Format: `{type}:{enabled}:{path}`
         * Example: `maven:true:/Users/user/.m2/repository`
         */
        fun serialize(config: RepositoryConfig): String {
            val typeKey = when (config.type) {
                RepositoryType.MAVEN_LOCAL -> "maven"
                RepositoryType.GRADLE_CACHE -> "gradle"
                RepositoryType.CUSTOM -> "custom"
            }
            return "$typeKey:${config.enabled}:${config.path}"
        }
    }
}

/**
 * Provides default repository locations detected from the environment.
 * 
 * Detects:
 * - Maven local repository from `maven.repo.local` system property or ~/.m2/repository
 * - Gradle cache from `GRADLE_USER_HOME` environment variable or ~/.gradle/caches
 */
object DefaultRepositories {

    /** Path to the Maven local repository. */
    val MAVEN_LOCAL: Path by lazy {
        val customSettings = System.getProperty("maven.repo.local")
        if (!customSettings.isNullOrBlank()) {
            Paths.get(customSettings)
        } else {
            Paths.get(System.getProperty("user.home"), ".m2", "repository")
        }
    }

    /** Path to the Gradle dependency cache. */
    val GRADLE_CACHE: Path by lazy {
        val gradleUserHome = System.getenv("GRADLE_USER_HOME")
            ?: System.getProperty("gradle.user.home")
        if (!gradleUserHome.isNullOrBlank()) {
            Paths.get(gradleUserHome, "caches", "modules-2", "files-2.1")
        } else {
            Paths.get(System.getProperty("user.home"), ".gradle", "caches", "modules-2", "files-2.1")
        }
    }

    /**
     * Detects available repositories from the current environment.
     * 
     * @return List of [RepositoryConfig] for repositories that exist on disk
     */
    fun detectFromEnvironment(): List<RepositoryConfig> {
        val configs = mutableListOf<RepositoryConfig>()

        val mavenLocal = MAVEN_LOCAL
        if (java.nio.file.Files.isDirectory(mavenLocal)) {
            configs.add(RepositoryConfig(RepositoryType.MAVEN_LOCAL, mavenLocal.toString()))
        }

        val gradleCache = GRADLE_CACHE
        if (java.nio.file.Files.isDirectory(gradleCache)) {
            configs.add(RepositoryConfig(RepositoryType.GRADLE_CACHE, gradleCache.toString()))
        }

        return configs
    }
}
