package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.core.rule.RuleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleScriptContextCatalogTest {

    @Test
    fun `field ignore describes field and method it contexts`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.FIELD_IGNORE, "general")
        val stage = profile.stages.single()

        assertEquals("groovy-rule", profile.executionMode)
        assertTrue(stage.binding("it").objectTypes.containsAll(listOf("field", "method")))

        val field = stage.objectApi("field")
        assertTrue(field.methods.any { it.name == "type" })
        assertTrue(field.methods.any { it.name == "containingClass" })
        assertTrue(field.methods.any { it.name == "defineClass" })
    }

    @Test
    fun `http call after exposes request response and retry control`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.HTTP_CALL_AFTER, "general")
        val stage = profile.stages.single()

        assertTrue(stage.bindings.any { it.name == "request" })
        assertTrue(stage.bindings.any { it.name == "response" })
        assertTrue(stage.objectApi("request").methods.any { it.name == "setHeader" })
        assertTrue(stage.objectApi("response").methods.any { it.name == "discard" })
    }

    @Test
    fun `export after exposes mutable api endpoint`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.EXPORT_AFTER, "general")
        val stage = profile.stages.single()

        assertTrue(stage.bindings.any { it.name == "api" })
        val api = stage.objectApi("api")
        assertTrue(api.methods.any { it.name == "appendDesc" })
        assertTrue(api.methods.any { it.name == "toCurl" })
    }

    @Test
    fun `postman pre request separates rule it from generated script bindings`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.POSTMAN_PREREQUEST, "general")
        val ruleStage = profile.stage("rule-evaluation")
        val scriptStage = profile.stage("generated-script")

        assertTrue(ruleStage.binding("it").objectTypes.contains("method"))
        assertTrue(scriptStage.bindings.any { it.name == "pm" })
        assertTrue(scriptStage.bindings.any { it.name == "request" })
        assertFalse(scriptStage.bindings.any { it.name == "response" })
        assertTrue(scriptStage.objectApi("request").properties.any { it.name == "headers" })
    }

    @Test
    fun `postman test exposes response`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.POSTMAN_TEST, "general")
        val scriptStage = profile.stage("generated-script")

        assertTrue(scriptStage.bindings.any { it.name == "response" })
        assertTrue(scriptStage.objectApi("response").methods.any { it.name == "json" })
    }

    @Test
    fun `common tool bindings expose their callable APIs`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.FIELD_IGNORE, "general")
        val stage = profile.stages.single()

        assertTrue(stage.objectApi("session").methods.any { it.name == "get" })
        assertTrue(stage.objectApi("session").methods.any { it.name == "set" })
        assertTrue(stage.objectApi("config").methods.any { it.name == "get" })
        assertTrue(stage.objectApi("helper").methods.any { it.name == "findClass" })
        assertTrue(stage.objectApi("tool").methods.any { it.name == "toJson" })
    }

    @Test
    fun `static configuration has no script bindings`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.MARKDOWN_TEMPLATE, "general")

        assertEquals("static-configuration", profile.executionMode)
        assertTrue(profile.stages.isEmpty())
        assertNotNull(profile.summary)
    }

    @Test
    fun `multi kind it binding states the contextType discriminator`() {
        // Issue #756: without the hint the model probes the method surface
        // with it.respondsTo('containingClass') to guess the context kind.
        val profile = RuleScriptContextCatalog.describe(RuleKeys.FIELD_IGNORE, "general")
        val itBinding = profile.stages.single().binding("it")

        assertTrue(
            "it description should teach the discriminator: '${itBinding.description}'",
            itBinding.description.contains("it.contextType()")
        )
        assertTrue(
            "it description should list the runtime values: '${itBinding.description}'",
            itBinding.description.contains("'field'/'method'")
        )
    }

    @Test
    fun `single kind it binding has no discriminator hint`() {
        val profile = RuleScriptContextCatalog.describe(RuleKeys.METHOD_DOC, "general")
        val itBinding = profile.stages.single().binding("it")

        assertFalse(
            "single-kind keys have nothing to discriminate: '${itBinding.description}'",
            itBinding.description.contains("contextType")
        )
    }

    @Test
    fun `multi kind custom method keys state class and method values`() {
        val profile = RuleScriptContextCatalog.describe("custom.method.is.api")
        val itBinding = profile.stages.single().binding("it")

        assertTrue(
            "custom.method.* keys run with class OR method contexts: '${itBinding.description}'",
            itBinding.description.contains("'method'/'class'")
        )
    }

    private fun RuleScriptStage.binding(name: String): ScriptBinding =
        bindings.firstOrNull { it.name == name }
            ?: throw AssertionError("missing binding '$name': $bindings")

    private fun RuleScriptStage.objectApi(id: String): ScriptObjectApi =
        objects.firstOrNull { it.id == id }
            ?: throw AssertionError("missing object '$id': $objects")

    private fun RuleScriptProfile.stage(name: String): RuleScriptStage =
        stages.firstOrNull { it.name == name }
            ?: throw AssertionError("missing stage '$name': $stages")
}
