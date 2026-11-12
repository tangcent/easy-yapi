package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.script.ScriptScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit coverage for the script-scope inheritance chain.
 *
 * The API Explorer stores a script set on the outermost project node under
 * `module:<project.name>` (the only scope kind available for that node), so
 * every endpoint in the project must resolve that same key. Otherwise a
 * script that sets e.g. a Cookie on the project root never runs.
 */
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
        // This is the key ApiDashboardPanel.resolveScopeForNode persists for the
        // outermost project node: NodeInfo("${project.name} (N)", null).
        val rootScopeKey = ScriptScope.Module(projectName).key

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
                ScriptScope.Module("shop-api"),
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
            listOf(ScriptScope.Module("shop-api"), ScriptScope.Class("com.example.UserCtrl")),
            scopes
        )
    }

    @Test
    fun `project and folder with the same name are not resolved twice`() {
        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("shop-api"), "shop-api")

        assertEquals(
            listOf(ScriptScope.Module("shop-api"), ScriptScope.Class("com.example.UserCtrl")),
            scopes
        )
    }

    @Test
    fun `all scopes keep project folder class order`() {
        val scopes = CurlScriptScopes.resolveAllScopes(endpoint("user-svc"), "shop-api")

        assertEquals(
            listOf(
                ScriptScope.Module("shop-api"),
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

    /**
     * The dashboard labels the root node "<project.name> (<count>)" and derives
     * the saved scope key from that label by stripping the trailing count suffix.
     * Keep both sides of the contract in one test so a change to either one is
     * caught here.
     */
    @Test
    fun `root node label resolves to the same key as the project scope`() {
        val projectName = "shop-api"
        val savedRootKey = ScriptScope.Module("$projectName (42)".substringBeforeLast(" (").trim()).key

        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("user-svc"), projectName)

        assertEquals("the saved root script key must lead the inheritance chain", savedRootKey, scopes.first().key)
    }

    @Test
    fun `project name containing a spaced parenthesis still matches the saved root key`() {
        val projectName = "shop (dev)"
        val savedRootKey = ScriptScope.Module("$projectName (42)".substringBeforeLast(" (").trim()).key

        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint("user-svc"), projectName)

        assertEquals("the saved root script key must lead the inheritance chain", savedRootKey, scopes.first().key)
    }

    @Test
    fun `folder name containing a spaced parenthesis matches the saved folder key`() {
        val folder = "user-svc (v2)"
        val savedFolderKey = ScriptScope.Module("$folder (7)".substringBeforeLast(" (").trim()).key

        val scopes = CurlScriptScopes.resolveFolderAndClassScopes(endpoint(folder), "shop-api")

        assertEquals(
            "the saved folder script key must be resolvable for its endpoints",
            savedFolderKey,
            scopes[1].key
        )
    }
}
