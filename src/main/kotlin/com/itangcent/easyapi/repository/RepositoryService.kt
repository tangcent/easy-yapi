package com.itangcent.easyapi.repository

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.DefaultSettingBinder
import com.itangcent.easyapi.settings.SettingBinder

@Service(Service.Level.PROJECT)
class RepositoryService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): RepositoryService = project.service()
    }

    private val settingBinder: SettingBinder by lazy {
        DefaultSettingBinder.getInstance(project)
    }

    fun getRepositories(): List<RepositoryConfig> {
        val settings = settingBinder.read()
        val userRepos = settings.grpcRepositories
            .mapNotNull { RepositoryConfig.parse(it) }
            .filter { it.enabled }

        return userRepos.ifEmpty {
            DefaultRepositories.detectFromEnvironment()
        }
    }

    fun getAllRepositories(): List<RepositoryConfig> {
        val settings = settingBinder.read()
        val userRepos = settings.grpcRepositories
            .mapNotNull { RepositoryConfig.parse(it) }

        return userRepos.ifEmpty {
            DefaultRepositories.detectFromEnvironment()
        }
    }
}
