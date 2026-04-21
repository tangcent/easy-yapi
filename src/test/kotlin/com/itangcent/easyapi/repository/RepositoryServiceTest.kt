package com.itangcent.easyapi.repository

import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.settings.DefaultSettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class RepositoryServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var repositoryService: RepositoryService
    private lateinit var defaultSettingBinder: DefaultSettingBinder

    override fun setUp() {
        super.setUp()
        defaultSettingBinder = DefaultSettingBinder(project)
        project.registerServiceInstance(
            serviceInterface = DefaultSettingBinder::class.java,
            instance = defaultSettingBinder
        )
        repositoryService = RepositoryService(project)
    }

    fun testGetRepositoriesReturnsNonNullWhenNoConfig() {
        val repos = repositoryService.getRepositories()

        assertNotNull("Should return non-null list", repos)
    }

    fun testGetRepositoriesReturnsEnabledRepositories() {
        defaultSettingBinder.save(Settings(grpcRepositories = arrayOf(
            "maven:true:/path/to/.m2/repository",
            "gradle:false:/path/to/.gradle/caches"
        )))

        val repos = repositoryService.getRepositories()

        assertEquals("Should return only enabled repositories", 1, repos.size)
        assertEquals("Should be Maven type", RepositoryType.MAVEN_LOCAL, repos[0].type)
        assertTrue("Should be enabled", repos[0].enabled)
    }

    fun testGetRepositoriesFallsBackToDefaultsWhenAllDisabled() {
        defaultSettingBinder.save(Settings(grpcRepositories = arrayOf(
            "maven:false:/path/to/.m2/repository",
            "gradle:false:/path/to/.gradle/caches"
        )))

        val repos = repositoryService.getRepositories()

        assertNotNull("Should fall back to defaults when all repositories are disabled", repos)
    }

    fun testGetAllRepositoriesIncludesDisabled() {
        defaultSettingBinder.save(Settings(grpcRepositories = arrayOf(
            "maven:true:/path/to/.m2/repository",
            "gradle:false:/path/to/.gradle/caches"
        )))

        val repos = repositoryService.getAllRepositories()

        assertEquals("Should return all repositories including disabled", 2, repos.size)
    }

    fun testGetRepositoriesIgnoresInvalidEntries() {
        defaultSettingBinder.save(Settings(grpcRepositories = arrayOf(
            "maven:true:/path/to/.m2/repository",
            "invalid-entry",
            "",
            "unknown:/path"
        )))

        val repos = repositoryService.getRepositories()

        assertEquals("Should only return valid and enabled entries", 1, repos.size)
        assertEquals("Should be Maven type", RepositoryType.MAVEN_LOCAL, repos[0].type)
    }

    fun testGetRepositoriesWithCustomRepository() {
        defaultSettingBinder.save(Settings(grpcRepositories = arrayOf(
            "custom:true:/custom/repo/path"
        )))

        val repos = repositoryService.getRepositories()

        assertEquals("Should return custom repository", 1, repos.size)
        assertEquals("Should be Custom type", RepositoryType.CUSTOM, repos[0].type)
        assertEquals("Path should match", "/custom/repo/path", repos[0].path)
    }

    fun testGetRepositoriesWithGradleRepository() {
        defaultSettingBinder.save(Settings(grpcRepositories = arrayOf(
            "gradle:true:/path/to/.gradle/caches"
        )))

        val repos = repositoryService.getRepositories()

        assertEquals("Should return Gradle repository", 1, repos.size)
        assertEquals("Should be Gradle type", RepositoryType.GRADLE_CACHE, repos[0].type)
    }

    fun testGetAllRepositoriesReturnsNonNullWhenNoConfig() {
        val repos = repositoryService.getAllRepositories()

        assertNotNull("Should return non-null list", repos)
    }

    fun testGetRepositoriesWithEmptyArrayFallsBackToDefaults() {
        defaultSettingBinder.save(Settings(grpcRepositories = emptyArray()))

        val repos = repositoryService.getRepositories()

        assertNotNull("Should fall back to environment defaults", repos)
    }

    fun testGetAllRepositoriesWithEmptyArrayFallsBackToDefaults() {
        defaultSettingBinder.save(Settings(grpcRepositories = emptyArray()))

        val repos = repositoryService.getAllRepositories()

        assertNotNull("Should fall back to environment defaults", repos)
    }

    fun testGetInstance() {
        val service = RepositoryService.getInstance(project)
        assertNotNull("Service instance should not be null", service)
    }
}
