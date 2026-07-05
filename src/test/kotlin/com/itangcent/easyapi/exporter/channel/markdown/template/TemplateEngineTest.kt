package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.psi.model.ObjectModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Pins the templating language contract before the engine exists (test-first).
 * One focused test per construct.
 *
 * Pure JUnit — no `Project`, no PSI.
 *
 * Reference instant for built-in tests: `2026-03-15T10:30:45Z` (UTC), shared with
 * [TemplateBuiltinsTest].
 */
class TemplateEngineTest {

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

    /** Empty model — used by tests that only need `{{moduleName}}` or built-ins. */
    private val emptyModel: TemplateModel = TemplateModel(
        moduleName = "TestAPI",
        groups = emptyList(),
        endpointCount = 0,
    )

    /** One-group, one-endpoint model — used by loop / nested-path tests. */
    private fun singleEndpointModel(endpointName: String = "getUser"): TemplateModel {
        val endpoint = simpleHttpEndpointView(endpointName)
        return TemplateModel(
            moduleName = "TestAPI",
            groups = listOf(Group(folder = "Users", endpoints = listOf(endpoint))),
            endpointCount = 1,
        )
    }

    /** Build a minimal HTTP [Endpoint] view without going through the builder. */
    private fun simpleHttpEndpointView(name: String): Endpoint = Endpoint(
        name = name,
        description = null,
        protocol = "HTTP",
        path = "/api/users/{id}",
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

    // ---------- Interpolation: {{ x }} ----------

    @Test
    fun testInterpolationResolvesTopLevelField() {
        assertEquals("TestAPI", TemplateEngine.render("{{moduleName}}", emptyModel, ctx))
    }

    @Test
    fun testInterpolationEscapesPipeAndNewline() {
        // MarkdownEscapeUtils.escape: \n → <br>, | → \|
        val model = TemplateModel(moduleName = "a|b\nc", groups = emptyList(), endpointCount = 0)
        assertEquals("a\\|b<br>c", TemplateEngine.render("{{moduleName}}", model, ctx))
    }

    @Test
    fun testInterpolationOfBlankRendersEmpty() {
        val model = TemplateModel(moduleName = "", groups = emptyList(), endpointCount = 0)
        assertEquals("", TemplateEngine.render("{{moduleName}}", model, ctx))
    }

    @Test
    fun testMissingVarResolvesToEmptyAndDoesNotThrow() {
        assertEquals("", TemplateEngine.render("{{unknown.path}}", emptyModel, ctx))
    }

    // ---------- Raw interpolation: {{{ x }}} ----------

    @Test
    fun testRawInterpolationDoesNotEscape() {
        val model = TemplateModel(moduleName = "a|b\nc", groups = emptyList(), endpointCount = 0)
        assertEquals("a|b\nc", TemplateEngine.render("{{{moduleName}}}", model, ctx))
    }

    @Test
    fun testRawInterpolationPreservesHtmlEntities() {
        // Used for the indent prefix `&ensp;&ensp;&#124;─` in body fields.
        val field = FieldView(
            name = "id",
            type = "string",
            desc = "",
            required = false,
            defaultValue = null,
            depth = 1,
            indent = "&ensp;&ensp;&#124;─",
            hasChildren = false,
            childrenCount = 0,
            structuralKind = FieldStructuralKind.PRIMITIVE,
        )
        val bodyModel = ObjectModel.Object(mapOf("id" to com.itangcent.easyapi.psi.model.FieldModel(ObjectModel.single(com.itangcent.easyapi.psi.type.JsonType.STRING), comment = "user id")))
        val endpoint = Endpoint(
            name = "x",
            description = null,
            protocol = "HTTP",
            path = "/",
            method = "GET",
            http = HttpView(
                pathParams = emptyList(),
                queryParams = emptyList(),
                formParams = emptyList(),
                headers = emptyList(),
                body = BodyView(model = bodyModel, fields = listOf(field)),
                response = null,
                hasRequestContent = true,
            ),
            grpc = null,
        )
        val model = TemplateModel(
            moduleName = "M",
            groups = listOf(Group(folder = "", endpoints = listOf(endpoint))),
            endpointCount = 1,
        )
        // Raw interpolation of the field indent should preserve the entities verbatim.
        // Path: groups[0].endpoints[0].http.body.fields[0].indent — reached via loop aliases.
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{#each api.http.body.fields as f}}{{{f.indent}}}{{f.name}}{{/each}}{{/each}}{{/each}}"
        assertEquals(
            "&ensp;&ensp;&#124;─id",
            TemplateEngine.render(template, model, ctx),
        )
    }

    // ---------- Comments ----------

    @Test
    fun testCommentIsIgnored() {
        assertEquals("", TemplateEngine.render("{{!-- this is a comment --}}", emptyModel, ctx))
    }

    @Test
    fun testCommentBetweenTextIsRemovedButTextPreserved() {
        assertEquals(
            "before after",
            TemplateEngine.render("before{{!-- skip --}} after", emptyModel, ctx),
        )
    }

    // ---------- If / else ----------

    @Test
    fun testIfTrueWhenNonEmptyString() {
        assertEquals("yes", TemplateEngine.render("{{#if moduleName}}yes{{/if}}", emptyModel, ctx))
    }

    @Test
    fun testIfFalseWhenEmptyString() {
        val model = TemplateModel(moduleName = "", groups = emptyList(), endpointCount = 0)
        assertEquals("no", TemplateEngine.render("{{#if moduleName}}yes{{else}}no{{/if}}", model, ctx))
    }

    @Test
    fun testIfFalseWhenPathResolvesToNull() {
        // endpointCount is 0 (truthy=0 is falsy)
        val model = TemplateModel(moduleName = "M", groups = emptyList(), endpointCount = 0)
        assertEquals("no", TemplateEngine.render("{{#if endpointCount}}yes{{else}}no{{/if}}", model, ctx))
    }

    @Test
    fun testIfTrueWhenEndpointCountIsOne() {
        val model = TemplateModel(moduleName = "M", groups = emptyList(), endpointCount = 1)
        assertEquals("yes", TemplateEngine.render("{{#if endpointCount}}yes{{/if}}", model, ctx))
    }

    @Test
    fun testIfNoElseClauseRendersNothingWhenFalse() {
        val model = TemplateModel(moduleName = "", groups = emptyList(), endpointCount = 0)
        assertEquals("", TemplateEngine.render("{{#if moduleName}}yes{{/if}}", model, ctx))
    }

    // ---------- Each loops ----------

    @Test
    fun testEachEmptyListEmitsNothing() {
        val template = "{{#each groups}}{{folder}}|{{/each}}"
        assertEquals("", TemplateEngine.render(template, emptyModel, ctx))
    }

    @Test
    fun testEachIteratesOverList() {
        val model = singleEndpointModel()
        val template = "{{#each groups}}{{folder}}|{{/each}}"
        assertEquals("Users|", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testEachWithAlias() {
        val model = singleEndpointModel()
        val template = "{{#each groups as g}}{{g.folder}}{{/each}}"
        assertEquals("Users", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testEachWithIndex() {
        val model = singleEndpointModel()
        val template = "{{#each groups as g}}{{@index}}:{{g.folder}}|{{/each}}"
        assertEquals("0:Users|", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testEachWithFirstAndLast() {
        // Single-element list: both @first and @last are true.
        val model = singleEndpointModel()
        val template = "{{#each groups as g}}{{@first}}-{{@last}}|{{/each}}"
        assertEquals("true-true|", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testEachWithMultipleItemsFirstAndLast() {
        // Two-element list: @first only on first, @last only on last.
        val e1 = simpleHttpEndpointView("a").copy(path = "/a")
        val e2 = simpleHttpEndpointView("b").copy(path = "/b")
        val model = TemplateModel(
            moduleName = "M",
            groups = listOf(
                Group(folder = "G1", endpoints = listOf(e1)),
                Group(folder = "G2", endpoints = listOf(e2)),
            ),
            endpointCount = 2,
        )
        val template = "{{#each groups as g}}{{@first}}-{{@last}}:{{g.folder}}|{{/each}}"
        assertEquals("true-false:G1|false-true:G2|", TemplateEngine.render(template, model, ctx))
    }

    // ---------- Standalone-line trimming ----------

    @Test
    fun testStandaloneBlockTagLineTrimsEntireLineAndTrailingNewline() {
        // The `  {{#if moduleName}}  ` line is a standalone block tag → entire line + trailing \n removed.
        val template = "before\n  {{#if moduleName}}  \ninner\n  {{/if}}  \nafter"
        assertEquals("before\ninner\nafter", TemplateEngine.render(template, emptyModel, ctx))
    }

    @Test
    fun testNonStandaloneBlockTagLineNotTrimmed() {
        // Block tag sharing a line with text → no trimming.
        val template = "before {{#if moduleName}}inner{{/if}} after"
        assertEquals("before inner after", TemplateEngine.render(template, emptyModel, ctx))
    }

    @Test
    fun testEachStandaloneLineTrimsAroundTheLoop() {
        val template = "header\n{{#each groups}}\nitem\n{{/each}}\nfooter"
        val model = singleEndpointModel()
        // Standalone `{{#each}}` and `{{/each}}` lines → trimmed; only the body "item" remains per iteration.
        assertEquals("header\nitem\nfooter", TemplateEngine.render(template, model, ctx))
    }

    // ---------- Explicit trim markers ----------

    @Test
    fun testTrimLeftMarkerEatsPrecedingWhitespaceAndNewline() {
        // {{- trims whitespace/newlines immediately before the tag.
        val template = "foo\n  {{- if moduleName}}yes{{/if}}"
        assertEquals("fooyes", TemplateEngine.render(template, emptyModel, ctx))
    }

    @Test
    fun testTrimRightMarkerEatsFollowingWhitespaceAndNewline() {
        // -}} trims whitespace/newlines immediately after the tag.
        val template = "{{#if moduleName}}yes  \n  {{- /if}}bar"
        assertEquals("yesbar", TemplateEngine.render(template, emptyModel, ctx))
    }

    @Test
    fun testBothTrimMarkersTogether() {
        val template = "foo  {{- if moduleName -}}  bar{{/if}}"
        assertEquals("foobar", TemplateEngine.render(template, emptyModel, ctx))
    }

    // ---------- Unknown var / unknown helper → empty + no throw ----------

    @Test
    fun testUnknownHelperReturnsEmptyAndDoesNotThrow() {
        assertEquals("", TemplateEngine.render("{{unknownHelper arg}}", emptyModel, ctx))
    }

    @Test
    fun testUnknownHelperWithMultipleArgsReturnsEmpty() {
        assertEquals("", TemplateEngine.render("{{unknownHelper a b c}}", emptyModel, ctx))
    }

    // ---------- Built-in variables (against fixed RenderContext) ----------

    @Test
    fun testBuiltinDateDefaultPattern() {
        assertEquals("2026-03-15", TemplateEngine.render("{{date}}", emptyModel, ctx))
    }

    @Test
    fun testBuiltinDateCustomPattern() {
        assertEquals("202603", TemplateEngine.render("{{date(yyyyMM)}}", emptyModel, ctx))
    }

    @Test
    fun testBuiltinTimeDefaultPattern() {
        assertEquals("10:30:45", TemplateEngine.render("{{time}}", emptyModel, ctx))
    }

    @Test
    fun testBuiltinDateTimeDefaultPattern() {
        assertEquals("2026-03-15 10:30:45", TemplateEngine.render("{{datetime}}", emptyModel, ctx))
    }

    @Test
    fun testBuiltinDateTimeCustomPattern() {
        assertEquals(
            "2026-03-15 10:30:45",
            TemplateEngine.render("{{datetime(yyyy-MM-dd HH:mm:ss)}}", emptyModel, ctx),
        )
    }

    @Test
    fun testBuiltinTimestamp() {
        assertEquals(
            fixedInstant.toEpochMilli().toString(),
            TemplateEngine.render("{{timestamp}}", emptyModel, ctx),
        )
    }

    @Test
    fun testBuiltinUsername() {
        assertEquals("test-user", TemplateEngine.render("{{username}}", emptyModel, ctx))
    }

    @Test
    fun testBuiltinProjectName() {
        assertEquals("test-project", TemplateEngine.render("{{projectName}}", emptyModel, ctx))
    }

    @Test
    fun testBuiltinPluginVersion() {
        assertEquals("1.2.3", TemplateEngine.render("{{pluginVersion}}", emptyModel, ctx))
    }

    @Test
    fun testInvalidDatePatternResolvesToEmptyAndDoesNotThrow() {
        assertEquals("", TemplateEngine.render("{{date(INVALID)}}", emptyModel, ctx))
    }

    // ---------- meta.* namespace always built-in; bare names always model/loop data ----------

    @Test
    fun testMetaNamespaceDateEqualsBareDate() {
        // `{{meta.date}}` and `{{date}}` both resolve to the built-in (no model field named `date`).
        assertEquals(
            TemplateEngine.render("{{date}}", emptyModel, ctx),
            TemplateEngine.render("{{meta.date}}", emptyModel, ctx),
        )
    }

    @Test
    fun testMetaNamespaceWithCustomPattern() {
        assertEquals("202603", TemplateEngine.render("{{meta.date(yyyyMM)}}", emptyModel, ctx))
    }

    @Test
    fun testUnknownMetaNameReturnsEmpty() {
        assertEquals("", TemplateEngine.render("{{meta.unknownThing}}", emptyModel, ctx))
    }

    @Test
    fun testLoopVariableShadowsBuiltinOfSameName() {
        // Use `date` as the loop alias for `groups`; `{{date.folder}}` must resolve to the Group
        // (loop var), NOT the built-in `date`. The built-in `date` is shadowed inside the loop.
        val model = singleEndpointModel()
        val template = "{{#each groups as date}}{{date.folder}}|{{/each}}"
        assertEquals("Users|", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testLoopVariableShadowsBuiltinAcrossIterations() {
        val e1 = simpleHttpEndpointView("a").copy(path = "/a")
        val e2 = simpleHttpEndpointView("b").copy(path = "/b")
        val model = TemplateModel(
            moduleName = "M",
            groups = listOf(
                Group(folder = "G1", endpoints = listOf(e1)),
                Group(folder = "G2", endpoints = listOf(e2)),
            ),
            endpointCount = 2,
        )
        // `username` is the loop alias; `{{username.folder}}` is the Group folder.
        val template = "{{#each groups as username}}{{username.folder}}|{{/each}}"
        assertEquals("G1|G2|", TemplateEngine.render(template, model, ctx))
    }

    // ---------- Nested-path traversal ----------

    @Test
    fun testNestedPathTraversesModel() {
        val model = singleEndpointModel()
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{api.name}}|{{/each}}{{/each}}"
        assertEquals("getUser|", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testNestedPathMissingSegmentResolvesToEmpty() {
        val model = singleEndpointModel()
        // `groups[0].endpoints[0].http` is non-null but `nonexistent` doesn't exist → empty.
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{api.http.nonexistent}}|{{/each}}{{/each}}"
        assertEquals("|", TemplateEngine.render(template, model, ctx))
    }

    @Test
    fun testEndpointHttpFieldsAccessible() {
        val model = singleEndpointModel()
        val template = "{{#each groups as g}}{{#each g.endpoints as api}}{{api.protocol}}|{{api.path}}|{{api.method}}{{/each}}{{/each}}"
        assertEquals("HTTP|/api/users/{id}|GET", TemplateEngine.render(template, model, ctx))
    }
}
