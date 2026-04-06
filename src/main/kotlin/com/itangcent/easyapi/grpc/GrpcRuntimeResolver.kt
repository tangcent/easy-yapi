package com.itangcent.easyapi.grpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.repository.RepositoryService
import com.itangcent.easyapi.repository.RepositoryType
import com.itangcent.easyapi.settings.DefaultSettingBinder
import com.itangcent.easyapi.settings.SettingBinder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolved gRPC runtime containing JAR paths and version information.
 * 
 * @property jars List of JAR file paths needed for gRPC runtime
 * @property version The resolved gRPC version string (e.g., "1.58.0")
 */
data class ResolvedRuntime(val jars: List<Path>, val version: String)

/**
 * Project service that resolves gRPC runtime JARs from local repositories.
 * 
 * Searches for gRPC dependencies in:
 * 1. Maven local repository (~/.m2/repository)
 * 2. Gradle cache (~/.gradle/caches/modules-2/files-2.1)
 * 3. Custom configured repositories
 * 
 * Resolution strategy:
 * - Finds all gRPC JARs in the repository
 * - Selects the newest complete version (with all required artifacts)
 * - Includes additional configured dependencies
 * 
 * @see RepositoryService for repository configuration
 * @see GrpcRequiredArtifacts for required dependencies
 */
@Service(Service.Level.PROJECT)
class GrpcRuntimeResolver(private val project: Project) {

    companion object : IdeaLog {
        fun getInstance(project: Project): GrpcRuntimeResolver = project.service()
    }

    private val settingBinder: SettingBinder by lazy {
        DefaultSettingBinder.getInstance(project)
    }

    private val repositoryService: RepositoryService by lazy {
        RepositoryService.getInstance(project)
    }

    private fun getArtifactConfigs(): List<GrpcArtifactConfig> {
        val settings = settingBinder.read()
        val userConfigs = settings.grpcArtifactConfigs
            .mapNotNull { GrpcArtifactConfig.parse(it) }
        val additionalJars = settings.grpcAdditionalJars
            .mapNotNull { Paths.get(it) }
            .filter { Files.exists(it) }

        val additionalArtifacts = additionalJars.mapNotNull { path ->
            val name = path.fileName?.toString() ?: return@mapNotNull null
            extractArtifactFromFileName(name)
        }.distinct()

        return GrpcRequiredArtifacts.mergeWithDefaults(userConfigs, additionalArtifacts)
            .filter { it.enabled }
    }

    private fun extractArtifactFromFileName(fileName: String): Artifact? {
        for (artifact in GrpcRequiredArtifacts.ALL) {
            if (fileName.startsWith(artifact.artifactId)) {
                return artifact
            }
        }
        val match = Regex("""^([a-z\-]+)-[\d]+\.[\d]+\.[\d]+\.jar$""").find(fileName)
        if (match != null) {
            val artifactId = match.groupValues[1]
            return Artifact("unknown", artifactId)
        }
        return null
    }

    fun isAvailable(): Boolean = resolve() != null

    fun resolve(): ResolvedRuntime? {
        LOG.info("Resolving gRPC runtime from repositories")

        for (repo in repositoryService.getRepositories()) {
            val repoPath = repo.toPath()
            if (!Files.isDirectory(repoPath)) {
                LOG.info("Repository not found: ${repo.displayName()} at $repoPath")
                continue
            }

            LOG.info("Searching in ${repo.displayName()}: $repoPath")
            val result = when (repo.type) {
                RepositoryType.MAVEN_LOCAL -> resolveFromMavenRepository(repoPath)
                RepositoryType.GRADLE_CACHE -> resolveFromGradleCache(repoPath)
                RepositoryType.CUSTOM -> resolveFromMavenRepository(repoPath)
            }

            if (result != null) {
                LOG.info("gRPC runtime resolved from ${repo.displayName()}: version=${result.version}")
                return result
            }
        }

        LOG.warn("Failed to resolve gRPC runtime from all repositories")
        return null
    }

    private fun resolveFromMavenRepository(root: Path): ResolvedRuntime? {
        val grpcRoot = root.resolve("io").resolve("grpc")

        if (!Files.isDirectory(grpcRoot)) {
            LOG.info("Maven gRPC directory not found: $grpcRoot")
            return null
        }

        val grpcJarsByVersion = mutableMapOf<String, MutableList<Path>>()
        Files.walk(grpcRoot)
            .filter { it.toString().endsWith(".jar") }
            .filter { path ->
                val name = path.fileName?.toString() ?: ""
                !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")
            }
            .forEach { path ->
                val name = path.fileName?.toString() ?: return@forEach
                val versionMatch = Regex("""-([\d]+\.[\d]+\.[\d]+)\.jar$""").find(name)
                if (versionMatch != null) {
                    val version = versionMatch.groupValues[1]
                    grpcJarsByVersion.getOrPut(version) { mutableListOf() }.add(path)
                }
            }

        if (grpcJarsByVersion.isEmpty()) {
            LOG.info("No gRPC JARs found in repository: $grpcRoot")
            return null
        }

        val newestVersion = grpcJarsByVersion.keys.maxWithOrNull(compareBy {
            val parts = it.split(".")
            if (parts.size >= 3) {
                try {
                    parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                } catch (_: NumberFormatException) {
                    0
                }
            } else 0
        }) ?: return null

        val selectedJars = grpcJarsByVersion[newestVersion] ?: return null

        val requiredArtifacts = GrpcRequiredArtifacts.REQUIRED_GRPC_ARTIFACTS
        val hasRequiredGrpc = requiredArtifacts.all { artifact ->
            selectedJars.any { it.fileName?.toString()?.startsWith(artifact.artifactId) == true }
        }

        if (!hasRequiredGrpc) {
            val foundArtifacts = selectedJars.mapNotNull { jar ->
                val name = jar.fileName?.toString() ?: ""
                requiredArtifacts.find { name.startsWith(it.artifactId) }
            }.toSet()
            val missing = requiredArtifacts.toSet() - foundArtifacts
            LOG.info("Repository has incomplete gRPC JARs for version $newestVersion (missing: $missing)")
            val completeVersions = grpcJarsByVersion.entries
                .filter { (_, jars) ->
                    requiredArtifacts.all { artifact ->
                        jars.any { it.fileName?.toString()?.startsWith(artifact.artifactId) == true }
                    }
                }
                .sortedByDescending { (version, _) ->
                    val parts = version.split(".")
                    if (parts.size >= 3) {
                        try {
                            parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                        } catch (_: NumberFormatException) {
                            0
                        }
                    } else 0
                }

            if (completeVersions.isEmpty()) {
                LOG.info("No complete gRPC version found in repository (all versions missing required artifacts)")
                return null
            }

            val (fallbackVersion, fallbackJars) = completeVersions.first()
            LOG.info("Using fallback version $fallbackVersion (has all required artifacts)")
            return buildResolvedRuntime(fallbackVersion, fallbackJars, root)
        }

        return buildResolvedRuntime(newestVersion, selectedJars, root)
    }

    private fun buildResolvedRuntime(
        version: String,
        grpcJars: List<Path>,
        root: Path
    ): ResolvedRuntime {
        LOG.info("Building runtime: version=$version, grpcJars=${grpcJars.size}")
        grpcJars.forEach { jar ->
            LOG.info("  gRPC JAR: ${jar.fileName}")
        }

        val configs = getArtifactConfigs()
        val allJars = mutableListOf<Path>()
        allJars.addAll(grpcJars)

        for (config in configs) {
            if (config.artifact.groupId == "io.grpc") continue

            val artifact = config.artifact
            val artifactRoot = root.resolve(artifact.groupPath).resolve(artifact.artifactId)
            val jar = findNewestJar(artifactRoot, artifact.artifactId)
            if (jar != null) {
                allJars.add(jar)
                LOG.info("Selected ${artifact.artifactId}: ${jar.fileName}")
            } else {
                LOG.info("${artifact.artifactId} not found in repository: $artifactRoot")
            }
        }

        val additionalJars = settingBinder.read().grpcAdditionalJars
            .mapNotNull { Paths.get(it) }
            .filter { Files.exists(it) }
        allJars.addAll(additionalJars)

        LOG.info("Total JARs: ${allJars.size}")
        return ResolvedRuntime(allJars, version)
    }

    private fun findNewestJar(root: Path, artifactPrefix: String): Path? {
        if (!Files.isDirectory(root)) return null

        val jars = mutableListOf<Path>()
        Files.walk(root)
            .filter { it.toString().endsWith(".jar") }
            .filter { path ->
                val name = path.fileName?.toString() ?: ""
                !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")
            }
            .filter { path ->
                val name = path.fileName?.toString() ?: ""
                name.startsWith(artifactPrefix)
            }
            .forEach { jars.add(it) }

        return jars.maxByOrNull { path ->
            val name = path.fileName?.toString() ?: ""
            parseVersionNumber(extractVersionFromFileName(name))
        }
    }

    private fun extractVersionFromFileName(fileName: String): String {
        val match = Regex("""-([\d]+\.[\d]+\.[\d]+)\.jar$""").find(fileName)
        return match?.groupValues?.get(1) ?: "0.0.0"
    }

    private fun parseVersionNumber(version: String): Int {
        val parts = version.split(".")
        return if (parts.size >= 3) {
            try {
                parts[0].toInt() * 1000000 + parts[1].toInt() * 1000 + parts[2].toInt()
            } catch (_: NumberFormatException) {
                0
            }
        } else 0
    }

    private fun resolveFromGradleCache(gradleRoot: Path): ResolvedRuntime? {
        val grpcCacheRoot = gradleRoot.resolve("io.grpc")

        if (!Files.isDirectory(grpcCacheRoot)) {
            LOG.info("Gradle gRPC cache not found: $grpcCacheRoot")
            return null
        }

        val grpcJarsByVersion = mutableMapOf<String, MutableList<Path>>()
        for (artifact in GrpcRequiredArtifacts.REQUIRED_GRPC_ARTIFACTS) {
            val artifactPath = grpcCacheRoot.resolve(artifact.artifactId)
            if (!Files.isDirectory(artifactPath)) continue

            Files.list(artifactPath).forEach { versionDir ->
                val version = versionDir.fileName?.toString() ?: return@forEach
                Files.walk(versionDir)
                    .filter { it.toString().endsWith(".jar") }
                    .filter { path ->
                        val name = path.fileName?.toString() ?: ""
                        !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")
                    }
                    .forEach { jar ->
                        grpcJarsByVersion.getOrPut(version) { mutableListOf() }.add(jar)
                    }
            }
        }

        if (grpcJarsByVersion.isEmpty()) {
            LOG.info("No gRPC JARs found in Gradle cache: $grpcCacheRoot")
            return null
        }

        val newestVersion = grpcJarsByVersion.keys.maxWithOrNull(compareBy {
            val parts = it.split(".")
            if (parts.size >= 3) {
                try {
                    parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                } catch (_: NumberFormatException) {
                    0
                }
            } else 0
        }) ?: return null

        val selectedJars = grpcJarsByVersion[newestVersion] ?: return null
        val allJars = mutableListOf<Path>()
        allJars.addAll(selectedJars)

        for (config in getArtifactConfigs()) {
            if (config.artifact.groupId == "io.grpc") continue

            val artifact = config.artifact
            val artifactCacheRoot = gradleRoot.resolve(artifact.groupId).resolve(artifact.artifactId)
            if (!Files.isDirectory(artifactCacheRoot)) continue

            val jar = findNewestJarInGradleCache(artifactCacheRoot, artifact.artifactId)
            if (jar != null) {
                allJars.add(jar)
                LOG.info("Selected ${artifact.artifactId} from Gradle cache: ${jar.fileName}")
            }
        }

        val additionalJars = settingBinder.read().grpcAdditionalJars
            .mapNotNull { Paths.get(it) }
            .filter { Files.exists(it) }
        allJars.addAll(additionalJars)

        LOG.info("Selected gRPC version $newestVersion from Gradle cache")
        return ResolvedRuntime(allJars, newestVersion)
    }

    private fun findNewestJarInGradleCache(root: Path, artifactPrefix: String): Path? {
        if (!Files.isDirectory(root)) return null

        val jars = mutableListOf<Path>()
        Files.list(root).forEach { versionDir ->
            if (!Files.isDirectory(versionDir)) return@forEach
            Files.walk(versionDir)
                .filter { it.toString().endsWith(".jar") }
                .filter { path ->
                    val name = path.fileName?.toString() ?: ""
                    !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")
                }
                .filter { path ->
                    val name = path.fileName?.toString() ?: ""
                    name.startsWith(artifactPrefix)
                }
                .forEach { jars.add(it) }
        }

        return jars.maxByOrNull { path ->
            val name = path.fileName?.toString() ?: ""
            parseVersionNumber(extractVersionFromFileName(name))
        }
    }

    fun resolveVersions(): Map<String, String> {
        val versions = mutableMapOf<String, String>()
        val repos = repositoryService.getRepositories()

        for (repo in repos) {
            val repoPath = repo.toPath()
            if (!Files.isDirectory(repoPath)) continue

            when (repo.type) {
                RepositoryType.MAVEN_LOCAL, RepositoryType.CUSTOM -> {
                    resolveVersionsFromMaven(repoPath, versions)
                }

                RepositoryType.GRADLE_CACHE -> {
                    resolveVersionsFromGradle(repoPath, versions)
                }
            }
        }

        return versions
    }

    private fun resolveVersionsFromMaven(root: Path, versions: MutableMap<String, String>) {
        for (artifact in GrpcRequiredArtifacts.ALL) {
            if (versions.containsKey(artifact.coordinate)) continue

            val artifactRoot = root.resolve(artifact.groupPath).resolve(artifact.artifactId)
            if (!Files.isDirectory(artifactRoot)) continue

            val newestVersion = findNewestVersionInMaven(artifactRoot)
            if (newestVersion != null) {
                versions[artifact.coordinate] = newestVersion
            }
        }
    }

    private fun findNewestVersionInMaven(artifactRoot: Path): String? {
        if (!Files.isDirectory(artifactRoot)) return null

        val versions = mutableListOf<String>()
        Files.list(artifactRoot)
            .filter { Files.isDirectory(it) }
            .forEach { versionDir ->
                val version = versionDir.fileName?.toString() ?: return@forEach
                if (version.matches(Regex("""[\d]+\.[\d]+\.[\d]+"""))) {
                    versions.add(version)
                }
            }

        return versions.maxWithOrNull(compareBy { parseVersionNumber(it) })
    }

    private fun resolveVersionsFromGradle(gradleRoot: Path, versions: MutableMap<String, String>) {
        for (artifact in GrpcRequiredArtifacts.ALL) {
            if (versions.containsKey(artifact.coordinate)) continue

            val artifactCacheRoot = gradleRoot.resolve(artifact.groupId).resolve(artifact.artifactId)
            if (!Files.isDirectory(artifactCacheRoot)) continue

            val newestVersion = findNewestVersionInGradle(artifactCacheRoot)
            if (newestVersion != null) {
                versions[artifact.coordinate] = newestVersion
            }
        }
    }

    private fun findNewestVersionInGradle(artifactCacheRoot: Path): String? {
        if (!Files.isDirectory(artifactCacheRoot)) return null

        val versions = mutableListOf<String>()
        Files.list(artifactCacheRoot)
            .filter { Files.isDirectory(it) }
            .forEach { versionDir ->
                val version = versionDir.fileName?.toString() ?: return@forEach
                if (version.matches(Regex("""[\d]+\.[\d]+\.[\d]+"""))) {
                    versions.add(version)
                }
            }

        return versions.maxWithOrNull(compareBy { parseVersionNumber(it) })
    }
}
