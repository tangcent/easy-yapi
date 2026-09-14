package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.FieldOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.IrType
import com.itangcent.easyapi.core.rule.parser.toSchemaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [OpenApiSchemaConverter] — the cycle-safe `ObjectModel → OAS
 * Schema Object` converter.
 *
 * Constructs `ObjectModel` instances directly (no PSI) — the model is POJO.
 */
class OpenApiSchemaConverterTest {

    // ─── primitive type table ──────────────────────────────────────
    //
    // `ObjectModel.Single.type` has a CLOSED vocabulary: every construction site in `main/`
    // normalises through `IrType.fromJavaType` / `fromPsiType` / `fromPrimitiveKind` /
    // `resolveIrType`, so only the values in [REACHABLE_SINGLE_TYPES] can ever reach
    // `primitiveSchema`. These tests enumerate that domain — not the Java type spellings the
    // old substring matcher tolerated (`byte[]`, `LocalDateTime`, …, none of which production
    // can produce) — and assert every one lands on a legal OAS 3.0.3 schema.

    @Test
    fun reachableSingleTypesMapToTheExpectedOasSchema() {
        // Drift guard: adding a `IrType` constant must force a decision about its OAS shape.
        val unmapped = REACHABLE_SINGLE_TYPES - EXPECTED_PRIMITIVE_SCHEMAS.keys
        assertTrue("IrType values with no OAS mapping: $unmapped", unmapped.isEmpty())

        for (type in REACHABLE_SINGLE_TYPES) {
            val expected = EXPECTED_PRIMITIVE_SCHEMAS.getValue(type)
            val schema = converter().convert(ObjectModel.single(type))!!

            assertEquals("type for '$type'", expected.first, schema.type)
            assertEquals("format for '$type'", expected.second, schema.format)
            assertTrue(
                "'$type' produced type '${schema.type}', not a legal OAS 3.0.3 type",
                schema.type in OAS_SCHEMA_TYPES,
            )
        }
    }

    /**
     * The OAS table and the draft-04 `toSchemaType` — the table the YApi channel emits —
     * are deliberately separate, because OAS denies `type: "null"` and needs `items` on an
     * `array`. This pins the *relationship* between them: the same `type` for every reachable
     * value, with the draft-04-only `"null"` as the single, documented divergence.
     *
     * The failure mode guarded here is the silent one — someone gives `float` a `type` of
     * `integer` on one side, or adds an IR type to one table only, and YApi and OpenAPI start
     * describing the same field differently.
     */
    @Test
    fun oasTypeAgreesWithTheDraft04VocabularyExceptForNull() {
        for (type in REACHABLE_SINGLE_TYPES) {
            val oas = converter().convert(ObjectModel.single(type))!!.type
            if (type == "null") {
                assertEquals("draft-04's `null` is illegal in OAS and must degrade", "string", oas)
                continue
            }
            assertEquals(
                "'$type': the two dialect tables must agree on `type`",
                toSchemaType(type),
                oas,
            )
        }
    }

    @Test
    fun singleArrayCarriesItemsBecauseOasRequiresThemForTypeArray() {
        // OAS 3.0.3: "items MUST be present if the type is array".
        // `Single("array")` means "a container whose element type was lost" — `fromJavaType`
        // returns it for unresolved list/set/collection spellings — so the widest legal
        // element schema is the honest choice.
        val schema = converter().convert(ObjectModel.single(IrType.ARRAY))!!
        assertEquals("array", schema.type)
        assertNotNull("type=array is invalid without items", schema.items)
    }

    @Test
    fun singleNullDoesNotLeakTheDraft04OnlyNullTypeIntoOas() {
        // `"null"` is a legal JSON Schema draft-04 type — and draft-04 is what the YApi channel
        // declares in its `$schema` — but OAS 3.0.3 limits `type` to six values. It must
        // degrade rather than emit `{"type": "null"}`.
        val schema = converter().convert(ObjectModel.single("null"))!!
        assertEquals("string", schema.type)
        assertNull(schema.format)
    }

    @Test
    fun unknownSingleTypeDegradesToStringInsteadOfBeingGuessed() {
        // Total-function safety net: a third-party classExporter/channel extension can hand us a
        // `Single` carrying an arbitrary string. It has no known OAS shape, so it must degrade to
        // `string` rather than be pattern-matched into a confident-but-wrong answer.
        val schema = converter().convert(ObjectModel.single("totallyUnknownThing"))!!
        assertEquals("string", schema.type)
        assertNull(schema.format)
    }

    // ─── format derived from ref (the retired date/uuid words live here now) ──

    /**
     * The IR vocabulary has no date/uuid words — the declaration (`ref`) is the ground truth,
     * and the OAS `format` annotation is derived from it. Only the `java.time` types whose
     * conventional serialization is an ISO-8601 string are in the table, plus `java.util.UUID`.
     */
    @Test
    fun stringFieldsDeriveTheOasFormatFromTheDeclaredRef() {
        fun formatOf(ref: String?) = converter().convert(ObjectModel.single(IrType.STRING, ref))!!.format
        assertEquals("date", formatOf("java.time.LocalDate"))
        assertEquals("date-time", formatOf("java.time.LocalDateTime"))
        assertEquals("date-time", formatOf("java.time.OffsetDateTime"))
        assertEquals("date-time", formatOf("java.time.ZonedDateTime"))
        assertEquals("uuid", formatOf("java.util.UUID"))
    }

    /**
     * Jackson's default for these is an epoch *number*, so a `format: date-time` would describe
     * a wire shape they do not have — they carry no format even when converted to a string.
     */
    @Test
    fun epochSerializingDeclarationsCarryNoFormat() {
        fun formatOf(ref: String?) = converter().convert(ObjectModel.single(IrType.STRING, ref))!!.format
        assertNull(formatOf("java.util.Date"))
        assertNull(formatOf("java.util.Calendar"))
        assertNull(formatOf("java.sql.Timestamp"))
        assertNull(formatOf("java.time.Instant"))
    }

    /**
     * The ref table answers only when the word is `string`: a project mapping a date type to
     * `long` (epoch timestamps, `json.rule.convert[X]=long`) is unaffected by the derivation.
     */
    @Test
    fun formatDerivationRequiresTheStringWord() {
        val schema = converter().convert(ObjectModel.single(IrType.LONG, "java.time.LocalDateTime"))!!
        assertEquals("integer", schema.type)
        assertEquals("int64", schema.format)
    }

    @Test
    fun unknownAndMissingRefsCarryNoFormat() {
        fun formatOf(ref: String?) = converter().convert(ObjectModel.single(IrType.STRING, ref))!!.format
        assertNull(formatOf("com.acme.MyStringLike"))
        assertNull(formatOf(null))
    }

    // ─── Object with fields + required ────────────────────────────

    @Test
    fun objectMapsToObjectTypeWithPropertiesAndRequired() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "id" to FieldModel(model = ObjectModel.single("long"), required = true),
                "name" to FieldModel(model = ObjectModel.single("string")),
            )
        )
        val schema = converter().convert(model)!!
        assertEquals("object", schema.type)
        val properties = schema.properties
        assertNotNull(properties)
        assertTrue(properties!!.containsKey("id"))
        assertTrue(properties.containsKey("name"))
        assertEquals(listOf("id"), schema.required)
        // Properties use LinkedHashMap to preserve insertion order
        assertEquals(listOf("id", "name"), properties.keys.toList())
    }

    // ─── Array ──────────────────────────────────────────────────────

    @Test
    fun arrayMapsToArrayTypeWithItemsDerivedFromElementModel() {
        val model = ObjectModel.Array(ObjectModel.single("int"))
        val schema = converter().convert(model)!!
        assertEquals("array", schema.type)
        val items = schema.items
        assertNotNull(items)
        assertEquals("integer", items!!.type)
        assertEquals("int32", items.format)
    }

    // ─── MapModel ──────────────────────────────────────────────────

    @Test
    fun mapModelMapsToObjectTypeWithAdditionalPropertiesFromValueType() {
        val model = ObjectModel.MapModel(
            keyType = ObjectModel.single("string"),
            valueType = ObjectModel.single("int"),
        )
        val schema = converter().convert(model)!!
        assertEquals("object", schema.type)
        val additional = schema.additionalProperties
        assertNotNull(additional)
        assertEquals("integer", additional!!.type)
        assertEquals("int32", additional.format)
    }

    // ─── field comment → description ───────────────────────────────

    @Test
    fun fieldCommentPopulatesSchemaDescription() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "name" to FieldModel(
                    model = ObjectModel.single("string"),
                    comment = "User's display name",
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["name"]!!
        assertEquals("User's display name", fieldSchema.description)
    }

    // ─── field options → enum + x-enumDescriptions ─────────────────

    @Test
    fun fieldOptionsWithoutDescriptionsPopulateEnumValuesOnly() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "status" to FieldModel(
                    model = ObjectModel.single("string"),
                    options = listOf(
                        FieldOption(value = "ACTIVE"),
                        FieldOption(value = "INACTIVE"),
                    ),
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["status"]!!
        assertEquals(listOf("ACTIVE", "INACTIVE"), fieldSchema.enumValues)
        assertNull(fieldSchema.xEnumDescriptions)
    }

    @Test
    fun fieldOptionsWithDescriptionsPopulateEnumValuesAndXEnumDescriptions() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "status" to FieldModel(
                    model = ObjectModel.single("string"),
                    options = listOf(
                        FieldOption(value = "ACTIVE", desc = "Active user"),
                        FieldOption(value = "INACTIVE", desc = "Inactive user"),
                    ),
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["status"]!!
        assertEquals(listOf("ACTIVE", "INACTIVE"), fieldSchema.enumValues)
        val enumDescs = fieldSchema.xEnumDescriptions
        assertNotNull(enumDescs)
        assertEquals("Active user", enumDescs!!["ACTIVE"])
        assertEquals("Inactive user", enumDescs["INACTIVE"])
    }

    // ─── field demo → example ──────────────────────────────────────

    @Test
    fun fieldDemoPopulatesSchemaExample() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "name" to FieldModel(
                    model = ObjectModel.single("string"),
                    demo = "Alice",
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["name"]!!
        assertEquals("Alice", fieldSchema.example)
    }

    // ─── cycle detection ───────────────────────────────

    @Test
    fun selfReferentialNodeReturnsDollarRefAndRegistersInComponents() {
        val node = buildSelfReferentialNode()
        val converter = converter()

        val schema = converter.convert(node, nameHint = "Node")!!

        // First use with nameHint returns a $ref (not an inline object).
        assertEquals("#/components/schemas/Node", schema.`$ref`)

        // components.schemas has Node registered.
        val components = converter.buildComponents()
        val schemas = components.schemas
        assertNotNull(schemas)
        assertTrue("Node" in schemas!!)
        val nodeSchema = schemas["Node"]!!
        assertEquals("object", nodeSchema.type)
        // The `next` field of Node is a $ref back to Node (cycle broken).
        val nextFieldSchema = nodeSchema.properties!!["next"]!!
        assertEquals("#/components/schemas/Node", nextFieldSchema.`$ref`)
    }

    @Test
    fun cycleDetectionTerminatesInFiniteTimeForDeepRecursion() {
        // Construct Node → next → Node → next → ... (cycle of length 1).
        // convert() must terminate, not StackOverflow.
        val node = buildSelfReferentialNode()
        val schema = converter().convert(node, nameHint = "Node")
        assertNotNull(schema)
    }

    // ─── schema-name resolution ────────────────────

    @Test
    fun nameHintPresentFirstUseRegistersAndReturnsDollarRef() {
        val model = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val converter = converter()

        val schema = converter.convert(model, nameHint = "User")!!

        assertEquals("#/components/schemas/User", schema.`$ref`)
        val components = converter.buildComponents()
        assertTrue(components.schemas!!.containsKey("User"))
    }

    @Test
    fun nestedObjectNamesItselfThroughItsOwnRef() {
        // The endpoint metadata names the top-level body only, so a nested class is converted
        // with no `nameHint` at all. Before the converter could fall back to `ref`, such a class
        // had no name and was therefore inlined into every place it appeared — and if it was
        // *also* a top-level type it ended up in the document twice, once inline and once as a
        // component under the other name.
        val user = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long"))),
            ref = "com.acme.model.User",
        )
        val envelope = ObjectModel.Object(
            linkedMapOf("data" to FieldModel(model = user)),
            ref = "com.acme.model.Result",
        )
        val converter = converter()

        val schema = converter.convert(envelope, nameHint = "Result")!!

        // The top-level body still takes the caller's name...
        assertEquals("#/components/schemas/Result", schema.`$ref`)
        val schemas = converter.buildComponents().schemas!!
        // ...but the nested class now registers under its own *simple* name (package stripped),
        // and the field points at it instead of inlining a second copy.
        assertTrue(schemas.containsKey("User"))
        assertEquals(
            "#/components/schemas/User",
            schemas["Result"]!!.properties!!["data"]!!.`$ref`
        )

        // Referencing the same class at top level reuses the component the nested visit created
        // rather than registering a duplicate.
        val topLevel = converter.convert(user, nameHint = "User")!!
        assertEquals("#/components/schemas/User", topLevel.`$ref`)
        assertEquals(setOf("Result", "User"), schemas.keys)
    }

    @Test
    fun anObjectWithNoRefAndNoNameHintIsStillInlined() {
        // The fallback is not a licence to invent a name: nothing declared this shape, so it
        // stays anonymous.
        val anonymous = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )

        val schema = converter().convert(anonymous, nameHint = null)!!

        assertEquals("object", schema.type)
        assertNull(schema.`$ref`)
    }

    @Test
    fun nameHintPresentSecondUseSameShapeReturnsExistingDollarRefWithoutDuplicate() {
        val model1 = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val model2 = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val converter = converter()

        val first = converter.convert(model1, nameHint = "User")!!
        val second = converter.convert(model2, nameHint = "User")!!

        // Both calls return the same $ref — no _N suffix on second use.
        assertEquals("#/components/schemas/User", first.`$ref`)
        assertEquals("#/components/schemas/User", second.`$ref`)

        // Only one entry registered.
        val schemas = converter.buildComponents().schemas!!
        assertEquals(1, schemas.size)
        assertTrue(schemas.containsKey("User"))
    }

    @Test
    fun nameHintPresentSecondUseDifferentShapeAppendsUnderscoreTwoSuffix() {
        val model1 = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val model2 = ObjectModel.Object(
            linkedMapOf("name" to FieldModel(model = ObjectModel.single("string")))
        )
        val converter = converter()

        val first = converter.convert(model1, nameHint = "User")!!
        val second = converter.convert(model2, nameHint = "User")!!

        assertEquals("#/components/schemas/User", first.`$ref`)
        // Different shape → _2 suffix (and a warn is logged).
        assertEquals("#/components/schemas/User_2", second.`$ref`)

        val schemas = converter.buildComponents().schemas!!
        assertTrue(schemas.containsKey("User"))
        assertTrue(schemas.containsKey("User_2"))
    }

    @Test
    fun noNameHintCyclicAnonymousModelUsesGeneratedSchemaN() {
        val anon1 = buildSelfReferentialNode()
        val anon2 = buildSelfReferentialNode()
        val converter = converter()

        val first = converter.convert(anon1, nameHint = null)!!
        val second = converter.convert(anon2, nameHint = null)!!

        // Anonymous first-use returns an inline schema (type=object), not a $ref.
        assertEquals("object", first.type)
        assertNull(first.`$ref`)
        // The `next` field is a $ref to GeneratedSchema1.
        val firstNext = first.properties!!["next"]!!
        assertEquals("#/components/schemas/GeneratedSchema1", firstNext.`$ref`)

        // Second anonymous cyclic model → GeneratedSchema2.
        assertEquals("object", second.type)
        val secondNext = second.properties!!["next"]!!
        assertEquals("#/components/schemas/GeneratedSchema2", secondNext.`$ref`)

        val schemas = converter.buildComponents().schemas!!
        assertTrue(schemas.containsKey("GeneratedSchema1"))
        assertTrue(schemas.containsKey("GeneratedSchema2"))
    }

    @Test
    fun nameHintStripsPackageAndGenerics() {
        // `com.acme.Result<User>` → `Result`
        val model = ObjectModel.Object(
            linkedMapOf("data" to FieldModel(model = ObjectModel.single("string")))
        )
        val converter = converter()

        val schema = converter.convert(model, nameHint = "com.acme.Result<User>")!!

        assertEquals("#/components/schemas/Result", schema.`$ref`)
        val schemas = converter.buildComponents().schemas!!
        assertTrue(schemas.containsKey("Result"))
        assertFalse(schemas.containsKey("com.acme.Result<User>"))
    }

    // ─── Misc: null model ───────────────────────────────────────────────────

    @Test
    fun convertNullModelReturnsNull() {
        assertNull(converter().convert(null))
    }

    @Test
    fun buildComponentsReturnsEmptyObjectWhenNothingRegistered() {
        val components = converter().buildComponents()
        // No schemas registered.
        assertNull(components.schemas)
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun converter(): OpenApiSchemaConverter = OpenApiSchemaConverter()

    /**
     * Builds a self-referential `Node { next: Node }` model. The trick is to
     * construct an `ObjectModel.Object` with an empty mutable `fields` map,
     * then mutate the map to add the self-reference after construction. The
     * `Object.id` is auto-assigned at construction and stays stable, so the
     * cycle detection (which uses `Object.id`) sees the same id when we
     * recurse into the `next` field.
     */
    private fun buildSelfReferentialNode(): ObjectModel.Object {
        val fields = linkedMapOf<String, FieldModel>()
        val node = ObjectModel.Object(fields = fields)
        fields["next"] = FieldModel(model = node)
        return node
    }

    private companion object {
        /** The six `type` values OAS 3.0.3 allows — `null` is deliberately absent. */
        private val OAS_SCHEMA_TYPES = setOf(
            "string", "number", "integer", "boolean", "array", "object",
        )

        /**
         * Every value [ObjectModel.Single.type] can actually hold: `IrType`'s own vocabulary
         * plus the two `fromJavaType` spellings that sit outside it — the file-array marker
         * `file[]` and the `null` placeholder produced by `ObjectModel.nullValue()`.
         */
        private val REACHABLE_SINGLE_TYPES = IrType.ALL_TYPES + setOf("file[]", "null")

        /** Expected OAS `(type, format)` for each reachable value. */
        private val EXPECTED_PRIMITIVE_SCHEMAS: Map<String, Pair<String, String?>> = mapOf(
            IrType.STRING to ("string" to null),
            IrType.FILE to ("string" to null),
            // Lossy but unchanged: the model has already dropped the element type here, and
            // `type: array` would need `items` to say anything more precise.
            "file[]" to ("string" to null),
            IrType.SHORT to ("integer" to "int32"),
            IrType.INT to ("integer" to "int32"),
            IrType.LONG to ("integer" to "int64"),
            IrType.FLOAT to ("number" to "float"),
            IrType.DOUBLE to ("number" to "double"),
            IrType.BOOLEAN to ("boolean" to null),
            IrType.ARRAY to ("array" to null),
            IrType.OBJECT to ("object" to null),
            // Legal in JSON Schema draft-04, illegal in OAS 3.0.3 → must degrade.
            "null" to ("string" to null),
        )
    }
}
