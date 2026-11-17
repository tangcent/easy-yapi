package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.script.ScriptScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlScriptScopesTest {

    private fun endpoint(folder: String?, className: String? = "com.example.UserCtrl") =
        ApiEndpoint(
            name = "getUser",
            folder = folder,
            className = className,
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET),
        )

    @Test
    fun `project root script scope is resolvable for every endpoint`() {
        val projectName = "shop-api"
        val rootScopeKey = ScriptScope.Project(projectName).key

        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("user-svc"), projectName)

        assertTrue(
            "Scopes must include the project root scope $rootScopeKey, got ${scopes.map { it.key }}",
            scopes.any { it.key == rootScopeKey }
        )
    }

    @Test
    fun `project scope comes before folder and class scopes`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("user-svc"), "shop-api")

        assertEquals(
            listOf(
                ScriptScope.Project("shop-api"),
                ScriptScope.Module("user-svc"),
                ScriptScope.Class("com.example.UserCtrl")
            ),
            scopes
        )
    }

    @Test
    fun `project root script still applies to endpoints without a folder`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint(null), "shop-api")

        assertEquals(
            listOf(
                ScriptScope.Project("shop-api"),
                ScriptScope.NoFolder("shop-api"),
                ScriptScope.Class("com.example.UserCtrl"),
            ),
            scopes
        )
    }

    @Test
    fun `project and same-named folder resolve as distinct scopes`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("shop-api"), "shop-api")

        assertEquals(
            listOf(
                ScriptScope.Project("shop-api"),
                ScriptScope.Module("shop-api"),
                ScriptScope.Class("com.example.UserCtrl"),
            ),
            scopes
        )
    }

    @Test
    fun `all scopes keep project folder class order without an endpoint key`() {
        // The fixture has no sourceMethod, so resolveAllScopes cannot append an
        // Endpoint scope; this pins the project + folder + class portion only.
        val scopes = CurlScriptScopes.resolveAllScopes(endpoint("user-svc"), "shop-api")

        assertEquals(
            listOf(
                ScriptScope.Project("shop-api"),
                ScriptScope.Module("user-svc"),
                ScriptScope.Class("com.example.UserCtrl")
            ),
            scopes
        )
    }

    @Test
    fun `null project name keeps the folder and class scopes unchanged`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("user-svc"), null)

        assertEquals(
            listOf(ScriptScope.Module("user-svc"), ScriptScope.Class("com.example.UserCtrl")),
            scopes
        )
    }

    @Test
    fun `blank project name is ignored`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("user-svc"), "  ")

        assertEquals(
            listOf(ScriptScope.Module("user-svc"), ScriptScope.Class("com.example.UserCtrl")),
            scopes
        )
    }

    @Test
    fun `project and folder names keep their literal text`() {
        val project = "shop (dev)"
        val folder = " user-svc (v2)"
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint(folder), project)

        assertEquals(ScriptScope.Project(project), scopes[0])
        assertEquals(ScriptScope.Module(folder), scopes[1])
    }

    @Test
    fun `blank folder uses the No folder scope`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint(" "), "shop-api")

        assertEquals(ScriptScope.NoFolder("shop-api"), scopes[1])
    }
}
