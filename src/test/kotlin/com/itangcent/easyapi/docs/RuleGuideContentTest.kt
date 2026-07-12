package com.itangcent.easyapi.docs

import com.itangcent.easyapi.testFramework.ResourceLoader
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content-assertion test for the `rule-guide.md` "Multi-Application Namespace"
 * section (Req 6.1, 6.5).
 *
 * The existing `RuleGuideWorkflowCatalogTest.kt` is an empty stub (a single
 * `package com.itangcent` line) and cannot be extended; this dedicated test is
 * the canonical assertion that the section exists and covers the load-bearing
 * topics the agent relies on at authoring time.
 *
 * Reads the resource via [ResourceLoader.readRaw] (CRLF→LF collapse) per the
 * AGENTS.md cross-platform golden-file rule — never [kotlin.io.path] /
 * `File.readText()`. Plain JUnit: no `Project`, no PSI/VFS, no IntelliJ
 * fixture required (the resource ships on the main classpath).
 */
class RuleGuideContentTest {

    private val ruleGuide: String by lazy {
        ResourceLoader.readRaw(RULE_GUIDE_RESOURCE)
    }

    @Test
    fun `rule guide contains the Multi-Application Namespace section header`() {
        assertTrue(
            "rule-guide.md must contain a '## Multi-Application Namespace' section header",
            ruleGuide.contains("## Multi-Application Namespace")
        )
    }

    @Test
    fun `section documents the namespace-key resolution order`() {
        // The three branches of the resolution order (Req 1.1/1.4/1.5).
        assertTrue(
            "section must reference module name as the first resolution branch",
            ruleGuide.contains("Module name", ignoreCase = true)
        )
        assertTrue(
            "section must reference spring.application.name as the second resolution branch",
            ruleGuide.contains("spring.application.name")
        )
        assertTrue(
            "section must reference ask_clarification as the final resolution branch",
            ruleGuide.contains("ask_clarification")
        )
    }

    @Test
    fun `section documents the per-app env-var naming convention`() {
        // The full naming convention surface (Req 1.1, 3.1, 3.2): host, bearer
        // token, and login params, all namespaced by <key>.
        assertTrue(
            "section must document the host placeholder {{<key>}}",
            ruleGuide.contains("{{<key>}}")
        )
        assertTrue(
            "section must document the bearer-token placeholder {{<key>-token}}",
            ruleGuide.contains("{{<key>-token}}")
        )
        assertTrue(
            "section must document the login-username placeholder {{<key>-username}}",
            ruleGuide.contains("{{<key>-username}}")
        )
        assertTrue(
            "section must document the login-password placeholder {{<key>-password}}",
            ruleGuide.contains("{{<key>-password}}")
        )
    }

    @Test
    fun `section documents the multi-app bundle-split rule`() {
        // Req 4.2: one propose_rule_content per app; bundle integrity still
        // required per app.
        assertTrue(
            "section must reference the propose_rule_content tool",
            ruleGuide.contains("propose_rule_content")
        )
        assertTrue(
            "section must describe splitting bundles per app (multi-app detection)",
            ruleGuide.contains("bundle", ignoreCase = true)
        )
    }

    @Test
    fun `section contains a worked example with two apps in one workspace`() {
        // The flagship worked example: order-service + payment-service sharing
        // one workspace, each namespaced independently.
        assertTrue(
            "section must contain a worked example with the order-service app",
            ruleGuide.contains("order-service")
        )
        assertTrue(
            "worked example must contain a second app (payment-service)",
            ruleGuide.contains("payment-service")
        )
        assertTrue(
            "worked example must show a namespaced host placeholder for one of the apps",
            ruleGuide.contains("{{order-service}}") || ruleGuide.contains("{{payment-service}}")
        )
    }

    @Test
    fun `section records the body-level namespacing limitation`() {
        // Req 5.5 (deferred per Decision 3): the v1 enabler converts header
        // values only; param/body conversion is deferred. The agent must not
        // promise body-level namespacing.
        assertTrue(
            "section must document the body-level namespacing limitation (Req 5.5)",
            ruleGuide.contains("body", ignoreCase = true) &&
                (ruleGuide.contains("deferred", ignoreCase = true) ||
                    ruleGuide.contains("limitation", ignoreCase = true))
        )
    }

    @Test
    fun `section records the resolution branch used in the proposal summary`() {
        // Req 1.5: the proposal summary must state which branch was used and
        // the resulting key so the user can correct a wrong guess.
        assertTrue(
            "section must instruct the agent to record the resolution branch in the proposal summary",
            ruleGuide.contains("proposal summary", ignoreCase = true)
        )
    }

    @Test
    fun `section documents the dependency-graph clustering step`() {
        // Req 4.1, 8.4: the rule-guide must name get_module_dependency_graph
        // as the tool to call when N > 1 to cluster modules into app groups.
        assertTrue(
            "section must reference the get_module_dependency_graph tool",
            ruleGuide.contains("get_module_dependency_graph")
        )
    }

    @Test
    fun `section documents the namespace-key normalization convention and examples`() {
        // Req 2.1: the normalization convention lives as prose in the rule-guide
        // (it governs the LLM agent's behavior, which performs the transform in
        // its own reasoning and bakes the literal — no Kotlin code calls a
        // normalizer). The load-bearing examples from Req 2.1 MUST be present so
        // the agent (and humans) can predict the result.
        assertTrue(
            "section must state the camelCase splitting rule",
            ruleGuide.contains("camelCase", ignoreCase = true)
        )
        assertTrue(
            "section must state the allowed character class [a-z0-9-]",
            ruleGuide.contains("[a-z0-9-]")
        )
        // The three canonical Req 2.1 examples.
        assertTrue(
            "section must show the OrderService -> order-service example",
            ruleGuide.contains("OrderService") && ruleGuide.contains("order-service")
        )
        assertTrue(
            "section must show the order_service -> order-service example",
            ruleGuide.contains("order_service")
        )
        assertTrue(
            "section must show the admin portal -> admin-portal example",
            ruleGuide.contains("admin portal") && ruleGuide.contains("admin-portal")
        )
    }

    companion object {
        private const val RULE_GUIDE_RESOURCE = "/docs/knowledge-base/rule-guide.md"
    }
}
