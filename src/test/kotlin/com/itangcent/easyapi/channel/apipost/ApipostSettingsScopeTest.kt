package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.StorageScope
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Pins the storage scope of every [ApipostSettings] field (plain JUnit — no
 * IntelliJ fixture needed).
 *
 * The split is deliberate and asymmetric, because the two kinds of value have
 * different lifetimes:
 *
 * - **`apipostToken` / `apipostServer` are APPLICATION-scoped.** The token carries
 *   no project identity — every `apis` / `models` call must pass `project_id`
 *   explicitly — so there is nothing per-project to store. How *many* projects the
 *   token can actually reach is deliberately **not** asserted here: that question
 *   is still open (`REVIEW.md` §6.10.6). The scope choice holds either way.
 *
 * - **`apipostProjectId` is PROJECT-scoped.** A repository maps to one target
 *   project, and that mapping is per repository. Global scope would leak: repo A
 *   sets `P1`, repo B silently exports into `P1` too, and nothing reports an
 *   error — the server is happy to accept it. Same failure class as the
 *   `$ref` sub-case in §6.9.5, hence the explicit test.
 *
 * This mirrors [com.itangcent.easyapi.channel.postman.PostmanSettings], where
 * `postmanToken` is APPLICATION-scoped and `postmanWorkspace` /
 * `postmanCollections` are PROJECT-scoped.
 */
class ApipostSettingsScopeTest {

    private fun scopeOf(property: String): Scope? =
        ApipostSettings::class.memberProperties
            .first { it.name == property }
            .findAnnotation<StorageScope>()
            ?.value

    @Test
    fun `token is stored once because it carries no project identity`() {
        assertEquals(
            "apipostToken must be APPLICATION-scoped — it carries no project identity, so there is nothing per-project to store",
            Scope.APPLICATION,
            scopeOf("apipostToken"),
        )
    }

    @Test
    fun `server is account level`() {
        assertEquals(
            "apipostServer is the account's ApiPost host, identical across repositories",
            Scope.APPLICATION,
            scopeOf("apipostServer"),
        )
    }

    @Test
    fun `project id is per repository so targets never leak across repositories`() {
        assertEquals(
            "apipostProjectId must be PROJECT-scoped — global scope silently pushes repo B into repo A's project",
            Scope.PROJECT,
            scopeOf("apipostProjectId"),
        )
    }

    @Test
    fun `every field declares its scope explicitly`() {
        // A field without @StorageScope silently falls back to APPLICATION (R-A-2),
        // which is exactly the bug this file guards against — so no field may rely
        // on the default.
        val undeclared = ApipostSettings::class.memberProperties
            .filter { it.findAnnotation<StorageScope>() == null }
            .map { it.name }
            .sorted()

        assertEquals(
            "every ApipostSettings field must carry @StorageScope; undeclared fields default to APPLICATION",
            emptyList<String>(),
            undeclared,
        )
    }

}
