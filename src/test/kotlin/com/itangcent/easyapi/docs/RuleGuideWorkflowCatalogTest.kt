package com.itangcent.easyapi.docs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drift tripwire for the workflow recipes and their script-context isolation
 * invariants.
 *
 * The recipes are spread across the whole knowledge surface rather than
 * living in one file: `rule-guide.md` keeps the user-facing workflow
 * narrative and the scope note, while the concrete recipe lines and
 * correctness notes live in the AI catalog — `ai/detection/` (per-pattern
 * recipes) and `ai/key-guides/` (per-key value shapes), with the normative
 * rules in `ai/agent-base.md`. This test reads all of them and asserts each
 * anchor string survives future edits — it does NOT validate the full
 * markdown, only that the key fragments are still present somewhere on that
 * surface.
 *
 * Additionally, enforces **script-context isolation invariants** (review
 * Issue: recipe context-mixing). `postman.test`/`postman.prerequest` rule
 * values must NOT carry a `groovy:` prefix (that routes them to
 * `Jsr223ScriptParser` at export time, where `pm` is unbound — the script
 * throws and the failure is silently swallowed). Conversely, `http.call.after`
 * recipes must NOT reference `pm.*` (that binding lives only in
 * `PmScriptExecutor`). These tests prevent regressions that re-introduce the
 * silent-failure trap.
 *
 * Sources are the repo working-tree files: the canonical
 * `src/main/resources/docs/knowledge-base/rule-guide.md` plus
 * `src/main/resources/ai/`. The external skill mirrors are kept in sync by
 * `syncKnowledgeBase` / `syncAgentCatalog` (verified by
 * `EasyYapiAssistantSkillTest`).
 *
 * Run with: `./gradlew test --tests "*.RuleGuideWorkflowCatalogTest*"`
 */
class RuleGuideWorkflowCatalogTest {

    private val ruleGuide: File =
        File("src/main/resources/docs/knowledge-base/rule-guide.md")

    private val aiDir: File = File("src/main/resources/ai")

    /**
     * Concatenation of every file that carries an anchor string. Reading the
     * union (rather than one file) is deliberate: the recipes' single home
     * changed when they moved out of `rule-guide.md`, and this guard is about
     * the *content* existing on the shipped knowledge surface, not about which
     * file holds it.
     */
    private val content: String by lazy {
        assertTrue(
            "rule-guide.md must exist at ${ruleGuide.path} " +
                "(test must run from project root)",
            ruleGuide.exists()
        )
        val sources = buildList {
            add(ruleGuide)
            add(aiDir.resolve("agent-base.md"))
            CATALOG_DIRS.forEach { dir ->
                aiDir.resolve(dir).listFiles()
                    ?.filter { it.isFile && it.extension == "md" }
                    ?.sortedBy { it.name }
                    ?.forEach { add(it) }
            }
        }
        val missing = sources.filterNot { it.isFile }
        assertTrue(
            "expected every knowledge source to exist on disk; missing: $missing",
            missing.isEmpty()
        )
        sources.joinToString("\n\n") { it.readText(Charsets.UTF_8) }
    }

    @Test
    fun `Workflow Patterns section exists`() {
        assertTrue(
            "rule-guide.md must contain a '## Workflow Patterns' section",
            content.contains("## Workflow Patterns")
        )
    }

    @Test
    fun `auth-chaining recipe uses postman test not prerequest for token extraction`() {
        // The #1 mistake is using postman.prerequest for token extraction.
        // The auth-chaining producer recipe MUST use postman.test (post-response).
        assertTrue(
            "auth-chaining producer recipe must use postman.test (post-response) " +
                "for token extraction, NOT postman.prerequest",
            content.contains("postman.test")
        )
        assertTrue(
            "auth-chaining recipe must reference pm.environment.set (token storage)",
            content.contains("pm.environment.set")
        )
    }

    @Test
    fun `auth-chaining recipe value is a literal script not groovy-prefixed`() {
        // Script-context isolation: postman.test values must NOT start with groovy:.
        // A groovy: prefix routes the value to Jsr223ScriptParser at export time,
        // where pm is NOT bound — the script throws MissingPropertyException and
        // the failure is silently swallowed (no test script lands in the collection).
        assertTrue(
            "auth-chaining recipe value must be a literal script (no groovy: prefix): " +
                "postman.test[...]=def token = pm.response.json()...",
            content.contains("postman.test[groovy: it.containingClass()?.qualifiedName() == \"com.example.AuthController\"]=def token = pm.response.json().token")
        )
        assertFalse(
            "auth-chaining recipe value must NOT be groovy-prefixed " +
                "(would execute in Jsr223ScriptParser where pm is unbound, silently failing)",
            content.contains("postman.test[\$class:com.example.AuthController]=groovy: def token")
        )
    }

    @Test
    fun `auth-chaining filter uses containingClass not dollar-class`() {
        // $class: on a PsiMethod matches the RETURN TYPE, not the containing class.
        // The auth-chaining recipe must use a groovy filter with containingClass().
        assertFalse(
            "auth-chaining recipe must NOT use \$class: filter " +
                "(\$class: on a PsiMethod matches return type, not containing class)",
            content.contains("postman.test[\$class:com.example.AuthController]")
        )
        assertTrue(
            "auth-chaining recipe must use a groovy filter with containingClass()",
            content.contains("postman.test[groovy: it.containingClass()?.qualifiedName() == \"com.example.AuthController\"]")
        )
    }

    @Test
    fun `static-auth and consumer recipes use method additional header`() {
        assertTrue(
            "static-auth recipe must use method.additional.header",
            content.contains("method.additional.header")
        )
        assertTrue(
            "consumer recipe must reference Bearer \${Authorization} (shared-env-var rule)",
            content.contains("Bearer \${Authorization}")
        )
    }

    @Test
    fun `per-request-injection and HMAC recipes use postman prerequest`() {
        assertTrue(
            "per-request-injection recipe must use postman.prerequest (pre-request)",
            content.contains("postman.prerequest")
        )
    }

    @Test
    fun `hmac recipe value is a literal script not groovy-prefixed`() {
        // Script-context isolation: postman.prerequest values must NOT start with groovy:.
        // Same reasoning as the auth-chaining test — groovy: prefix routes to
        // Jsr223ScriptParser where pm is unbound.
        assertTrue(
            "HMAC recipe value must be a literal script (no groovy: prefix): " +
                "postman.prerequest[...]=def mac = javax.crypto.Mac...",
            content.contains("postman.prerequest[groovy: it.containingClass()?.qualifiedName().startsWith(\"com.example.api.\")]=def mac = javax.crypto.Mac")
        )
        assertFalse(
            "HMAC recipe value must NOT be groovy-prefixed " +
                "(would execute in Jsr223ScriptParser where pm is unbound, silently failing)",
            content.contains("]=groovy: def mac = javax.crypto.Mac.getInstance")
        )
    }

    @Test
    fun `401-refresh recipe uses http call after`() {
        assertTrue(
            "401-refresh recipe must use http.call.after (post-call rule)",
            content.contains("http.call.after")
        )
        assertTrue(
            "401-refresh recipe must reference httpClient.newRequest (sub-request)",
            content.contains("httpClient.newRequest")
        )
    }

    @Test
    fun `401-refresh recipe does not reference pm binding`() {
        // Script-context isolation: http.call.after runs in Jsr223ScriptParser,
        // where pm is NOT bound. The recipe must not reference pm.* at all.
        // The 401-refresh recipe line is a single long line; extract it and
        // assert no pm. reference appears in the script body.
        val recipeLine = content.lines().firstOrNull { it.contains("http.call.after=groovy:") }
        assertTrue(
            "401-refresh recipe line must exist (http.call.after=groovy:...)",
            recipeLine != null
        )
        assertFalse(
            "401-refresh recipe must NOT reference pm. (pm is not bound in " +
                "Jsr223ScriptParser / http.call.after context — use session.set() " +
                "or localStorage.set() for storage instead). Recipe line:\n$recipeLine",
            recipeLine!!.contains("pm.environment.set") || recipeLine.contains("pm.response") ||
                recipeLine.contains("pm.request") || recipeLine.contains("pm.sendRequest")
        )
    }

    @Test
    fun `never-emit-secrets rule is present`() {
        assertTrue(
            "catalog must state the never-emit-secrets rule " +
                "(every credential is an env-var reference, never a literal value)",
            content.contains("never emit secrets") || content.contains("Never emit secrets") ||
                content.contains("no-hardcoded-secret") || content.contains("No hardcoded secret")
        )
    }

    @Test
    fun `never-strip-legitimate-auth-fields rule is present`() {
        assertTrue(
            "catalog must state the never-strip-legitimate-auth-fields rule " +
                "(do NOT generate field.ignore for password/secret/clientSecret/refreshToken)",
            content.contains("never strip legitimate auth") ||
                content.contains("Never strip legitimate auth") ||
                content.contains("legitimately")
        )
    }

    @Test
    fun `script-context isolation correctness rule is present`() {
        // The #6 correctness rule documents the silent-failure trap: postman.*
        // values must not be groovy-prefixed; http.call.* values must not use pm.
        assertTrue(
            "catalog must carry the script-context-isolation correctness rule " +
                "(postman.* values: no groovy: prefix; http.call.* values: no pm)",
            content.contains("Script-context isolation") || content.contains("script-context isolation") ||
                content.contains("silently swallowed")
        )
    }

    @Test
    fun `scope note correctly attributes postman and http call rules`() {
        // The scope note must distinguish: postman.* rules affect Postman export;
        // http.call.* rules affect the plugin HTTP client. Neither affects Markdown/cURL.
        assertTrue(
            "scope note must mention Postman",
            content.contains("Postman")
        )
        assertTrue(
            "scope note must mention the plugin HTTP client (interceptor hooks)",
            content.contains("HTTP client") || content.contains("interceptor")
        )
        assertTrue(
            "scope note must state neither affects Markdown/cURL export",
            content.contains("Markdown") && content.contains("cURL")
        )
    }

    private companion object {
        /** Catalog directories under `src/main/resources/ai/` carrying recipes. */
        val CATALOG_DIRS = listOf("detection", "key-guides")
    }
}
