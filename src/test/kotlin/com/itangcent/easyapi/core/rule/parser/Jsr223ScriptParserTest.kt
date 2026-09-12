package com.itangcent.easyapi.core.rule.parser

import com.itangcent.easyapi.core.psi.type.IrType
import org.junit.Assert.*
import org.junit.Test

class Jsr223ScriptParserTest {

    @Test
    fun testGroovyScriptParserCanParse() {
        val parser = GroovyScriptParser()
        assertTrue("Should parse groovy: prefix", parser.canParse("groovy: it.name()"))
        assertFalse("Should not parse non-groovy prefix", parser.canParse("javascript: code"))
        assertFalse("Should not parse empty string", parser.canParse(""))
    }

    @Test
    fun testGroovyScriptParserPrefix() {
        val parser = GroovyScriptParser()
        assertTrue("Should recognize groovy: prefix", parser.canParse("groovy:1+1"))
    }

    // region toSchemaType — the draft-04 vocabulary table (moved from IrType)

    @Test
    fun testToSchemaType_jsonVocabulary() {
        assertEquals("string", toSchemaType("string"))
        assertEquals("string", toSchemaType("file"))
        assertEquals("integer", toSchemaType("int"))
        assertEquals("integer", toSchemaType("short"))
        assertEquals("integer", toSchemaType("long"))
        assertEquals("number", toSchemaType("float"))
        assertEquals("number", toSchemaType("double"))
        assertEquals("boolean", toSchemaType("boolean"))
        assertEquals("array", toSchemaType("array"))
        assertEquals("object", toSchemaType("object"))
        // Draft-04-only: legal for the YApi channel's declared `$schema`, illegal in OAS 3.0.3
        // (whose `type` has six values). `OpenApiSchemaConverter` therefore keeps its own table
        // and maps `"null"` to `string` rather than delegating here.
        assertEquals("null", toSchemaType("null"))
    }

    /**
     * Drift guard for the draft-04 table: every IR type needs an explicit answer. The `else`
     * branch (`"string"`) is a safety net for the rule-script aliases below, not a decision —
     * without this, adding an IR type would silently make it a `string` for YApi.
     *
     * The same guard on the OpenAPI side is
     * `OpenApiSchemaConverterTest.reachableSingleTypesMapToTheExpectedOasSchema`.
     */
    @Test
    fun testToSchemaType_coversEveryIrType() {
        val expected = mapOf(
            IrType.STRING to "string",
            IrType.SHORT to "integer",
            IrType.INT to "integer",
            IrType.LONG to "integer",
            IrType.FLOAT to "number",
            IrType.DOUBLE to "number",
            IrType.BOOLEAN to "boolean",
            IrType.ARRAY to "array",
            IrType.OBJECT to "object",
            IrType.FILE to "string",
        )
        assertEquals("every IR type needs a draft-04 mapping", IrType.ALL_TYPES, expected.keys)
        for ((type, schemaType) in expected) {
            assertEquals("'$type'", schemaType, toSchemaType(type))
        }
    }

    /**
     * The aliases that reach [toSchemaType] from rule scripts (`jsonTypeToSchemaType`)
     * and from the channels that legitimately share this draft-04 vocabulary (YApi).
     * They used to fall through to `"string"` — a wrong answer rather than a missing one.
     */
    @Test
    fun testToSchemaType_aliasesAndCase() {
        assertEquals("integer", toSchemaType("integer"))
        assertEquals("integer", toSchemaType("int32"))
        assertEquals("integer", toSchemaType("int64"))
        assertEquals("integer", toSchemaType("byte"))
        assertEquals("number", toSchemaType("number"))
        assertEquals("number", toSchemaType("decimal"))
        assertEquals("number", toSchemaType("bigdecimal"))
        assertEquals("boolean", toSchemaType("bool"))
        // Case-insensitive: script authors and converters spell these both ways.
        assertEquals("integer", toSchemaType("INTEGER"))
        assertEquals("boolean", toSchemaType("BOOLEAN"))
        // Unknown / blank fall back to string.
        assertEquals("string", toSchemaType("totallyUnknownThing"))
        assertEquals("string", toSchemaType(null))
        assertEquals("string", toSchemaType("  "))
    }

    // endregion
}
