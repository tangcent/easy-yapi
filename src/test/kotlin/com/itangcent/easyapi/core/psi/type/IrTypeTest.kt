package com.itangcent.easyapi.core.psi.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrTypeTest {
    
    @Test
    fun testConstants() {
        assertEquals("string", IrType.STRING)
        assertEquals("short", IrType.SHORT)
        assertEquals("int", IrType.INT)
        assertEquals("long", IrType.LONG)
        assertEquals("float", IrType.FLOAT)
        assertEquals("double", IrType.DOUBLE)
        assertEquals("boolean", IrType.BOOLEAN)
        assertEquals("array", IrType.ARRAY)
        assertEquals("object", IrType.OBJECT)
        assertEquals("file", IrType.FILE)
    }

    @Test
    fun testAllTypes() {
        assertTrue(IrType.ALL_TYPES.contains("string"))
        assertTrue(IrType.ALL_TYPES.contains("int"))
        assertTrue(IrType.ALL_TYPES.contains("long"))
        assertTrue(IrType.ALL_TYPES.contains("array"))
        assertTrue(IrType.ALL_TYPES.contains("object"))
        // Retired words: no longer members of the core ring.
        assertFalse(IrType.ALL_TYPES.contains("date"))
        assertFalse(IrType.ALL_TYPES.contains("datetime"))
        assertFalse(IrType.ALL_TYPES.contains("uuid"))
    }
    
    @Test
    fun testNumberTypes() {
        assertTrue(IrType.isNumber("short"))
        assertTrue(IrType.isNumber("int"))
        assertTrue(IrType.isNumber("long"))
        assertTrue(IrType.isNumber("float"))
        assertTrue(IrType.isNumber("double"))
        assertFalse(IrType.isNumber("string"))
        assertFalse(IrType.isNumber("array"))
        assertFalse(IrType.isNumber(null))
    }
    
    @Test
    fun testIsPrimitive() {
        assertTrue(IrType.isPrimitive("string"))
        assertTrue(IrType.isPrimitive("int"))
        assertTrue(IrType.isPrimitive("boolean"))
        assertFalse(IrType.isPrimitive("array"))
        assertFalse(IrType.isPrimitive("object"))
        assertFalse(IrType.isPrimitive(null))
    }
    
    @Test
    fun testIsValid() {
        assertTrue(IrType.isValid("string"))
        assertTrue(IrType.isValid("int"))
        assertTrue(IrType.isValid("array"))
        assertTrue(IrType.isValid("object"))
        assertFalse(IrType.isValid("integer"))
        assertFalse(IrType.isValid("list"))
        assertFalse(IrType.isValid(null))
    }
    
    @Test
    fun testFromJavaType_string() {
        assertEquals(IrType.STRING, IrType.fromJavaType("java.lang.String"))
        assertEquals(IrType.STRING, IrType.fromJavaType("String"))
        assertEquals(IrType.STRING, IrType.fromJavaType("char"))
        assertEquals(IrType.STRING, IrType.fromJavaType("java.lang.Character"))
    }
    
    @Test
    fun testFromJavaType_boolean() {
        assertEquals(IrType.BOOLEAN, IrType.fromJavaType("boolean"))
        assertEquals(IrType.BOOLEAN, IrType.fromJavaType("java.lang.Boolean"))
    }
    
    @Test
    fun testFromJavaType_integerTypes() {
        assertEquals(IrType.INT, IrType.fromJavaType("int"))
        assertEquals(IrType.INT, IrType.fromJavaType("java.lang.Integer"))
        assertEquals(IrType.SHORT, IrType.fromJavaType("short"))
        assertEquals(IrType.SHORT, IrType.fromJavaType("java.lang.Short"))
        assertEquals(IrType.LONG, IrType.fromJavaType("long"))
        assertEquals(IrType.LONG, IrType.fromJavaType("java.lang.Long"))
        assertEquals(IrType.LONG, IrType.fromJavaType("java.math.BigInteger"))
        assertEquals(IrType.INT, IrType.fromJavaType("byte"))
    }
    
    @Test
    fun testFromJavaType_floatTypes() {
        assertEquals(IrType.FLOAT, IrType.fromJavaType("float"))
        assertEquals(IrType.FLOAT, IrType.fromJavaType("java.lang.Float"))
        assertEquals(IrType.DOUBLE, IrType.fromJavaType("double"))
        assertEquals(IrType.DOUBLE, IrType.fromJavaType("java.lang.Double"))
        assertEquals(IrType.DOUBLE, IrType.fromJavaType("java.math.BigDecimal"))
    }
    
    /**
     * `date` / `datetime` / `uuid` were IR words until 3.x; a rule or config may still carry
     * the old spelling (`json.rule.convert[java.util.Date]=date`, `json.additional.field`,
     * `param.type`), and all of those strings funnel through here. Normalizing to
     * [IrType.STRING] keeps the wire shape; falling through to `object` would expand a scalar
     * into an empty object. The *source* spellings they used to be translated from
     * (`java.time.LocalDate`, `java.time.LocalDateTime`, `java.sql.Timestamp`, …) are
     * deliberately not recognised here: what those become is declared by the configuration
     * (`extensions/converts.config` maps them to `java.lang.String`) and applied by
     * `SpecialTypeHandler.resolveSpecialType` before this function is ever reached.
     */
    @Test
    fun testFromJavaType_retiredDateWordsNormalizeToString() {
        assertEquals(IrType.STRING, IrType.fromJavaType("date"))
        assertEquals(IrType.STRING, IrType.fromJavaType("datetime"))
        assertEquals(IrType.STRING, IrType.fromJavaType("uuid"))
        // The `Date` simple name reaches the same shim whichever package it is spelled with.
        assertEquals(IrType.STRING, IrType.fromJavaType("java.util.Date"))
        assertEquals(IrType.STRING, IrType.fromJavaType("java.sql.Date"))

        // Qualified java.time spellings are unknown classes here — an object, like any other.
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.time.LocalDate"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.time.LocalDateTime"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.sql.Timestamp"))
    }

    /**
     * A qualified name is a real class, so the loose substring fallbacks — which exist only for
     * the bare spellings a rule script hands us — must not fire on it. They used to, and
     * `java.time.Duration` (its package name contains "time") came out as `datetime` while
     * `com.acme.Department` (it contains "part") came out as `file`.
     */
    @Test
    fun testFromJavaType_qualifiedSpellingsAreNotSubstringMatched() {
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.time.Duration"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.time.Period"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("com.acme.Department"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("com.acme.Timeline"))

        // The unqualified spelling keeps the legacy fallback — that is the script-facing path.
        assertEquals(IrType.ARRAY, IrType.fromJavaType("UserList"))
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
        assertEquals(IrType.ARRAY, IrType.fromJavaType("List<Department>"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.List<com.acme.Department>"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("List<Partition>"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.List<Departments>"))

        // Real file elements, in every spelling a rule script may hand over.
        assertEquals("file[]", IrType.fromJavaType("List<MultipartFile>"))
        assertEquals("file[]", IrType.fromJavaType("java.util.List<org.springframework.web.multipart.MultipartFile>"))
        assertEquals("file[]", IrType.fromJavaType("List<Part>"))
        assertEquals("file[]", IrType.fromJavaType("java.util.List<javax.servlet.http.Part>"))
        assertEquals("file[]", IrType.fromJavaType("java.util.List<jakarta.servlet.http.Part>"))

        // These never contained "part", so they used to miss the file[] answer that the
        // resolver produces for the same element type; the token-level rule now matches it.
        assertEquals("file[]", IrType.fromJavaType("java.util.List<java.io.File>"))
        assertEquals("file[]", IrType.fromJavaType("java.util.List<java.nio.file.Path>"))
    }

    /**
     * The bare-spelling loose fallback uses the same token-level rule: `MultipartFile` and
     * `Part` are files, `Department` (it contains "part") is not — it falls through to
     * `object` like any other unknown bare class name.
     */
    @Test
    fun testFromJavaType_bareFileFallbackIsTokenLevel() {
        assertEquals(IrType.FILE, IrType.fromJavaType("MultipartFile"))
        assertEquals(IrType.FILE, IrType.fromJavaType("javax.servlet.http.Part"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("Department"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("Partition"))
    }

    /**
     * A UUID is a string on the wire and must never be expanded into the JDK class's
     * `mostSigBits`/`leastSigBits`. The shipped `extensions/converts.config` maps
     * `java.util.UUID` to `java.lang.String`, and the retired `uuid` word normalizes to the
     * same answer here.
     */
    @Test
    fun testFromJavaType_uuidTypes() {
        assertEquals(IrType.STRING, IrType.fromJavaType("uuid"))
        assertEquals(IrType.STRING, IrType.fromJavaType("java.util.UUID"))
    }

    @Test
    fun testDefaultValueForType_nonJsonScalars() {
        // `file` is not a JSON type but still needs an example value. It used to fall through
        // to `null`, which disagreed with `ObjectModelValueConverter.singleToValue` (see its
        // drift-guard test).
        assertEquals("(binary)", IrType.defaultValueForType(IrType.FILE))
    }

    @Test
    fun testFromJavaType_collectionTypes() {
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.List"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.ArrayList"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.Set"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.HashSet"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("java.util.Collection"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("List<String>"))
        assertEquals(IrType.ARRAY, IrType.fromJavaType("Set<Integer>"))
    }
    
    @Test
    fun testFromJavaType_mapTypes() {
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.util.Map"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("java.util.HashMap"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("Map<String, Object>"))
    }
    
    @Test
    fun testFromJavaType_fileTypes() {
        assertEquals(IrType.FILE, IrType.fromJavaType("org.springframework.web.multipart.MultipartFile"))
        assertEquals(IrType.FILE, IrType.fromJavaType("__file__"))
        assertEquals(IrType.FILE, IrType.fromJavaType("MultipartFile"))
        assertEquals(IrType.FILE, IrType.fromJavaType("javax.servlet.http.Part"))
        assertEquals(IrType.FILE, IrType.fromJavaType("jakarta.servlet.http.Part"))
        assertEquals(IrType.FILE, IrType.fromJavaType("java.io.File"))
        assertEquals(IrType.FILE, IrType.fromJavaType("java.nio.file.Path"))
        assertEquals(IrType.FILE, IrType.fromJavaType("org.springframework.core.io.Resource"))
    }
    
    @Test
    fun testFromJavaType_nullAndEmpty() {
        assertEquals(IrType.STRING, IrType.fromJavaType(null))
        assertEquals(IrType.STRING, IrType.fromJavaType(""))
        assertEquals(IrType.STRING, IrType.fromJavaType("   "))
    }
    
    @Test
    fun testFromJavaType_customClass() {
        assertEquals(IrType.OBJECT, IrType.fromJavaType("com.example.MyCustomClass"))
        assertEquals(IrType.OBJECT, IrType.fromJavaType("UserDTO"))
    }

    /**
     * [IrType.fromPrimitiveKind] is the `kind`-keyed view of [IrType.fromJavaType].
     * They are separate tables, so this pins them together: for every kind, the kind mapping
     * must equal the mapping of that kind's boxed FQN. Without it the two could drift and
     * `Integer` vs `int` would produce different JSON types depending on the resolution path.
     */
    @Test
    fun testFromPrimitiveKindAgreesWithFromJavaType() {
        for (kind in PrimitiveKind.entries) {
            val viaKind = IrType.fromPrimitiveKind(kind)
            if (kind == PrimitiveKind.VOID) {
                assertEquals("void has no value", null, viaKind)
                continue
            }
            assertEquals(
                "kind/boxed mismatch for $kind",
                viaKind,
                IrType.fromJavaType(kind.wrapperFqnForTest())
            )
        }
    }

    @Test
    fun testFromPrimitiveKind_values() {
        assertEquals(IrType.BOOLEAN, IrType.fromPrimitiveKind(PrimitiveKind.BOOLEAN))
        assertEquals(IrType.INT, IrType.fromPrimitiveKind(PrimitiveKind.BYTE))
        assertEquals(IrType.STRING, IrType.fromPrimitiveKind(PrimitiveKind.CHAR))
        assertEquals(IrType.SHORT, IrType.fromPrimitiveKind(PrimitiveKind.SHORT))
        assertEquals(IrType.INT, IrType.fromPrimitiveKind(PrimitiveKind.INT))
        assertEquals(IrType.LONG, IrType.fromPrimitiveKind(PrimitiveKind.LONG))
        assertEquals(IrType.FLOAT, IrType.fromPrimitiveKind(PrimitiveKind.FLOAT))
        assertEquals(IrType.DOUBLE, IrType.fromPrimitiveKind(PrimitiveKind.DOUBLE))
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
