package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.FieldOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.IrType

/**
 * Cycle-safe converter from EasyApi's [ObjectModel] to an OAS [SchemaObject]
 * tree.
 *
 * The converter is pure and project-independent — it accumulates named
 * schemas in an internal [components] map and exposes them via
 * [buildComponents]. Cycles in the input [ObjectModel.Object] graph are
 * detected via an immutable `seenIds` set passed by copy;
 * on revisit, the converter returns a `$ref` rather than recursing infinitely.
 *
 * Schema-name resolution:
 * - The name comes from the [nameHint] when the caller has one (it is the endpoint metadata's
 *   type name), otherwise from the model's own [ObjectModel.ref] — a nested class knows its own
 *   name even though the endpoint metadata only names the top-level body.
 * - When a name is available, the FIRST occurrence registers the full inlined schema in
 *   `components.schemas[<name>]` and the caller receives a `$ref` to it. Subsequent calls with
 *   the same name and matching shape return the existing `$ref`.
 * - When the shape differs, a `_2`, `_3`, … suffix is appended and a `warn` is logged.
 * - When neither is available (anonymous body), the schema is inlined at the call site; only on
 *   recursion (cycle) does it get a `GeneratedSchemaN` name and a `$ref`.
 */
class OpenApiSchemaConverter : IdeaLog {

    /** Mutable accumulator of named schemas → emitted as `components.schemas`. */
    private val components: LinkedHashMap<String, SchemaObject> = linkedMapOf()

    /** Tracks the resolved schema name (if any) for each visited [ObjectModel.Object] id. */
    private val idToName: MutableMap<Int, String> = mutableMapOf()

    /** Counter for `GeneratedSchemaN` synthesis. */
    private var generatedCounter: Int = 0

    /**
     * Converts [model] into a [SchemaObject]. Returns `null` if [model] is null.
     *
     * - [nameHint]: hint for the schema name (typically the simple class name
     *   from `responseType` / `bodyAttr`); package and generic args are
     *   stripped. When provided, the schema is registered under that name in
     *   `components.schemas` and the caller receives a `$ref`.
     * - [seenIds]: immutable-by-copy cycle-detection set. Each
     *   recursive call passes `LinkedHashSet(seenIds).apply { add(model.id) }`.
     */
    fun convert(
        model: ObjectModel?,
        nameHint: String? = null,
        seenIds: LinkedHashSet<Int> = linkedSetOf(),
    ): SchemaObject? {
        if (model == null) return null

        return when (model) {
            is ObjectModel.Object -> {
                // Cycle: the same Object.id has been visited along the current path.
                if (model.id in seenIds) {
                    val name = idToName[model.id] ?: allocateGeneratedName(model, seenIds)
                    return SchemaObject(`$ref` = "#/components/schemas/$name")
                }

                // Same model already registered under a name → reuse $ref.
                val existingName = idToName[model.id]
                if (existingName != null) {
                    return SchemaObject(`$ref` = "#/components/schemas/$existingName")
                }

                // First visit. The endpoint metadata names the top-level body; a nested class
                // names itself through `ref`. Without that fallback every nested class could only
                // be inlined into each place it appears — and a class that is *also* a top-level
                // type would be emitted twice, once inline and once as a component.
                //
                // This is the one place a name is genuinely required: an OAS object without one
                // can never become a `$ref`. Channels that only render the JSON word (Markdown's
                // type column, YApi's draft-04 `type: object`) do not consult `ref` at all.
                val simpleName = componentName(nameHint) ?: componentName(model.ref)
                if (simpleName != null) {
                    registerWithShapeCheck(model, simpleName, seenIds)
                } else {
                    // Anonymous first visit — inline at the call site.
                    buildObjectSchema(model, LinkedHashSet(seenIds).apply { add(model.id) })
                }
            }
            is ObjectModel.Single -> primitiveSchema(model)
            is ObjectModel.Array -> SchemaObject(
                type = "array",
                items = convert(model.item, nameHint = null, seenIds = seenIds),
            )
            is ObjectModel.MapModel -> SchemaObject(
                type = "object",
                additionalProperties = convert(model.valueType, nameHint = null, seenIds = seenIds),
            )
        }
    }

    /** Returns the accumulated named schemas as a [ComponentsObject]. */
    fun buildComponents(): ComponentsObject =
        ComponentsObject(schemas = components.takeIf { it.isNotEmpty() })

    // ─── Name resolution ──────────────────────────────────────────

    /**
     * Registers [model] under [simpleName], after checking the existing entry
     * (if any) for a shape match. On shape mismatch, finds the first free
     * `_N` suffix (N ≥ 2), logs a `warn`, and registers under the new name.
     *
     * Sets `idToName[model.id]` BEFORE building the schema so recursive
     * cycle-$refs pick up the correct name. If the name turns out to clash,
     * `idToName` is updated to the resolved `_N` name and the schema is
     * rebuilt so its internal $refs point to the correct name.
     */
    private fun registerWithShapeCheck(
        model: ObjectModel.Object,
        simpleName: String,
        seenIds: LinkedHashSet<Int>,
    ): SchemaObject {
        // Tentatively assign so cycle recursion picks up `simpleName`.
        idToName[model.id] = simpleName
        val nextSeen = LinkedHashSet(seenIds).apply { add(model.id) }
        val newSchema = buildObjectSchema(model, nextSeen)

        val existing = components[simpleName]
        if (existing == null) {
            // Name is free → register and return $ref.
            components[simpleName] = newSchema
            return SchemaObject(`$ref` = "#/components/schemas/$simpleName")
        }
        if (existing == newSchema) {
            // Same shape → reuse. Discard the freshly-built schema.
            return SchemaObject(`$ref` = "#/components/schemas/$simpleName")
        }

        // Shape mismatch → find first free `_N` slot.
        var n = 2
        while (true) {
            val candidate = "${simpleName}_$n"
            val existingN = components[candidate]
            if (existingN == null) {
                // Free slot — rebuild with the correct name so cycle $refs
                // point to `candidate`, not the tentative `simpleName`.
                idToName[model.id] = candidate
                val rebuilt = buildObjectSchema(model, nextSeen)
                components[candidate] = rebuilt
                LOG.warn("Schema name clash: '$simpleName' already used with a different shape; registering as '$candidate'")
                return SchemaObject(`$ref` = "#/components/schemas/$candidate")
            }
            if (existingN == newSchema) {
                // `_N` entry matches → reuse.
                idToName[model.id] = candidate
                return SchemaObject(`$ref` = "#/components/schemas/$candidate")
            }
            n++
        }
    }

    /**
     * Allocates a fresh `GeneratedSchemaN` name for an anonymous cyclic model.
     * Registers the inline schema under the new name so future
     * cycle-$refs resolve correctly.
     */
    private fun allocateGeneratedName(
        model: ObjectModel.Object,
        seenIds: LinkedHashSet<Int>,
    ): String {
        val name = "GeneratedSchema${++generatedCounter}"
        idToName[model.id] = name
        val nextSeen = LinkedHashSet(seenIds).apply { add(model.id) }
        components[name] = buildObjectSchema(model, nextSeen)
        return name
    }

    /**
     * Reduces a type spelling to a bare class name for use as a `components.schemas` key:
     * package prefix and generic arguments are stripped (`java.util.List<com.acme.User>` →
     * `List`, `com.acme.User` → `User`).
     *
     * Returns null for a null/blank input, in which case the caller leaves the node anonymous
     * rather than inventing a name. Serves both the [nameHint] path (endpoint metadata's type
     * name) and the [ObjectModel.ref] path (a nested class naming itself).
     */
    private fun componentName(name: String?): String? =
        name?.substringBefore('<')?.substringAfterLast('.')?.trim()?.takeIf { it.isNotBlank() }

    // ─── Inline schema construction ──────────────────────────────────────────

    /**
     * Builds an inline [SchemaObject] for [model]. Recurses into fields via
     * [convert] so cycle detection applies. The caller must have added
     * [model.id] to [seenIds] before calling.
     */
    private fun buildObjectSchema(
        model: ObjectModel.Object,
        seenIds: LinkedHashSet<Int>,
    ): SchemaObject {
        val properties = linkedMapOf<String, SchemaObject>()
        val required = mutableListOf<String>()
        for ((fieldName, fieldModel) in model.fields) {
            val fieldSchema = convert(fieldModel.model, nameHint = null, seenIds = seenIds)
                ?: continue
            properties[fieldName] = enrichWithFieldMetadata(fieldSchema, fieldModel)
            if (fieldModel.required) required.add(fieldName)
        }
        return SchemaObject(
            type = "object",
            properties = properties.takeIf { it.isNotEmpty() },
            required = required.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Applies field-level enrichments (comment → description,
     * options → enum + x-enumDescriptions, demo → example)
     * to [base]. Returns [base] unchanged if no enrichment applies; otherwise
     * returns a copy with the enriched fields populated.
     */
    private fun enrichWithFieldMetadata(base: SchemaObject, field: FieldModel): SchemaObject {
        if (field.comment == null && field.options.isNullOrEmpty() && field.demo == null) {
            return base
        }
        val description = field.comment ?: base.description
        val enumValues: List<Any>? = field.options
            ?.mapNotNull { it.value }
            ?.takeIf { it.isNotEmpty() }
            ?: base.enumValues
        val enumDescs: Map<String, String>? = field.options
            ?.mapNotNull { opt ->
                val v = opt.value ?: return@mapNotNull null
                opt.desc?.let { v.toString() to it }
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let { pairs -> linkedMapOf<String, String>().apply { putAll(pairs) } }
            ?: base.xEnumDescriptions
        val example = field.demo ?: base.example
        return base.copy(
            description = description,
            enumValues = enumValues,
            xEnumDescriptions = enumDescs,
            example = example,
        )
    }

    /** Converts an option [value] of arbitrary type to its wire form. */
    private fun FieldOption.wireValue(): Any? = value

    // ─── Primitive type table ─────────────────────────────────────

    /**
     * OAS 3.0.3 schema for an [ObjectModel.Single].
     *
     * The input vocabulary is **closed and matched by equality**: every construction site in
     * `main/` normalises `Single.type` through `IrType.fromJavaType` / `fromPsiType` /
     * `fromPrimitiveKind` / `resolveIrType`, so the table below enumerates exactly those
     * values. No substring matching — that would also fire on unrelated names (`Department`
     * contains `part`, `PageList` contains `list`).
     *
     * Why not delegate to the draft-04 `toSchemaType` (the rule-script API's table, in
     * `core/rule/parser`), which looks like the same table: that one is
     * the **JSON Schema draft-04** vocabulary the YApi channel emits (`$schema: draft-04`), and
     * draft-04 permits `type: "null"`. OAS 3.0.3 permits only six types and additionally
     * requires `items` whenever `type` is `array`. The two vocabularies are not
     * interchangeable — sharing one function let the draft-04-only `null` leak into OAS output.
     *
     * They are not independent either: for every reachable `Single.type` the `type` chosen
     * below is exactly the draft-04 `toSchemaType` of that same value, the sole exception being the
     * draft-04-only `"null"`, which must degrade. `OpenApiSchemaConverterTest` asserts that
     * relationship across the whole closed vocabulary, so a type added to one table cannot
     * silently miss the other.
     *
     * `Single("null")` — the placeholder for an unresolved / `void` type
     * ([ObjectModel.nullValue]) — is listed explicitly so the `else` below covers only
     * genuinely-unknown spellings. OAS 3.0.3 has no `null` type, so it degrades to `string`
     * just like the draft-04-only `null` the two vocabularies split on.
     *
     * The `else` branch is a total-function safety net, not a guess: a third-party
     * `classExporter` / `channel` extension can hand us a `Single` carrying an arbitrary string.
     * Such a value has no known OAS shape, so it degrades to `string` rather than being
     * pattern-matched into a confident but wrong answer — and logs at `info`, so the
     * degradation is diagnosable instead of silent.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun primitiveSchema(model: ObjectModel.Single): SchemaObject = when (model.type) {
        IrType.STRING -> SchemaObject(type = "string", format = formatForRef(model.ref))
        IrType.FILE, "file[]" -> SchemaObject(type = "string")
        IrType.SHORT, IrType.INT -> SchemaObject(type = "integer", format = "int32")
        IrType.LONG -> SchemaObject(type = "integer", format = "int64")
        IrType.FLOAT -> SchemaObject(type = "number", format = "float")
        IrType.DOUBLE -> SchemaObject(type = "number", format = "double")
        IrType.BOOLEAN -> SchemaObject(type = "boolean")
        // `Single("array")` means "a container whose element type was lost" — `fromJavaType`
        // returns it for unresolved list/set/collection spellings. OAS requires `items` whenever
        // `type` is `array`, so emit the widest legal element schema rather than omit it.
        IrType.ARRAY -> SchemaObject(type = "array", items = SchemaObject())
        IrType.OBJECT -> SchemaObject(type = "object")
        // Unresolved / `void` placeholder — no OAS `null` type exists, so render as `string`.
        "null" -> SchemaObject(type = "string")
        else -> {
            LOG.info("Unknown IrType '${model.type}' in OpenAPI schema; degrading to 'string'")
            SchemaObject(type = "string")
        }
    }

    /**
     * The OAS `format` a **string-typed** field with declaration [ref] carries, or `null`.
     *
     * The IR vocabulary no longer has date/uuid words — the declaration (`ObjectModel.ref`) is
     * the ground truth, and this table is where it becomes an annotation. It is consulted only
     * from the [IrType.STRING] branch of [primitiveSchema], so a project mapping a date type to
     * `long` (epoch timestamps, `json.rule.convert[X]=long`) is unaffected.
     *
     * Deliberately minimal: only the `java.time` types whose conventional serialization is an
     * ISO-8601 string, plus `java.util.UUID`. Excluded on purpose are `java.util.Date`,
     * `java.util.Calendar`, `java.sql.Timestamp` (extends `java.util.Date`) and
     * `java.time.Instant` — Jackson's default for those is an epoch *number*, so a
     * `format: date-time` would describe a wire shape they do not have.
     */
    private fun formatForRef(ref: String?): String? = when (ref) {
        "java.time.LocalDate" -> "date"
        "java.time.LocalDateTime", "java.time.OffsetDateTime", "java.time.ZonedDateTime" -> "date-time"
        // Not one of the six core OAS formats, but the one every toolchain emits for a UUID
        // (springdoc, swagger-ui's `uuid` display); OAS explicitly allows additional values.
        "java.util.UUID" -> "uuid"
        else -> null
    }
}
