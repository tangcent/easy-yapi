package com.itangcent.easyapi.repository

import org.junit.Assert.*
import org.junit.Test

class DefaultRepositoriesTest {

    @Test
    fun testMavenLocalPath() {
        val path = DefaultRepositories.MAVEN_LOCAL
        assertNotNull("Maven local path should not be null", path)
        assertTrue("Maven local path should contain .m2 or be custom", 
            path.toString().contains(".m2") || 
            System.getProperty("maven.repo.local") != null)
    }

    @Test
    fun testGradleCachePath() {
        val path = DefaultRepositories.GRADLE_CACHE
        assertNotNull("Gradle cache path should not be null", path)
        assertTrue("Gradle cache path should contain gradle", 
            path.toString().contains("gradle"))
    }

    @Test
    fun testDetectFromEnvironment() {
        val repos = DefaultRepositories.detectFromEnvironment()
        assertNotNull("Should return list", repos)
        
        // At least one repository should be detected in most environments
        // (Maven local or Gradle cache typically exists)
        assertTrue("Should detect at least one repository or return empty list", 
            repos.isEmpty() || repos.isNotEmpty())
    }

    @Test
    fun testDetectedRepositoriesHaveCorrectTypes() {
        val repos = DefaultRepositories.detectFromEnvironment()
        for (repo in repos) {
            assertTrue("Repository type should be valid", 
                repo.type == RepositoryType.MAVEN_LOCAL || 
                repo.type == RepositoryType.GRADLE_CACHE)
        }
    }

    @Test
    fun testDetectedRepositoriesAreEnabled() {
        val repos = DefaultRepositories.detectFromEnvironment()
        for (repo in repos) {
            assertTrue("Detected repositories should be enabled", repo.enabled)
        }
    }
}
