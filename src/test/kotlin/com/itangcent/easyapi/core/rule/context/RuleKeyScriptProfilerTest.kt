package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.channel.postman.PostmanRuleKeys
import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.binding
import com.itangcent.easyapi.framework.custom.CustomRuleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleKeyScriptProfilerTest {

    @Test
    fun `field ignore describes field and method it contexts`() {
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.FIELD_IGNORE, "general")
        val objectsById = RuleKeyScriptProfiler.collectAllObjects(profile).associateBy { it.id }

        assertEquals("dynamic", profile.executionMode)
        assertTrue(profile.binding("it").objectTypes.containsAll(listOf("field", "method")))
        assertEquals(listOf("field", "method"), profile.itContexts.sorted())

        val field = objectsById["field"]!!
        assertTrue(field.methods.any { it.name == "type" })
        assertTrue(field.methods.any { it.name == "containingClass" })
        assertTrue(field.methods.any { it.name == "defineClass" })
    }

    @Test
    fun `http call after exposes request response and retry control`() {
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.HTTP_CALL_AFTER, "general")
        val objectsById = RuleKeyScriptProfiler.collectAllObjects(profile).associateBy { it.id }

        assertTrue(profile.bindings.any { it.name == "request" })
        assertTrue(profile.bindings.any { it.name == "response" })
        assertTrue(objectsById["request"]!!.methods.any { it.name == "setHeader" })
        assertTrue(objectsById["response"]!!.methods.any { it.name == "discard" })
    }

    @Test
    fun `export after exposes mutable api endpoint`() {
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.EXPORT_AFTER, "general")
        val objectsById = RuleKeyScriptProfiler.collectAllObjects(profile).associateBy { it.id }

        assertTrue(profile.bindings.any { it.name == "api" })
        val api = objectsById["api"]!!
        assertTrue(api.methods.any { it.name == "appendDesc" })
        assertTrue(api.methods.any { it.name == "toCurl" })
    }

    @Test
    fun `postman script keys share the single dynamic evaluation contract`() {
        // There is no two-stage model anymore: like every key, the value is
        // evaluated dynamically (groovy or literal) in one rule-evaluation
        // pass. The Postman runtime (pm.environment, pm.response, …) is
        // external to EasyAPI — documented in notes/recipe, not fabricated as
        // fake bindings.
        val prerequest = RuleKeyScriptProfiler.describe(PostmanRuleKeys.POSTMAN_PREREQUEST, "postman")
        val test = RuleKeyScriptProfiler.describe(PostmanRuleKeys.POSTMAN_TEST, "postman")

        assertEquals("dynamic", prerequest.executionMode)
        assertEquals("dynamic", test.executionMode)
        assertFalse(prerequest.bindings.any { it.name == "pm" })
        assertFalse(prerequest.bindings.any { it.name == "response" })
        assertTrue(prerequest.binding("it").objectTypes.contains("method"))
        assertTrue(
            "notes should teach the two value formats: ${prerequest.notes}",
            prerequest.notes.any { it.contains("injected literally") && it.contains("groovy:") }
        )
    }

    @Test
    fun `common tool bindings expose their callable APIs`() {
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.FIELD_IGNORE, "general")
        val objectsById = RuleKeyScriptProfiler.collectAllObjects(profile).associateBy { it.id }

        assertTrue(objectsById["session"]!!.methods.any { it.name == "get" })
        assertTrue(objectsById["session"]!!.methods.any { it.name == "set" })
        assertTrue(objectsById["config"]!!.methods.any { it.name == "get" })
        assertTrue(objectsById["helper"]!!.methods.any { it.name == "findClass" })
        assertTrue(objectsById["tool"]!!.methods.any { it.name == "toJson" })
    }

    @Test
    fun `collection stays a type anchor without JDK method listings`() {
        // The AI catalog reflects only classes the plugin owns (com.itangcent.*).
        // `collection` is a plain java.util.List at runtime — a type the LLM
        // already knows — so its object keeps the type + role description but
        // intentionally lists no methods.
        val profile = RuleKeyScriptProfiler.describe(PostmanRuleKeys.POSTMAN_COLLECTION_TEST, "postman")
        val objectsById = RuleKeyScriptProfiler.collectAllObjects(profile).associateBy { it.id }
        val collection = objectsById["collection"]
            ?: throw AssertionError("postman.collection.test must expose the collection object")

        assertTrue(
            "collection must keep its standard type anchor: ${collection.type}",
            collection.type.contains("List")
        )
        assertTrue(
            "collection must not enumerate JDK methods (now: ${collection.methods.map { it.name }})",
            collection.methods.isEmpty()
        )
    }

    @Test
    fun `static configuration has no script bindings`() {
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.MARKDOWN_TEMPLATE, "general")

        assertEquals("static-configuration", profile.executionMode)
        assertTrue(profile.bindings.isEmpty())
        assertTrue(profile.objectRefs.isEmpty())
        assertTrue(profile.itContexts.isEmpty())
    }

    @Test
    fun `multi kind it binding states the contextType discriminator`() {
        // Issue #756: without the hint the model probes the method surface
        // with it.respondsTo('containingClass') to guess the context kind.
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.FIELD_IGNORE, "general")
        val itBinding = profile.binding("it")

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
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.METHOD_DOC, "general")
        val itBinding = profile.binding("it")

        assertFalse(
            "single-kind keys have nothing to discriminate: '${itBinding.description}'",
            itBinding.description.contains("contextType")
        )
    }

    @Test
    fun `multi kind custom method keys state class and method values`() {
        val profile = RuleKeyScriptProfiler.describe(CustomRuleKeys.CUSTOM_METHOD_IS_API, "custom")
        val itBinding = profile.binding("it")

        assertTrue(
            "custom.method.* keys run with class OR method contexts: '${itBinding.description}'",
            itBinding.description.contains("'method'/'class'")
        )
    }

    @Test
    fun `interface object apis keep the universal ItContext surface`() {
        // ContextKind.typeClass was repointed at the script interfaces
        // (ItContext/ClassContext/...). These objects must keep the universal
        // script methods inherited from ItContext, not just the per-kind ones.
        val classProfile = RuleKeyScriptProfiler.describe(CustomRuleKeys.CUSTOM_CLASS_IS_API, "custom")
        val classObjectsById = RuleKeyScriptProfiler.collectAllObjects(classProfile).associateBy { it.id }
        val classApi = classObjectsById["class"]!!
        assertTrue("class api keeps contextType: ${classApi.methods.map { it.name }}", classApi.methods.any { it.name == "contextType" })
        assertTrue("class api keeps toJson: ${classApi.methods.map { it.name }}", classApi.methods.any { it.name == "toJson" })
        assertTrue("class api surfaces name as a method", classApi.methods.any { it.name == "getName" })

        val methodProfile = RuleKeyScriptProfiler.describe(RuleKeys.METHOD_DOC, "general")
        val methodObjectsById = RuleKeyScriptProfiler.collectAllObjects(methodProfile).associateBy { it.id }
        val methodApi = methodObjectsById["method"]!!
        assertTrue("method api keeps contextType: ${methodApi.methods.map { it.name }}", methodApi.methods.any { it.name == "contextType" })
        assertTrue("method api keeps ann: ${methodApi.methods.map { it.name }}", methodApi.methods.any { it.name == "ann" })
        assertTrue("method api keeps canonicalText: ${methodApi.methods.map { it.name }}", methodApi.methods.any { it.name == "canonicalText" })
    }

    @Test
    fun `field order with declares a and b as field context injections not script objects`() {
        // Issue: field.order.with injects two members (a, b) as comparison
        // operands. They are field/method *context values*, not script objects
        // — they must be surfaced as bindings with a declared type, and must
        // NOT appear in objectRefs (which would reference a non-existent
        // script-object API).
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.FIELD_ORDER_WITH, "general")
        val objectsById = RuleKeyScriptProfiler.collectAllObjects(profile).associateBy { it.id }

        val a = profile.binding("a")
        val b = profile.binding("b")
        assertEquals(listOf("field"), a.objectTypes)
        assertEquals(listOf("field"), b.objectTypes)
        assertTrue("a should be a context injection, not a script object", a.description.contains("context value"))

        assertFalse("a must not leak into objectRefs", profile.objectRefs.contains("a"))
        assertFalse("b must not leak into objectRefs", profile.objectRefs.contains("b"))
        assertFalse("no 'a' script-object API should exist", objectsById.containsKey("a"))
        assertFalse("no 'b' script-object API should exist", objectsById.containsKey("b"))
    }

    @Test
    fun `deprecated compatibility shims stay out of the object catalog`() {
        // ScriptApiEndpoint keeps the legacy 4-arg setParam/setPathParam
        // overloads alive for historical .rules. They are callable at runtime
        // but must not be advertised to the AI — otherwise a model writes a
        // rule against a shim instead of the current full-arity API.
        val profile = RuleKeyScriptProfiler.describe(RuleKeys.EXPORT_AFTER, "general")
        val api = RuleKeyScriptProfiler.collectAllObjects(profile).first { it.id == "api" }
        val signatures = api.methods.map { method ->
            "${method.name}(${method.parameters.size})"
        }

        assertFalse(
            "legacy 4-arg setParam must not be listed: $signatures",
            signatures.contains("setParam(4)")
        )
        assertFalse(
            "legacy 3-arg setPathParam must not be listed: $signatures",
            signatures.contains("setPathParam(3)")
        )
        assertTrue(
            "the current setParam arity must still be listed: $signatures",
            signatures.any { it.startsWith("setParam(") }
        )
    }

    @Test
    fun `a key without a declared summary falls back to a generated one`() {
        // Schemes are adopted incrementally: a key may declare its semantics
        // (static vs scripted, contexts, bindings) without writing a summary.
        // The profile must still be self-describing rather than blank.
        val staticKey = RuleKey.string("test.static.no.summary", scheme = RuleKeyScheme(staticConfiguration = true))
        val staticProfile = RuleKeyScriptProfiler.describe(staticKey, "test")

        assertEquals("static-configuration", staticProfile.executionMode)
        assertTrue(
            "static fallback should say the value is not scripted: ${staticProfile.description}",
            staticProfile.description.contains("not evaluated as a script")
        )

        val scriptedKey = RuleKey.string(
            "test.scripted.no.summary",
            scheme = RuleKeyScheme(contextKinds = listOf(ContextKind.FIELD))
        )
        val scriptedProfile = RuleKeyScriptProfiler.describe(scriptedKey, "test")

        assertEquals("dynamic", scriptedProfile.executionMode)
        assertTrue(
            "dynamic fallback should teach the accepted value forms: ${scriptedProfile.description}",
            scriptedProfile.description.contains("groovy:") &&
                scriptedProfile.description.contains("literal")
        )
    }

    @Test
    fun `an undeclared additional binding kind is a binding without an object api`() {
        // A channel-owned key may inject a runtime value the profiler has no
        // wrapper class for. It is still declared as a binding (the script can
        // use the variable) but must never be referenced as a script object —
        // that would point the AI at a method list that does not exist.
        val key = RuleKey.string(
            "test.custom.binding",
            scheme = RuleKeyScheme(
                contextKinds = listOf(ContextKind.METHOD),
                additionalBindings = listOf(binding("channelModel"))
            )
        )
        val profile = RuleKeyScriptProfiler.describe(key, "test")

        val binding = profile.binding("channelModel")
        assertEquals(listOf("channelModel"), binding.objectTypes)
        assertEquals("key-specific", binding.availability)
        assertFalse(
            "an unregistered kind has no reflected API and must not be referenced: ${profile.objectRefs}",
            profile.objectRefs.contains("channelModel")
        )
        assertFalse(
            "no object API should be collected for it",
            RuleKeyScriptProfiler.collectAllObjects(profile).any { it.id == "channelModel" }
        )
    }

    @Test
    fun `binding only objects are never collected as script objects`() {
        // `item`/`document` are channel-owned models: they are bound and usable
        // in scripts, but there is no com.itangcent wrapper class to reflect,
        // so they stay out of the object dictionary even when referenced.
        val profile = profileWith(
            objectRefs = listOf("item", "document", "api"),
            bindings = listOf(
                scriptBinding("item"),
                scriptBinding("document"),
                scriptBinding("api")
            )
        )

        val ids = RuleKeyScriptProfiler.collectAllObjects(profile).map { it.id }
        assertFalse("item has no wrapper class to reflect: $ids", ids.contains("item"))
        assertFalse("document has no wrapper class to reflect: $ids", ids.contains("document"))
        assertTrue("api does have one and must be collected: $ids", ids.contains("api"))
    }

    @Test
    fun `an it context id that is not a ContextKind is ignored rather than thrown`() {
        // collectAllObjects re-resolves the it-object ids by name. A malformed
        // id (a hand-built profile, or a future kind rename) must be skipped —
        // the profile is still useful without that one object — instead of
        // blowing up the whole catalog.
        val profile = profileWith(
            objectRefs = listOf("bogus-kind", "logger"),
            bindings = listOf(scriptBinding("it", objectTypes = listOf("bogus-kind")))
        )

        val ids = RuleKeyScriptProfiler.collectAllObjects(profile).map { it.id }
        assertFalse("an unresolvable it kind must be dropped: $ids", ids.contains("bogus-kind"))
        assertTrue("the rest of the catalog must survive: $ids", ids.contains("logger"))
    }

    @Test
    fun `objects referenced repeatedly are collected once`() {
        // The object dictionary is the join target for §objects: a repeated id
        // must not produce duplicate entries.
        val profile = profileWith(
            objectRefs = listOf("api", "logger", "api", "logger"),
            bindings = listOf(scriptBinding("api"), scriptBinding("logger"))
        )

        val ids = RuleKeyScriptProfiler.collectAllObjects(profile).map { it.id }
        assertEquals("each object appears once: $ids", ids.distinct(), ids)
        assertTrue(ids.containsAll(listOf("api", "logger")))
    }

    @Test
    fun `allScriptObjects covers every it kind common helper and wrapped additional object`() {
        // get_script_object_api resolves ids against this static dictionary:
        // it must contain every id any key could ever reference, independent of
        // which keys exist or are enabled.
        val dict = RuleKeyScriptProfiler.allScriptObjects().map { it.id }

        // Every it-context object (one per ContextKind).
        ContextKind.entries.forEach { kind ->
            assertTrue("it-context object '${kind.id}' must be in the dictionary: $dict", kind.id in dict)
        }
        // The common helper objects.
        listOf("logger", "session", "tool", "regex", "files", "config", "localStorage", "fieldContext", "httpClient", "helper", "runtime").forEach { id ->
            assertTrue("common object '$id' must be in the dictionary: $dict", id in dict)
        }
        // Additional bindings with a reflected wrapper class.
        listOf("api", "endpoint", "request", "response", "collection").forEach { id ->
            assertTrue("wrapped additional object '$id' must be in the dictionary: $dict", id in dict)
        }
        // Bindings without a wrapper class are NOT objects.
        assertFalse("a/b are context injections, not objects", dict.contains("a"))
        assertFalse("item is an opaque channel model, not an object", dict.contains("item"))
    }

    private fun profileWith(objectRefs: List<String>, bindings: List<ScriptBinding>): RuleScriptProfile =
        RuleScriptProfile(
            key = "test.profile",
            aliases = emptyList(),
            source = "test",
            executionMode = "dynamic",
            description = "",
            bindings = bindings,
            objectRefs = objectRefs,
            notes = emptyList()
        )

    private fun scriptBinding(name: String, objectTypes: List<String> = listOf(name)): ScriptBinding =
        ScriptBinding(name, objectTypes = objectTypes, availability = "always", description = "")

    private fun RuleScriptProfile.binding(name: String): ScriptBinding =
        bindings.firstOrNull { it.name == name }
            ?: throw AssertionError("missing binding '$name': $bindings")
}
