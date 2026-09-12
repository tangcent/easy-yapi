package com.itangcent.easyapi.core.psi.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTypeTest {
    
    @Test
    fun testConstants() {
        assertEquals("string", JsonType.STRING)
        assertEquals("short", JsonType.SHORT)
        assertEquals("int", JsonType.INT)
        assertEquals("long", JsonType.LONG)
        assertEquals("float", JsonType.FLOAT)
        assertEquals("double", JsonType.DOUBLE)
        assertEquals("boolean", JsonType.BOOLEAN)
        assertEquals("array", JsonType.ARRAY)
        assertEquals("object", JsonType.OBJECT)
        assertEquals("file", JsonType.FILE)
        assertEquals("date", JsonType.DATE)
        assertEquals("datetime", JsonType.DATETIME)
        assertEquals("uuid", JsonType.UUID)
    }
    
    @Test
    fun testAllTypes() {
        assertTrue(JsonType.ALL_TYPES.contains("string"))
        assertTrue(JsonType.ALL_TYPES.contains("int"))
        assertTrue(JsonType.ALL_TYPES.contains("long"))
        assertTrue(JsonType.ALL_TYPES.contains("array"))
        assertTrue(JsonType.ALL_TYPES.contains("object"))
        assertTrue(JsonType.ALL_TYPES.contains("uuid"))
    }
    
    @Test
    fun testNumberTypes() {
        assertTrue(JsonType.isNumber("short"))
        assertTrue(JsonType.isNumber("int"))
        assertTrue(JsonType.isNumber("long"))
        assertTrue(JsonType.isNumber("float"))
        assertTrue(JsonType.isNumber("double"))
        assertFalse(JsonType.isNumber("string"))
        assertFalse(JsonType.isNumber("array"))
        assertFalse(JsonType.isNumber(null))
    }
    
    @Test
    fun testIsPrimitive() {
        assertTrue(JsonType.isPrimitive("string"))
        assertTrue(JsonType.isPrimitive("int"))
        assertTrue(JsonType.isPrimitive("boolean"))
        assertFalse(JsonType.isPrimitive("array"))
        assertFalse(JsonType.isPrimitive("object"))
        assertFalse(JsonType.isPrimitive(null))
    }
    
    @Test
    fun testIsValid() {
        assertTrue(JsonType.isValid("string"))
        assertTrue(JsonType.isValid("int"))
        assertTrue(JsonType.isValid("array"))
        assertTrue(JsonType.isValid("object"))
        assertFalse(JsonType.isValid("integer"))
        assertFalse(JsonType.isValid("list"))
        assertFalse(JsonType.isValid(null))
    }
    
    @Test
    fun testFromJavaType_string() {
        assertEquals(JsonType.STRING, JsonType.fromJavaType("java.lang.String"))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("String"))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("char"))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("java.lang.Character"))
    }
    
    @Test
    fun testFromJavaType_boolean() {
        assertEquals(JsonType.BOOLEAN, JsonType.fromJavaType("boolean"))
        assertEquals(JsonType.BOOLEAN, JsonType.fromJavaType("java.lang.Boolean"))
    }
    
    @Test
    fun testFromJavaType_integerTypes() {
        assertEquals(JsonType.INT, JsonType.fromJavaType("int"))
        assertEquals(JsonType.INT, JsonType.fromJavaType("java.lang.Integer"))
        assertEquals(JsonType.SHORT, JsonType.fromJavaType("short"))
        assertEquals(JsonType.SHORT, JsonType.fromJavaType("java.lang.Short"))
        assertEquals(JsonType.LONG, JsonType.fromJavaType("long"))
        assertEquals(JsonType.LONG, JsonType.fromJavaType("java.lang.Long"))
        assertEquals(JsonType.LONG, JsonType.fromJavaType("java.math.BigInteger"))
        assertEquals(JsonType.INT, JsonType.fromJavaType("byte"))
    }
    
    @Test
    fun testFromJavaType_floatTypes() {
        assertEquals(JsonType.FLOAT, JsonType.fromJavaType("float"))
        assertEquals(JsonType.FLOAT, JsonType.fromJavaType("java.lang.Float"))
        assertEquals(JsonType.DOUBLE, JsonType.fromJavaType("double"))
        assertEquals(JsonType.DOUBLE, JsonType.fromJavaType("java.lang.Double"))
        assertEquals(JsonType.DOUBLE, JsonType.fromJavaType("java.math.BigDecimal"))
    }
    
    /**
     * `date` / `datetime` are IR spellings a rule opts a type into
     * (`json.rule.convert[java.util.Date]=date`), so they must stay accepted as input. The
     * *source* spellings they used to be translated from (`java.time.LocalDate`,
     * `java.time.LocalDateTime`, `java.sql.Timestamp`, …) are deliberately no longer
     * recognised here: what those become is declared by the configuration
     * (`extensions/converts.config` maps them to `java.lang.String`) and applied by
     * `SpecialTypeHandler.resolveSpecialType` before this function is ever reached.
     */
    @Test
    fun testFromJavaType_dateAndDatetimeAreIrSpellings() {
        assertEquals(JsonType.DATE, JsonType.fromJavaType("date"))
        assertEquals(JsonType.DATETIME, JsonType.fromJavaType("datetime"))
        // The `Date` simple name is still recognised whichever package it is spelled with —
        // the two used to disagree (`java.util.Date` matched, `java.sql.Date` did not).
        assertEquals(JsonType.DATE, JsonType.fromJavaType("java.util.Date"))
        assertEquals(JsonType.DATE, JsonType.fromJavaType("java.sql.Date"))

        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.time.LocalDate"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.time.LocalDateTime"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.sql.Timestamp"))
    }

    /**
     * A qualified name is a real class, so the loose substring fallbacks — which exist only for
     * the bare spellings a rule script hands us — must not fire on it. They used to, and
     * `java.time.Duration` (its package name contains "time") came out as `datetime` while
     * `com.acme.Department` (it contains "part") came out as `file`.
     */
    @Test
    fun testFromJavaType_qualifiedSpellingsAreNotSubstringMatched() {
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.time.Duration"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.time.Period"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("com.acme.Department"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("com.acme.Timeline"))

        // The unqualified spelling keeps the legacy fallback — that is the script-facing path.
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("UserList"))
    }

    /**
     * Whether a container declaration carries files is decided per identifier *token*, not by
     * substring. The container branch used to test `contains("part")`, so any element whose
     * name merely contained "part" turned the whole array into an upload list —
     * `List<Department>`, `List<com.acme.Department>` and `List<Partition>` were all `file[]`.
     * Real file elements keep their answer, and spellings that never contained "part"
     * (`List<java.io.File>`, `List<java.nio.file.Path>`) now agree with what the resolver
     * produces for the same types.
     */
    @Test
    fun testFromJavaType_containerElementFileDetectionIsTokenLevel() {
        // Not files, whatever their name contains.
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("List<Department>"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.List<com.acme.Department>"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("List<Partition>"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.List<Departments>"))

        // Real file elements, in every spelling a rule script may hand over.
        assertEquals("file[]", JsonType.fromJavaType("List<MultipartFile>"))
        assertEquals("file[]", JsonType.fromJavaType("java.util.List<org.springframework.web.multipart.MultipartFile>"))
        assertEquals("file[]", JsonType.fromJavaType("List<Part>"))
        assertEquals("file[]", JsonType.fromJavaType("java.util.List<javax.servlet.http.Part>"))
        assertEquals("file[]", JsonType.fromJavaType("java.util.List<jakarta.servlet.http.Part>"))

        // These never contained "part", so they used to miss the file[] answer that the
        // resolver produces for the same element type; the token-level rule now matches it.
        assertEquals("file[]", JsonType.fromJavaType("java.util.List<java.io.File>"))
        assertEquals("file[]", JsonType.fromJavaType("java.util.List<java.nio.file.Path>"))
    }

    /**
     * The bare-spelling loose fallback uses the same token-level rule: `MultipartFile` and
     * `Part` are files, `Department` (it contains "part") is not — it falls through to
     * `object` like any other unknown bare class name.
     */
    @Test
    fun testFromJavaType_bareFileFallbackIsTokenLevel() {
        assertEquals(JsonType.FILE, JsonType.fromJavaType("MultipartFile"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("javax.servlet.http.Part"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("Department"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("Partition"))
    }

    /**
     * A UUID is a string on the wire and must never be expanded into the JDK class's
     * `mostSigBits`/`leastSigBits`. It is an IR spelling a rule can opt a type into; the FQN
     * spelling reaches the same answer through the simple name.
     */
    @Test
    fun testFromJavaType_uuidTypes() {
        assertEquals(JsonType.UUID, JsonType.fromJavaType("uuid"))
        assertEquals(JsonType.UUID, JsonType.fromJavaType("java.util.UUID"))
    }

    @Test
    fun testDefaultValueForType_nonJsonScalars() {
        // The IR vocabulary is richer than JSON; those values still need an example value.
        // They used to fall through to `null`, which disagreed with
        // `ObjectModelValueConverter.singleToValue` (see its drift-guard test).
        assertEquals("", JsonType.defaultValueForType(JsonType.DATE))
        assertEquals("", JsonType.defaultValueForType(JsonType.DATETIME))
        assertEquals("", JsonType.defaultValueForType(JsonType.UUID))
        assertEquals("(binary)", JsonType.defaultValueForType(JsonType.FILE))
    }

    @Test
    fun testToDisplayType() {
        // `date`/`datetime` are IR-only: they are neither JSON nor Java type names, and they do
        // not tell a reader what the wire value looks like, so a document's type column must not
        // print them.
        assertEquals("string", JsonType.toDisplayType(JsonType.DATE))
        assertEquals("string", JsonType.toDisplayType(JsonType.DATETIME))
        // Everything whose name already tells the reader the wire shape is kept as-is — this is
        // why `toSchemaType` (which collapses long/short to `integer`) is not the display
        // mapping.
        assertEquals("string", JsonType.toDisplayType(JsonType.STRING))
        assertEquals("long", JsonType.toDisplayType(JsonType.LONG))
        assertEquals("short", JsonType.toDisplayType(JsonType.SHORT))
        assertEquals("file", JsonType.toDisplayType(JsonType.FILE))
        assertEquals("uuid", JsonType.toDisplayType(JsonType.UUID))
        assertEquals("object", JsonType.toDisplayType(JsonType.OBJECT))
        assertEquals("custom", JsonType.toDisplayType("custom"))
    }

    /**
     * Drift guard: adding an IR type must force a decision about what a document prints for it.
     * Leaving `date`/`datetime` un-rewritten — or rewriting anything else — fails here.
     */
    @Test
    fun testToDisplayType_rewritesOnlyTheAmbiguousIrNames() {
        val rewritten = JsonType.ALL_TYPES.filter { JsonType.toDisplayType(it) != it }.toSet()
        assertEquals(setOf(JsonType.DATE, JsonType.DATETIME), rewritten)
    }
    
    @Test
    fun testFromJavaType_collectionTypes() {
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.List"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.ArrayList"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.Set"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.HashSet"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.Collection"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("List<String>"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("Set<Integer>"))
    }
    
    @Test
    fun testFromJavaType_mapTypes() {
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.util.Map"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.util.HashMap"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("Map<String, Object>"))
    }
    
    @Test
    fun testFromJavaType_fileTypes() {
        assertEquals(JsonType.FILE, JsonType.fromJavaType("org.springframework.web.multipart.MultipartFile"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("__file__"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("MultipartFile"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("javax.servlet.http.Part"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("jakarta.servlet.http.Part"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("java.io.File"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("java.nio.file.Path"))
        assertEquals(JsonType.FILE, JsonType.fromJavaType("org.springframework.core.io.Resource"))
    }
    
    @Test
    fun testFromJavaType_nullAndEmpty() {
        assertEquals(JsonType.STRING, JsonType.fromJavaType(null))
        assertEquals(JsonType.STRING, JsonType.fromJavaType(""))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("   "))
    }
    
    @Test
    fun testFromJavaType_customClass() {
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("com.example.MyCustomClass"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("UserDTO"))
    }

    /**
     * [JsonType.fromPrimitiveKind] is the `kind`-keyed view of [JsonType.fromJavaType].
     * They are separate tables, so this pins them together: for every kind, the kind mapping
     * must equal the mapping of that kind's boxed FQN. Without it the two could drift and
     * `Integer` vs `int` would produce different JSON types depending on the resolution path.
     */
    @Test
    fun testFromPrimitiveKindAgreesWithFromJavaType() {
        for (kind in PrimitiveKind.entries) {
            val viaKind = JsonType.fromPrimitiveKind(kind)
            if (kind == PrimitiveKind.VOID) {
                assertEquals("void has no value", null, viaKind)
                continue
            }
            assertEquals(
                "kind/boxed mismatch for $kind",
                viaKind,
                JsonType.fromJavaType(kind.wrapperFqnForTest())
            )
        }
    }

    @Test
    fun testFromPrimitiveKind_values() {
        assertEquals(JsonType.BOOLEAN, JsonType.fromPrimitiveKind(PrimitiveKind.BOOLEAN))
        assertEquals(JsonType.INT, JsonType.fromPrimitiveKind(PrimitiveKind.BYTE))
        assertEquals(JsonType.STRING, JsonType.fromPrimitiveKind(PrimitiveKind.CHAR))
        assertEquals(JsonType.SHORT, JsonType.fromPrimitiveKind(PrimitiveKind.SHORT))
        assertEquals(JsonType.INT, JsonType.fromPrimitiveKind(PrimitiveKind.INT))
        assertEquals(JsonType.LONG, JsonType.fromPrimitiveKind(PrimitiveKind.LONG))
        assertEquals(JsonType.FLOAT, JsonType.fromPrimitiveKind(PrimitiveKind.FLOAT))
        assertEquals(JsonType.DOUBLE, JsonType.fromPrimitiveKind(PrimitiveKind.DOUBLE))
    }

    @Test
    fun testToSchemaType_jsonVocabulary() {
        assertEquals("string", JsonType.toSchemaType("string"))
        assertEquals("string", JsonType.toSchemaType("date"))
        assertEquals("string", JsonType.toSchemaType("datetime"))
        assertEquals("string", JsonType.toSchemaType("file"))
        assertEquals("integer", JsonType.toSchemaType("int"))
        assertEquals("integer", JsonType.toSchemaType("short"))
        assertEquals("integer", JsonType.toSchemaType("long"))
        assertEquals("number", JsonType.toSchemaType("float"))
        assertEquals("number", JsonType.toSchemaType("double"))
        assertEquals("boolean", JsonType.toSchemaType("boolean"))
        assertEquals("array", JsonType.toSchemaType("array"))
        assertEquals("object", JsonType.toSchemaType("object"))
        // Draft-04-only: legal for the YApi channel's declared `$schema`, illegal in OAS 3.0.3
        // (whose `type` has six values). `OpenApiSchemaConverter` therefore keeps its own table
        // and maps `"null"` to `string` rather than delegating here.
        assertEquals("null", JsonType.toSchemaType("null"))
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
            JsonType.STRING to "string",
            JsonType.SHORT to "integer",
            JsonType.INT to "integer",
            JsonType.LONG to "integer",
            JsonType.FLOAT to "number",
            JsonType.DOUBLE to "number",
            JsonType.BOOLEAN to "boolean",
            JsonType.ARRAY to "array",
            JsonType.OBJECT to "object",
            JsonType.FILE to "string",
            JsonType.DATE to "string",
            JsonType.DATETIME to "string",
            JsonType.UUID to "string",
        )
        assertEquals("every IR type needs a draft-04 mapping", JsonType.ALL_TYPES, expected.keys)
        for ((type, schemaType) in expected) {
            assertEquals("'$type'", schemaType, JsonType.toSchemaType(type))
        }
    }

    /**
     * The aliases that reach [JsonType.toSchemaType] from rule scripts (`jsonTypeToSchemaType`)
     * and from the channels that legitimately share this draft-04 vocabulary (YApi).
     * They used to fall through to `"string"` — a wrong answer rather than a missing one.
     */
    @Test
    fun testToSchemaType_aliasesAndCase() {
        assertEquals("integer", JsonType.toSchemaType("integer"))
        assertEquals("integer", JsonType.toSchemaType("int32"))
        assertEquals("integer", JsonType.toSchemaType("int64"))
        assertEquals("integer", JsonType.toSchemaType("byte"))
        assertEquals("number", JsonType.toSchemaType("number"))
        assertEquals("number", JsonType.toSchemaType("decimal"))
        assertEquals("number", JsonType.toSchemaType("bigdecimal"))
        assertEquals("boolean", JsonType.toSchemaType("bool"))
        // Case-insensitive: script authors and converters spell these both ways.
        assertEquals("integer", JsonType.toSchemaType("INTEGER"))
        assertEquals("boolean", JsonType.toSchemaType("BOOLEAN"))
        // Unknown / blank fall back to string.
        assertEquals("string", JsonType.toSchemaType("totallyUnknownThing"))
        assertEquals("string", JsonType.toSchemaType(null))
        assertEquals("string", JsonType.toSchemaType("  "))
    }
}

/** Boxed FQN of a primitive kind, for the kind/boxed consistency check above. */
private fun PrimitiveKind.wrapperFqnForTest(): String = when (this) {
    PrimitiveKind.BOOLEAN -> "java.lang.Boolean"
    PrimitiveKind.BYTE -> "java.lang.Byte"
    PrimitiveKind.CHAR -> "java.lang.Character"
    PrimitiveKind.SHORT -> "java.lang.Short"
    PrimitiveKind.INT -> "java.lang.Integer"
    PrimitiveKind.LONG -> "java.lang.Long"
    PrimitiveKind.FLOAT -> "java.lang.Float"
    PrimitiveKind.DOUBLE -> "java.lang.Double"
    PrimitiveKind.VOID -> "java.lang.Void"
}
