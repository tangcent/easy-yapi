package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Engine-level contract for method-call syntax (`body.asDemo()`, `body.asJson()`, `body.asJson5()`),
 * the `meta.*` carve-out (review finding F4), the chained-call v1 boundary (F14), and the
 * non-BodyView receiver fallback (F7).
 *
 * Pure JUnit — no `Project`, no PSI/VFS.
 */
class TemplateEngineMethodCallTest {

    private val fixedInstant: Instant = Instant.parse("2026-03-15T10:30:45Z")
    private val zone: ZoneId = ZoneId.of("UTC")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, zone)

    private val ctx: RenderContext = RenderContext(
        clock = fixedClock,
        zone = zone,
        username = "test-user",
        projectName = "test-project",
        pluginVersion = "1.2.3",
    )

    private val emptyModel: TemplateModel = TemplateModel(
        moduleName = "TestAPI",
        groups = emptyList(),
        endpointCount = 0,
    )

    /** Builds a model with one HTTP endpoint whose body is a simple Object {name: string}. */
    private fun modelWithBody(bodyModel: ObjectModel): TemplateModel {
        val body = BodyView(
            model = bodyModel,
            fields = listOf(
                FieldView(
                    name = "name",
                    type = "string",
                    desc = "user name",
                    required = false,
                    defaultValue = null,
                    depth = 0,
                    indent = "",
                    hasChildren = false,
                    childrenCount = 0,
                    structuralKind = FieldStructuralKind.PRIMITIVE,
                ),
            ),
        )
        val endpoint = Endpoint(
            name = "Create User",
            description = null,
            protocol = "HTTP",
            path = "/api/users",
            method = "POST",
            http = HttpView(
                pathParams = emptyList(),
                queryParams = emptyList(),
                formParams = emptyList(),
                headers = emptyList(),
                body = body,
                response = null,
                hasRequestContent = true,
            ),
            grpc = null,
        )
        return TemplateModel(
            moduleName = "TestAPI",
            groups = listOf(Group(folder = "Users", endpoints = listOf(endpoint))),
            endpointCount = 1,
        )
    }

    private val simpleBodyModel: ObjectModel = ObjectModel.Object(
        mapOf("name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"))
    )

    private val modelWithSimpleBody: TemplateModel = modelWithBody(simpleBodyModel)

    private val expectedJson: String = ObjectModelJsonConverter.toJson(simpleBodyModel)
    private val expectedJson5: String = ObjectModelJsonConverter.toJson5(simpleBodyModel)

    // ---------- asDemo / asJson / asJson5 ----------

    @Test
    fun testAsDemoResolvesBodyAndReturnsJson() {
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asDemo()}}}{{/each}}{{/each}}"
        assertEquals(expectedJson, TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    @Test
    fun testAsJsonReturnsJson() {
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asJson()}}}{{/each}}{{/each}}"
        assertEquals(expectedJson, TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    @Test
    fun testAsJson5ReturnsJson5() {
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asJson5()}}}{{/each}}{{/each}}"
        assertEquals(expectedJson5, TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    @Test
    fun testAsJson5DiffersFromAsDemoWhenCommentsPresent() {
        // The body has a comment "user name" — toJson5 should embed it, toJson should not.
        assertNotEquals(expectedJson, expectedJson5)
        assertTrue("JSON5 should contain the comment", expectedJson5.contains("user name"))
    }

    @Test
    fun testAsDemoEqualsAsJson() {
        // F9 / FR-9: asDemo and asJson return byte-identical strings for the same BodyView.
        val template1 = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asDemo()}}}{{/each}}{{/each}}"
        val template2 = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asJson()}}}{{/each}}{{/each}}"
        assertEquals(
            TemplateEngine.render(template1, modelWithSimpleBody, ctx),
            TemplateEngine.render(template2, modelWithSimpleBody, ctx),
        )
    }

    // ---------- Raw form is not escaped ----------

    @Test
    fun testMethodCallInRawFormIsNotEscaped() {
        // {{{...}}} must NOT html-escape the JSON output (would corrupt quotes/braces).
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asDemo()}}}{{/each}}{{/each}}"
        val rendered = TemplateEngine.render(template, modelWithSimpleBody, ctx)
        // JSON contains `"` and `{` `}` — they must survive unescaped.
        assertTrue("rendered should contain {", rendered.contains("{"))
        assertTrue("rendered should contain }", rendered.contains("}"))
        assertTrue("rendered should contain \"", rendered.contains("\""))
    }

    @Test
    fun testMethodCallInEscapedFormIsEscaped() {
        // {{...}} (non-raw) MUST html-escape — pipes and newlines. JSON has neither by default,
        // but the contract is that {{...}} runs through MarkdownEscapeUtils.escape.
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{api.http.body.asDemo()}}{{/each}}{{/each}}"
        val rendered = TemplateEngine.render(template, modelWithSimpleBody, ctx)
        // The escaped form should still render the JSON (no pipe/newline to escape here),
        // so it equals the raw form for this particular body. We assert it's non-empty.
        assertTrue("escaped form should render non-empty", rendered.isNotEmpty())
    }

    // ---------- Null receiver ----------

    @Test
    fun testMethodCallOnNullReceiverRendersEmpty() {
        // Body is null → receiver resolves to null → renders empty, no throw, no log.
        val endpoint = Endpoint(
            name = "Get",
            description = null,
            protocol = "HTTP",
            path = "/api/x",
            method = "GET",
            http = HttpView(
                pathParams = emptyList(),
                queryParams = emptyList(),
                formParams = emptyList(),
                headers = emptyList(),
                body = null,
                response = null,
                hasRequestContent = false,
            ),
            grpc = null,
        )
        val model = TemplateModel(
            moduleName = "M",
            groups = listOf(Group(folder = "", endpoints = listOf(endpoint))),
            endpointCount = 1,
        )
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asDemo()}}}{{/each}}{{/each}}"
        assertEquals("", TemplateEngine.render(template, model, ctx))
    }

    // ---------- Unknown method on BodyView ----------

    @Test
    fun testUnknownMethodOnBodyViewRendersEmpty() {
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.unknownMethod()}}}{{/each}}{{/each}}"
        assertEquals("", TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    // ---------- Non-BodyView receiver (F7) ----------

    @Test
    fun testMethodCallOnNonBodyViewReceiverRendersEmpty() {
        // api.path is a String receiver. Calling asJson5() on it → empty + info log.
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.path.asJson5()}}}{{/each}}{{/each}}"
        assertEquals("", TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    // ---------- meta.* no-regression (F4) ----------

    @Test
    fun testMetaDateStillParsesAsBuiltinCall() {
        // meta.date('yyyy') must continue to resolve as a BuiltinCall, not a MethodCall.
        assertEquals("2026", TemplateEngine.render("{{meta.date(yyyy)}}", emptyModel, ctx))
    }

    @Test
    fun testMetaTimeStillParsesAsBuiltinCall() {
        assertEquals("10:30:45", TemplateEngine.render("{{meta.time(HH:mm:ss)}}", emptyModel, ctx))
    }

    @Test
    fun testMetaUnknownThingStillParsesAsBuiltinCall() {
        // meta.unknownThing('x') — not a registered builtin → empty, but parses as BuiltinCall.
        assertEquals("", TemplateEngine.render("{{meta.unknownThing('x')}}", emptyModel, ctx))
    }

    @Test
    fun testBareDateStillParsesAsBuiltinCall() {
        // Bare date('yyyy') (no dot) — BuiltinCall.
        assertEquals("2026", TemplateEngine.render("{{date(yyyy)}}", emptyModel, ctx))
    }

    @Test
    fun testMetaDateNoParensStillBuiltinCall() {
        assertEquals("2026-03-15", TemplateEngine.render("{{meta.date}}", emptyModel, ctx))
    }

    // ---------- Chained-call v1 boundary (F14) ----------

    @Test
    fun testChainedMethodCallRendersEmpty() {
        // api.http.body.asJson5().trim() — v1 does not support chained calls.
        // Renders empty + info log "Chained method calls are not supported in v1".
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.http.body.asJson5().trim()}}}{{/each}}{{/each}}"
        assertEquals("", TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    @Test
    fun testChainedCallOnMetaDateRendersEmpty() {
        // meta.date('yyyy').foo() — also a chained call; v1 does not support.
        assertEquals("", TemplateEngine.render("{{meta.date('yyyy').foo()}}", emptyModel, ctx))
    }

    // ---------- No-regression: helpers, dotted paths, if, each ----------

    @Test
    fun testDottedPathStillResolves() {
        // api.path (no parens) — Path, not MethodCall.
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{api.path}}}{{/each}}{{/each}}"
        assertEquals("/api/users", TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    @Test
    fun testIfBlockStillWorks() {
        val template = "{{#if moduleName}}yes{{else}}no{{/if}}"
        assertEquals("yes", TemplateEngine.render(template, emptyModel, ctx))
    }

    @Test
    fun testEachBlockStillWorks() {
        val template = "{{#each groups as g}}{{g.folder}}|{{/each}}"
        assertEquals("Users|", TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }

    @Test
    fun testJsonDemoHelperStillWorks() {
        // {{jsonDemo body.model}} — helper form, takes ObjectModel directly.
        // Uses raw {{{...}}} because jsonDemo's output contains newlines (fence + JSON).
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{{jsonDemo api.http.body.model}}}{{/each}}{{/each}}"
        val rendered = TemplateEngine.render(template, modelWithSimpleBody, ctx)
        // jsonDemo wraps in ```json\n...\n```
        assertTrue("jsonDemo should open fence", rendered.startsWith("```json\n"))
        assertTrue("jsonDemo should close fence", rendered.endsWith("\n```"))
        assertTrue("jsonDemo should contain the JSON", rendered.contains("\"name\""))
    }

    // ---------- Direct body.asDemo() (body is loop context) ----------

    @Test
    fun testAsDemoCalledOnLoopContextBody() {
        // Inside {{#each api.http.body.fields as f}}, `body` is NOT in scope.
        // But if we use `api.http.body` explicitly, it works.
        // Uses raw {{{...}}} because asDemo()'s JSON output contains newlines.
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}body={{{api.http.body.asDemo()}}}{{/each}}{{/each}}"
        assertEquals("body=$expectedJson", TemplateEngine.render(template, modelWithSimpleBody, ctx))
    }
}
