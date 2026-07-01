package com.itangcent.easyapi.exporter.markdown.template

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Focused unit tests for [TemplateHelpers] — one helper per group.
 *
 * Pattern A (pure JUnit, no Project/PSI). The engine integration (path resolution → helper
 * arg evaluation → result stringify) is covered by [TemplateEngineTest]; here we pin each
 * helper's contract directly.
 *
 * Contracts pinned (per `.spec/markdown-template/templates/CONTRACT.md` § Helpers required by
 * the example templates, and `tasks.md` Task 1.7):
 * - `requiredLabel bool` → `"YES"` when true, else `"NO"`.
 * - `eq a b` → Boolean equality (used inside `{{#if eq a b}}`).
 * - `typeOf model` → `Single`→type, `Array`→`<item>[]`, `Object`→"object", `Map`→"map".
 * - `indent depth` → empty at 0; `&ensp;&ensp;`×depth + `&#124;─` otherwise.
 * - `fieldDesc field` → comment + options joined with `<br>`; options as `value :desc` / `value`.
 * - `jsonDemo model` → ``` ```json … ``` ``` fence around `ObjectModelJsonConverter.toJson`.
 * - Unknown helper → null (engine emits empty + debug log).
 */
class TemplateHelpersTest {

    private val ctx: RenderContext = RenderContext(
        clock = Clock.fixed(Instant.parse("2026-03-15T10:30:45Z"), ZoneId.of("UTC")),
        zone = ZoneId.of("UTC"),
        username = "u",
        projectName = "p",
        pluginVersion = "v",
    )

    // ---------- requiredLabel ----------

    @Test
    fun testRequiredLabelTrueReturnsYES() {
        assertEquals("YES", TemplateHelpers.resolve("requiredLabel", listOf(true), ctx))
    }

    @Test
    fun testRequiredLabelFalseReturnsNO() {
        assertEquals("NO", TemplateHelpers.resolve("requiredLabel", listOf(false), ctx))
    }

    @Test
    fun testRequiredLabelNullReturnsNO() {
        assertEquals("NO", TemplateHelpers.resolve("requiredLabel", listOf<Any?>(null), ctx))
    }

    @Test
    fun testRequiredLabelNoArgsReturnsNO() {
        assertEquals("NO", TemplateHelpers.resolve("requiredLabel", emptyList(), ctx))
    }

    // ---------- eq ----------

    @Test
    fun testEqEqualStringsReturnsTrue() {
        assertEquals(true, TemplateHelpers.resolve("eq", listOf("HTTP", "HTTP"), ctx))
    }

    @Test
    fun testEqDifferentStringsReturnsFalse() {
        assertEquals(false, TemplateHelpers.resolve("eq", listOf("HTTP", "gRPC"), ctx))
    }

    @Test
    fun testEqBothNullReturnsTrue() {
        assertEquals(true, TemplateHelpers.resolve("eq", listOf<Any?>(null, null), ctx))
    }

    @Test
    fun testEqNullAndStringReturnsFalse() {
        assertEquals(false, TemplateHelpers.resolve("eq", listOf<Any?>(null, "x"), ctx))
    }

    @Test
    fun testEqNumbersEqualReturnsTrue() {
        assertEquals(true, TemplateHelpers.resolve("eq", listOf(1, 1), ctx))
    }

    @Test
    fun testEqNumbersDifferReturnsFalse() {
        assertEquals(false, TemplateHelpers.resolve("eq", listOf(1, 2), ctx))
    }

    @Test
    fun testEqMissingArgsReturnsFalse() {
        assertEquals(false, TemplateHelpers.resolve("eq", listOf("only-one"), ctx))
    }

    @Test
    fun testEqReturnsBooleanNotString() {
        // Boolean — not "true"/"false" — so {{#if eq a b}} truthiness works (string "false" would be truthy).
        val result = TemplateHelpers.resolve("eq", listOf("x", "y"), ctx)
        assertTrue("eq must return Boolean for #if truthiness", result is Boolean)
        assertFalse(result as Boolean)
    }

    // ---------- typeOf ----------

    @Test
    fun testTypeOfSingleReturnsType() {
        assertEquals("string", TemplateHelpers.resolve("typeOf", listOf(ObjectModel.single("string")), ctx))
    }

    @Test
    fun testTypeOfArrayReturnsItemArrayNotation() {
        assertEquals("string[]", TemplateHelpers.resolve("typeOf", listOf(ObjectModel.array(ObjectModel.single("string"))), ctx))
    }

    @Test
    fun testTypeOfNestedArrayReturnsDoubleArray() {
        assertEquals("int[][]", TemplateHelpers.resolve("typeOf", listOf(ObjectModel.array(ObjectModel.array(ObjectModel.single("int")))), ctx))
    }

    @Test
    fun testTypeOfObjectReturnsObjectLiteral() {
        assertEquals("object", TemplateHelpers.resolve("typeOf", listOf(ObjectModel.emptyObject()), ctx))
    }

    @Test
    fun testTypeOfMapReturnsMapLiteral() {
        assertEquals("map", TemplateHelpers.resolve("typeOf", listOf(ObjectModel.map(ObjectModel.single("string"), ObjectModel.single("int"))), ctx))
    }

    @Test
    fun testTypeOfNullReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("typeOf", listOf<Any?>(null), ctx))
    }

    @Test
    fun testTypeOfNonModelReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("typeOf", listOf("not-a-model"), ctx))
    }

    // ---------- indent ----------

    @Test
    fun testIndentZeroReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("indent", listOf(0), ctx))
    }

    @Test
    fun testIndentOneReturnsSinglePrefix() {
        assertEquals("&ensp;&ensp;&#124;─", TemplateHelpers.resolve("indent", listOf(1), ctx))
    }

    @Test
    fun testIndentTwoReturnsDoublePrefix() {
        assertEquals("&ensp;&ensp;&ensp;&ensp;&#124;─", TemplateHelpers.resolve("indent", listOf(2), ctx))
    }

    @Test
    fun testIndentThreeReturnsTriplePrefix() {
        assertEquals("&ensp;&ensp;&ensp;&ensp;&ensp;&ensp;&#124;─", TemplateHelpers.resolve("indent", listOf(3), ctx))
    }

    @Test
    fun testIndentNullReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("indent", listOf<Any?>(null), ctx))
    }

    @Test
    fun testIndentNegativeReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("indent", listOf(-1), ctx))
    }

    // ---------- fieldDesc ----------

    @Test
    fun testFieldDescCommentOnly() {
        val field = FieldModel(ObjectModel.single("string"), comment = "user name")
        assertEquals("user name", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescNullCommentReturnsEmpty() {
        val field = FieldModel(ObjectModel.single("string"), comment = null)
        assertEquals("", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescBlankCommentReturnsEmpty() {
        val field = FieldModel(ObjectModel.single("string"), comment = "   ")
        assertEquals("", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescOptionsOnlyNoDesc() {
        val field = FieldModel(
            ObjectModel.single("string"),
            options = listOf(FieldOption("A", null), FieldOption("B", null)),
        )
        assertEquals("A<br>B", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescOptionsOnlyWithDesc() {
        val field = FieldModel(
            ObjectModel.single("string"),
            options = listOf(FieldOption("A", "desc a"), FieldOption("B", "desc b")),
        )
        assertEquals("A :desc a<br>B :desc b", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescCommentAndOptionsJoinedWithBr() {
        val field = FieldModel(
            ObjectModel.single("string"),
            comment = "the status",
            options = listOf(FieldOption("A", "desc a"), FieldOption("B", null)),
        )
        assertEquals("the status<br>A :desc a<br>B", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescEmptyOptionsReturnsCommentOnly() {
        val field = FieldModel(
            ObjectModel.single("string"),
            comment = "hello",
            options = emptyList(),
        )
        assertEquals("hello", TemplateHelpers.resolve("fieldDesc", listOf(field), ctx))
    }

    @Test
    fun testFieldDescNullReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("fieldDesc", listOf<Any?>(null), ctx))
    }

    @Test
    fun testFieldDescNonFieldModelReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("fieldDesc", listOf("not-a-field"), ctx))
    }

    // ---------- jsonDemo ----------

    @Test
    fun testJsonDemoSingleString() {
        val model = ObjectModel.single("string")
        // JsonType.defaultValueForType("string") == "" → JSON literal ""
        val expected = "```json\n\"\"\n```"
        assertEquals(expected, TemplateHelpers.resolve("jsonDemo", listOf(model), ctx))
    }

    @Test
    fun testJsonDemoObject() {
        val model = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single("string"), comment = "user name"))
        )
        // Don't hardcode the exact JSON — just verify the fence wraps whatever toJson produces.
        val result = TemplateHelpers.resolve("jsonDemo", listOf(model), ctx) as String
        assertTrue("jsonDemo must start with ```json fence", result.startsWith("```json\n"))
        assertTrue("jsonDemo must end with ``` fence", result.endsWith("\n```"))
    }

    @Test
    fun testJsonDemoNullReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("jsonDemo", listOf<Any?>(null), ctx))
    }

    @Test
    fun testJsonDemoNonModelReturnsEmpty() {
        assertEquals("", TemplateHelpers.resolve("jsonDemo", listOf("not-a-model"), ctx))
    }

    // ---------- unknown helper ----------

    @Test
    fun testUnknownHelperReturnsNull() {
        // The engine converts null → empty + debug log. Here we verify the helper layer returns null.
        assertEquals(null, TemplateHelpers.resolve("doesNotExist", listOf("a", "b"), ctx))
    }

    @Test
    fun testUnknownHelperReturnsNullForNoArgs() {
        assertEquals(null, TemplateHelpers.resolve("doesNotExist", emptyList(), ctx))
    }
}
