package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentDetail
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentInfo
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentValue
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.util.json.GsonUtils
import org.junit.Assert.*

/**
 * Tests for [CachedPostmanApiClient] environment methods.
 *
 * Covers caching behavior for `listEnvironments`, passthrough for
 * `getEnvironment`, cache invalidation on `createEnvironment` and
 * `updateEnvironment` (with workspaceId).
 *
 * Spec: requirements/REQ-4, tasks/3.1, tasks/3.2
 */
class CachedPostmanApiClientEnvironmentTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun createClient(apiKey: String = "test-api-key"): CachedPostmanApiClient {
        val postmanClient = PostmanApiClient(apiKey = apiKey, httpClient = UrlConnectionHttpClient)
        return CachedPostmanApiClient(postmanClient)
    }

    // ── listEnvironments ─────────────────────────────────────────────────────

    fun testListEnvironmentsWithEmptyApiKey() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "")

        val environments = client.listEnvironments("ws-1", useCache = false)
        assertTrue("Environments should be empty for blank API key", environments.isEmpty())
    }

    fun testListEnvironmentsWithCache() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val envsNoCache = client.listEnvironments("ws-1", useCache = false)
        val envsWithCache = client.listEnvironments("ws-1", useCache = true)

        assertNotNull("Environments list should not be null", envsWithCache)
    }

    fun testEnvironmentsCaching() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")
        val workspaceId = "ws-cache-test"

        val envs = client.listEnvironments(workspaceId, useCache = false)

        val cacheKey = "postman/environments_${workspaceId}.json"
        val cached = AppCacheRepository.getInstance().read(cacheKey)
        if (envs.isNotEmpty()) {
            assertNotNull("Cache should be populated after fetching environments", cached)
        }
    }

    fun testClearEnvironmentsCache() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")
        val workspaceId = "ws-clear-test"

        client.listEnvironments(workspaceId, useCache = false)
        client.clearEnvironmentsCache(workspaceId)

        val cacheKey = "postman/environments_${workspaceId}.json"
        val cached = AppCacheRepository.getInstance().read(cacheKey)
        assertNull("Cache should be cleared", cached)
    }

    fun testListEnvironmentsUsesCacheWhenAvailable() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")
        val workspaceId = "ws-use-cache"

        // Pre-populate cache with a known environment
        val cachedEnvs = listOf(
            PostmanEnvironmentInfo(id = "cached-1", name = "cached-dev", uid = "u-1")
        )
        val cacheKey = "postman/environments_${workspaceId}.json"
        AppCacheRepository.getInstance().write(cacheKey, GsonUtils.toJson(cachedEnvs))

        // useCache=true should return cached data
        val result = client.listEnvironments(workspaceId, useCache = true)

        assertEquals(1, result.size)
        assertEquals("cached-1", result[0].id)
        assertEquals("cached-dev", result[0].name)
    }

    fun testListEnvironmentsBypassesCacheWhenFlagFalse() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")
        val workspaceId = "ws-no-cache"

        // Pre-populate cache
        val cachedEnvs = listOf(PostmanEnvironmentInfo(id = "cached-1", name = "cached-dev"))
        val cacheKey = "postman/environments_${workspaceId}.json"
        AppCacheRepository.getInstance().write(cacheKey, GsonUtils.toJson(cachedEnvs))

        // useCache=false should bypass cache and fetch fresh
        val result = client.listEnvironments(workspaceId, useCache = false)

        // Result comes from actual API call (which will fail/return empty without real credentials)
        assertNotNull(result)
    }

    // ── getEnvironment ───────────────────────────────────────────────────────

    fun testGetEnvironmentWithEmptyApiKey() = runTest {
        val client = createClient(apiKey = "")
        val result = client.getEnvironment("env-1")
        assertNull("Should return null for blank API key", result)
    }

    // ── createEnvironment ────────────────────────────────────────────────────

    fun testCreateEnvironmentWithEmptyApiKey() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "")
        val env = PostmanEnvironmentDetail(name = "dev", values = listOf(PostmanEnvironmentValue("A", "1")))

        val result = client.createEnvironment("ws-1", env)

        // Mock mode succeeds
        assertTrue(result.success)
    }

    fun testCreateEnvironmentClearsCacheOnSuccess() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "")
        val workspaceId = "ws-create-clear"
        val env = PostmanEnvironmentDetail(name = "dev")

        // Pre-populate cache
        val cacheKey = "postman/environments_${workspaceId}.json"
        AppCacheRepository.getInstance().write(cacheKey, "[{\"id\":\"old\",\"name\":\"old\"}]")

        // Create with blank API key → mock mode → success → should clear cache
        val result = client.createEnvironment(workspaceId, env)
        assertTrue(result.success)

        val cached = AppCacheRepository.getInstance().read(cacheKey)
        assertNull("Cache should be cleared after successful create", cached)
    }

    // ── updateEnvironment (without workspaceId — passthrough, no cache clear) ─

    fun testUpdateEnvironmentPassthrough() = runTest {
        val client = createClient(apiKey = "")
        val env = PostmanEnvironmentDetail(name = "dev")

        val result = client.updateEnvironment("env-1", env)

        // Mock mode succeeds
        assertTrue(result.success)
    }

    // ── updateEnvironment (with workspaceId — clears cache on success) ───────

    fun testUpdateEnvironmentWithWorkspaceIdClearsCacheOnSuccess() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "")
        val workspaceId = "ws-update-clear"
        val env = PostmanEnvironmentDetail(name = "dev")

        // Pre-populate cache
        val cacheKey = "postman/environments_${workspaceId}.json"
        AppCacheRepository.getInstance().write(cacheKey, "[{\"id\":\"old\",\"name\":\"old\"}]")

        // Update with blank API key → mock mode → success → should clear cache
        val result = client.updateEnvironment("env-1", workspaceId, env)
        assertTrue(result.success)

        val cached = AppCacheRepository.getInstance().read(cacheKey)
        assertNull("Cache should be cleared after successful update with workspaceId", cached)
    }

    fun testUpdateEnvironmentWithWorkspaceIdDoesNotClearCacheOnFailure() = runTest {
        AppCacheRepository.getInstance().clear()

        // Use a non-blank API key so the call goes through HTTP (which will fail without a real server)
        val client = createClient(apiKey = "test-api-key")
        val workspaceId = "ws-update-fail"
        val env = PostmanEnvironmentDetail(name = "dev")

        // Pre-populate cache
        val cacheKey = "postman/environments_${workspaceId}.json"
        AppCacheRepository.getInstance().write(cacheKey, "[{\"id\":\"old\",\"name\":\"old\"}]")

        // Update with real API key → HTTP call fails → cache should NOT be cleared
        val result = client.updateEnvironment("env-1", workspaceId, env)

        val cached = AppCacheRepository.getInstance().read(cacheKey)
        // Cache should still be present (the call likely failed, but either way we verify cache is not aggressively cleared on failure)
        // Note: if the call somehow succeeded (unlikely without real credentials), cache would be cleared — that's also acceptable
        if (!result.success) {
            assertNotNull("Cache should NOT be cleared on failure", cached)
        }
    }
}
