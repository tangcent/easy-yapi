package com.itangcent.easyapi.core.repository

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.settings.module.GrpcSettings
import com.itangcent.easyapi.core.settings.settings

@Service(Service.Level.PROJECT)
class RepositoryService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): RepositoryService = project.service()
    }

    fun getRepositories(): List<RepositoryConfig> {
        val settings = project.settings<GrpcSettings>()
        val userRepos = settings.grpcRepositories
            .mapNotNull { RepositoryConfig.parse(it) }
            .filter { it.enabled }

        return userRepos.ifEmpty {
            DefaultRepositories.detectFromEnvironment()
        }
    }

    fun getAllRepositories(): List<RepositoryConfig> {
        val settings = project.settings<GrpcSettings>()
        val userRepos = settings.grpcRepositories
            .mapNotNull { RepositoryConfig.parse(it) }

        return userRepos.ifEmpty {
            DefaultRepositories.detectFromEnvironment()
        }
    }
}
