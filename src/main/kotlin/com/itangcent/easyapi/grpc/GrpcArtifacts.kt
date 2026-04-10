package com.itangcent.easyapi.grpc

import java.nio.file.Path

data class Artifact(
    val groupId: String,
    val artifactId: String
) {
    val coordinate: String
        get() = "$groupId:$artifactId"

    val groupPath: String
        get() = groupId.replace('.', '/')

    fun jarName(version: String): String = "$artifactId-$version.jar"

    fun mavenUrl(mavenCentral: String, version: String): String {
        return "$mavenCentral/$groupPath/$artifactId/$version/${jarName(version)}"
    }

    override fun toString(): String = coordinate

    companion object {
        fun parse(coordinate: String): Artifact? {
            val parts = coordinate.split(":")
            if (parts.size != 2) return null
            return Artifact(parts[0], parts[1])
        }
    }
}

data class QualifiedArtifact(
    val artifact: Artifact,
    val version: String,
    val path: Path? = null
) {
    val coordinate: String
        get() = "${artifact.coordinate}:$version"

    val groupId: String
        get() = artifact.groupId

    val artifactId: String
        get() = artifact.artifactId

    override fun toString(): String = coordinate

    companion object {
        fun parse(coordinate: String): QualifiedArtifact? {
            val parts = coordinate.split(":")
            if (parts.size != 3) return null
            return QualifiedArtifact(Artifact(parts[0], parts[1]), parts[2])
        }
    }
}

enum class ArtifactVersionMode {
    LATEST,
    FIXED
}

data class GrpcArtifactConfig(
    val artifact: Artifact,
    var versionMode: ArtifactVersionMode = ArtifactVersionMode.LATEST,
    var fixedVersion: String? = null,
    var enabled: Boolean = true,
    var resolvedVersion: String? = null
) {
    val coordinate: String
        get() = artifact.coordinate

    fun effectiveVersion(defaultVersion: String): String {
        return when (versionMode) {
            ArtifactVersionMode.LATEST -> resolvedVersion ?: defaultVersion
            ArtifactVersionMode.FIXED -> fixedVersion ?: resolvedVersion ?: defaultVersion
        }
    }

    override fun toString(): String {
        val version = when (versionMode) {
            ArtifactVersionMode.LATEST -> resolvedVersion ?: "latest"
            ArtifactVersionMode.FIXED -> fixedVersion ?: resolvedVersion ?: "latest"
        }
        return "${artifact.coordinate}:$version"
    }

    companion object {
        fun parse(config: String): GrpcArtifactConfig? {
            val parts = config.split(":")
            return when (parts.size) {
                2 -> GrpcArtifactConfig(Artifact(parts[0], parts[1]))
                3 -> {
                    val artifact = Artifact(parts[0], parts[1])
                    val version = parts[2]
                    when {
                        version == "latest" -> GrpcArtifactConfig(artifact, ArtifactVersionMode.LATEST)
                        version.isNotBlank() -> GrpcArtifactConfig(
                            artifact,
                            ArtifactVersionMode.FIXED,
                            version
                        )
                        else -> GrpcArtifactConfig(artifact)
                    }
                }
                4 -> {
                    val artifact = Artifact(parts[0], parts[1])
                    val mode = if (parts[2] == "fixed") ArtifactVersionMode.FIXED else ArtifactVersionMode.LATEST
                    val enabled = parts[3].toBoolean()
                    GrpcArtifactConfig(artifact, mode, parts[2].takeIf { it != "latest" }, enabled)
                }
                else -> null
            }
        }
    }
}

object GrpcRequiredArtifacts {
    val GRPC_CORE = Artifact("io.grpc", "grpc-core")
    val GRPC_API = Artifact("io.grpc", "grpc-api")
    val GRPC_NETTY_SHADED = Artifact("io.grpc", "grpc-netty-shaded")
    val GRPC_PROTOBUF = Artifact("io.grpc", "grpc-protobuf")
    val GRPC_STUB = Artifact("io.grpc", "grpc-stub")
    val GRPC_SERVICES = Artifact("io.grpc", "grpc-services")
    val PROTOBUF_JAVA = Artifact("com.google.protobuf", "protobuf-java")
    val PROTOBUF_JAVA_UTIL = Artifact("com.google.protobuf", "protobuf-java-util")
    val GUAVA = Artifact("com.google.guava", "guava")
    val FAILURE_ACCESS = Artifact("com.google.guava", "failureaccess")
    val GSON = Artifact("com.google.code.gson", "gson")
    val PERFMARK_API = Artifact("io.perfmark", "perfmark-api")

    val ALL: List<Artifact> = listOf(
        GRPC_NETTY_SHADED,
        GRPC_API,
        GRPC_PROTOBUF,
        GRPC_STUB,
        GRPC_CORE,
        GRPC_SERVICES,
        PROTOBUF_JAVA,
        PROTOBUF_JAVA_UTIL,
        GUAVA,
        FAILURE_ACCESS,
        GSON,
        PERFMARK_API
    )

    val GRPC_ARTIFACT_IDS: Set<String> = ALL.map { it.artifactId }.toSet()

    val REQUIRED_GRPC_ARTIFACTS: List<Artifact> = listOf(
        GRPC_NETTY_SHADED,
        GRPC_API,
        GRPC_PROTOBUF,
        GRPC_STUB,
        GRPC_CORE
    )

    fun defaultConfigs(): List<GrpcArtifactConfig> = ALL.map { GrpcArtifactConfig(it) }

    fun mergeWithDefaults(
        userConfigs: List<GrpcArtifactConfig>?,
        additionalArtifacts: List<Artifact> = emptyList()
    ): List<GrpcArtifactConfig> {
        if (userConfigs.isNullOrEmpty()) {
            return defaultConfigs() + additionalArtifacts.map { GrpcArtifactConfig(it) }
        }

        val userConfigMap = userConfigs.associateBy { it.artifact.coordinate }
        val allArtifacts = ALL + additionalArtifacts

        return allArtifacts.map { artifact ->
            userConfigMap[artifact.coordinate] ?: GrpcArtifactConfig(artifact)
        }
    }
}
