package com.itangcent.easyapi.channel.apipost

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Route assertions for [ApipostUrls].
 *
 * Cheap, but they pin the host prefix and the exact path of every route, which is
 * what makes a typo here visible in a unit test rather than as a `404 page not
 * found` at export time. All six paths were probed live (`REVIEW.md §6.8.3`,
 * `§6.9.1`).
 *
 * The trailing-slash case matters: users paste the host with and without one, and
 * `//open/apis/list` is a different (404) route.
 */
class ApipostUrlsTest {

    @Test
    fun `trailing slashes and whitespace are normalised away`() {
        assertEquals("https://open.apipost.net", ApipostUrls.normalizeBaseUrl("  https://open.apipost.net/  "))
        assertEquals("https://open.apipost.net", ApipostUrls.normalizeBaseUrl("https://open.apipost.net"))
    }

    @Test
    fun `every route sits under the open prefix of the configured host`() {
        val base = "https://open.apipost.net/"
        assertEquals("https://open.apipost.net/open/apis/list", ApipostUrls.listApisUrl(base))
        assertEquals("https://open.apipost.net/open/apis/create", ApipostUrls.createApiUrl(base))
        assertEquals("https://open.apipost.net/open/apis/update", ApipostUrls.updateApiUrl(base))
        assertEquals("https://open.apipost.net/open/models/list", ApipostUrls.listModelsUrl(base))
        assertEquals("https://open.apipost.net/open/models/create", ApipostUrls.createModelUrl(base))
        assertEquals("https://open.apipost.net/open/models/update", ApipostUrls.updateModelUrl(base))
    }

    @Test
    fun `the team and project routes are singular`() {
        // `/open/teams/list` and `/open/projects/list` both answer 404 — the
        // plural looks more natural and is wrong (REVIEW.md §6.11.1).
        val base = "https://open.apipost.net"
        assertEquals("https://open.apipost.net/open/team/list", ApipostUrls.listTeamsUrl(base))
        assertEquals("https://open.apipost.net/open/project/list", ApipostUrls.listProjectsUrl(base))
    }

    @Test
    fun `the default host matches the settings default`() {
        assertEquals(
            "https://open.apipost.net/open/apis/create",
            ApipostUrls.createApiUrl(ApipostSettings.APIPOST_OPEN_HOST),
        )
    }
}
