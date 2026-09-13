package com.itangcent.easyapi.core.psi.type

/**
 * Constants and utilities for JSON type handling.
 *
 * Provides:
 * - Standard JSON type constants (string, int, boolean, etc.)
 * - Type validation utilities
 * - Java-to-JSON type conversion
 * - PSI-to-JSON type conversion
 *
 * ## Standard Types
 * - Primitive: string, short, int, long, float, double, boolean
 * - Composite: array, object
 * - Special: file, date, datetime, uuid
 *
 * ## Usage
 * ```kotlin
 * // Get default value for a type
 * val default = JsonType.defaultValueForType("string") // ""
 *
 * // Convert Java type to JSON type
 * val jsonType = JsonType.fromJavaType("java.lang.String") // "string"
 *
 * // Check if type is a number
 * val isNum = JsonType.isNumber("int") // true
 * ```
 */
object JsonType {
    const val STRING = "string"
    const val SHORT = "short"
    const val INT = "int"
    const val LONG = "long"
    const val FLOAT = "float"
    const val DOUBLE = "double"
    const val BOOLEAN = "boolean"
    const val ARRAY = "array"
    const val OBJECT = "object"
    const val FILE = "file"
    const val DATE = "date"
    const val DATETIME = "datetime"

    /**
     * A `java.util.UUID`. String-shaped on the wire, but kept distinct in the IR so that a
     * channel can annotate it — OpenAPI emits `{type: string, format: uuid}` — and so that the
     * value is not mistaken for a composite and expanded into `mostSigBits`/`leastSigBits`.
     */
    const val UUID = "uuid"

    val ALL_TYPES = setOf(
        STRING, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, ARRAY, OBJECT, FILE, DATE, DATETIME, UUID
    )

    val NUMBER_TYPES = setOf(SHORT, INT, LONG, FLOAT, DOUBLE)

    /** Container heads that map to [ARRAY] — `list`, `set`, `collection` and their common impls. */
    private val COLLECTION_SPELLINGS = setOf(
        "list", "arraylist", "linkedlist", "set", "hashset", "linkedhashset", "collection"
    )

    /** Container heads that map to [OBJECT] — `map` and its common impls. */
    private val MAP_SPELLINGS = setOf(
        "map", "hashmap", "linkedhashmap", "treemap"
    )

    fun isNumber(type: String?): Boolean = type != null && type in NUMBER_TYPES

    fun isPrimitive(type: String?): Boolean = type != null && type != ARRAY && type != OBJECT

    fun isValid(type: String?): Boolean = type != null && type in ALL_TYPES

    /**
     * The example value for a JSON type.
     *
     * The only table of its kind — `ObjectModelValueConverter` delegates its scalar cases here
     * so the two cannot disagree (they did: a date rendered as `""` in one path and `null` in
     * the other). Structural shapes (`object`/`array`) have no scalar default and are not
     * listed; `null` deliberately falls through to `null`.
     *
     * [DATE]/[DATETIME]/[UUID] are string-shaped on the wire, so they get the same `""` a
     * [STRING] gets rather than `null`: the exported example must be valid for the schema the
     * same field produces (`{"type": "string"}`).
     */
    fun defaultValueForType(type: String): Any? {
        return when (type) {
            STRING, DATE, DATETIME, UUID -> ""
            INT, SHORT, "int32" -> 0
            LONG, "int64" -> 0L
            FLOAT -> 0.0f
            DOUBLE -> 0.0
            BOOLEAN, "bool" -> false
            FILE -> "(binary)"
            "bytes" -> ""
            else -> null
        }
    }

    /**
     * Maps a primitive [PrimitiveKind] to its JSON type, or `null` for [PrimitiveKind.VOID].
     *
     * `null` means "no value", not "unknown": callers render `void` as a null value rather
     * than a JSON type. This is the `kind`-keyed view of [fromJavaType] — the two are kept
     * consistent by `JsonTypeTest.fromPrimitiveKindAgreesWithFromJavaType`.
     */
    fun fromPrimitiveKind(kind: PrimitiveKind): String? = when (kind) {
        PrimitiveKind.BOOLEAN -> BOOLEAN
        PrimitiveKind.BYTE -> INT
        PrimitiveKind.CHAR -> STRING
        PrimitiveKind.SHORT -> SHORT
        PrimitiveKind.INT -> INT
        PrimitiveKind.LONG -> LONG
        PrimitiveKind.FLOAT -> FLOAT
        PrimitiveKind.DOUBLE -> DOUBLE
        PrimitiveKind.VOID -> null
    }

    /**
     * Maps a type spelling to its JSON type.
     *
     * The input is either the JSON-native/primitive vocabulary ([STRING], `long`,
     * `java.lang.String`, …), an IR spelling a rule opted into ([date], [DATETIME], [UUID],
     * [FILE]) or a bare container spelling a rule script may hand us (`List<User>`,
     * `MultipartFile[]`).
     *
     * There is deliberately **no table of non-basic types here**. Which FQNs are scalar-shaped
     * and what each becomes is declared by the active configuration
     * (`json.rule.convert[<fqn>]` — see `extensions/converts.config`) and applied by
     * `SpecialTypeHandler.resolveSpecialType` *before* this function is reached, so a project
     * can remap or drop the built-in answers without a code change. This function only owns the
     * closed [JsonType] vocabulary — that is why [date]/[DATETIME]/[UUID] remain valid inputs:
     * a rule may explicitly opt a type into them.
     */
    fun fromJavaType(javaType: String?): String {
        if (javaType.isNullOrBlank()) return STRING

        val normalized = javaType.lowercase().removePrefix("java.lang.").removePrefix("java.util.")
        // The head simple name: `java.util.Date`, `java.sql.Date` and a bare `Date` are the same
        // answer here, and `kotlin.collections.List<User>` still reads as a container.
        val simple = normalized.substringBefore('<').substringAfterLast('.')

        return when {
            simple == "string" || simple == "char" || simple == "character" -> STRING
            simple == "boolean" -> BOOLEAN
            simple == "byte" -> INT
            simple == "short" -> SHORT
            simple == "int" || simple == "integer" -> INT
            simple == "long" -> LONG
            simple == "float" -> FLOAT
            simple == "double" -> DOUBLE
            simple == "biginteger" -> LONG
            simple == "bigdecimal" -> DOUBLE
            // `date` / `datetime` / `uuid` are the IR spellings, kept as inputs so a rule can
            // opt a type into them (`json.rule.convert[X]=date`). No *source* spelling is
            // translated into them any more: what `java.time.LocalDate`,
            // `java.sql.Timestamp`, … become is declared by the configuration
            // (`extensions/converts.config`), and by `SpecialTypeHandler.resolveSpecialType`
            // before this function is reached.
            simple == "date" -> DATE
            simple == "datetime" -> DATETIME
            simple == "uuid" -> UUID

            normalized == "file" || normalized == "__file__" || normalized == "multipartfile" ||
                    normalized == "org.springframework.web.multipart.multipartfile" ||
                    normalized == "org.springframework.web.multipart.commons.commonsmultipartfile" ||
                    normalized == "javax.servlet.http.part" ||
                    normalized == "jakarta.servlet.http.part" ||
                    normalized == "java.io.file" ||
                    normalized == "java.nio.file.path" ||
                    normalized == "org.springframework.core.io.resource" -> FILE

            normalized.contains("multipartfile[]") ||
                    normalized.contains("multipartfile>") && normalized.contains("[]") ||
                    normalized.contains("part[]") ||
                    normalized.contains("part>") && normalized.contains("[]") -> "file[]"

            // The head names a container whether or not the spelling is fully qualified
            // (`java.util.List`, `kotlin.collections.List<User>`). Whether the declaration
            // carries files is a token-level question — `List<Part>` is `file[]`, while
            // `List<Department>` is a plain array (a substring check used to make any
            // element whose name *contains* "part" a file). `normalized` is lowercased,
            // which is why [SpecialTypeHandler.mentionsFileType] is case-insensitive.
            simple in COLLECTION_SPELLINGS -> {
                if (SpecialTypeHandler.mentionsFileType(normalized)) {
                    "file[]"
                } else {
                    ARRAY
                }
            }

            simple in MAP_SPELLINGS -> OBJECT

            // ─── Loose fallbacks — unqualified spellings only ───────────────────
            //
            // Everything below matches on substrings, which is only meaningful for the bare
            // spellings a rule script may hand us (`List<MultipartFile>`, `UserList`). A
            // qualified name is a real class, and substring-matching one produced wrong
            // answers: `com.acme.Department` → `file` (it contains "part"),
            // `com.acme.Timeline` and `java.time.Duration` → `datetime` (they contain
            // "time"). An unknown qualified class is an object, full stop.
            normalized.contains('.') -> OBJECT

            // Same token-level rule as the container branch: `MultipartFile`/`Part` are files,
            // `Department` (it contains "part") is not — it falls through to `object`.
            SpecialTypeHandler.mentionsFileType(normalized) -> FILE
            normalized.contains("list") || normalized.contains("set") ||
                    normalized.contains("collection") || normalized.contains("[]") -> ARRAY

            normalized.contains("map") -> OBJECT
            normalized.contains("int") || normalized.contains("integer") -> INT
            normalized.contains("long") -> LONG
            normalized.contains("float") -> FLOAT
            normalized.contains("double") -> DOUBLE
            normalized.contains("boolean") -> BOOLEAN
            normalized.contains("short") -> SHORT
            normalized.contains("byte") -> INT
            normalized.contains("date") || normalized.contains("time") -> DATETIME
            else -> OBJECT
        }
    }

    fun fromPsiType(psiType: com.intellij.psi.PsiType?): String {
        if (psiType == null) return STRING

        val canonical = psiType.canonicalText

        if (psiType is com.intellij.psi.PsiPrimitiveType) {
            return when (canonical) {
                "boolean" -> BOOLEAN
                "byte" -> INT
                "short" -> SHORT
                "int" -> INT
                "long" -> LONG
                "float" -> FLOAT
                "double" -> DOUBLE
                "char" -> STRING
                "void" -> STRING
                else -> STRING
            }
        }

        if (psiType is com.intellij.psi.PsiArrayType) {
            val componentCanonical = psiType.componentType.canonicalText
            if (SpecialTypeHandler.isFileTypeCanonical(componentCanonical)) return "file[]"
            return ARRAY
        }

        return fromJavaType(canonical)
    }

    /**
     * Maps a JSON type name (as used by [fromJavaType]/[fromPsiType]) to its
     * JSON Schema data type. Exposed to rule scripts via the shared
     * `ScriptHelper.jsonTypeToSchemaType` helper.
     *
     * This table is the **JSON Schema draft-04** vocabulary — the dialect the YApi channel
     * declares (`JsonSchemaBuilder`: `$schema: draft-04`), in which `type: "null"` is legal.
     * It is deliberately **not** shared with OpenAPI: OAS 3.0.3 allows only six `type` values
     * (`null` is not among them) and requires `items` whenever `type` is `array`, so
     * `OpenApiSchemaConverter` keeps its own explicit table. Do not collapse the two — a common
     * table silently leaks draft-04-only spellings into OAS documents.
     *
     * The two are not *independent* either. For every reachable [ObjectModel.Single.type] the
     * OAS table's `type` equals this function's answer, the single exception being the
     * draft-04-only `"null"`, which OAS must degrade. `OpenApiSchemaConverterTest` asserts that
     * over the whole closed vocabulary, so a type added to one table cannot silently miss the
     * other.
     *
     * Matching is case-insensitive and also accepts the aliases that reach this function from
     * rule scripts: the Java primitive spellings (`byte`, `decimal`, `bigdecimal`, `bool`) and
     * the JSON Schema spellings themselves (`integer`, `number`, `int32`, `int64`). Without
     * them those names fell through to `"string"` — a wrong answer rather than a missing one.
     */
    fun toSchemaType(type: String?): String {
        if (type.isNullOrBlank()) return STRING
        return when (type.lowercase()) {
            STRING, DATE, DATETIME, UUID, FILE, "text" -> "string"
            SHORT, INT, LONG, "integer", "int32", "int64", "byte" -> "integer"
            FLOAT, DOUBLE, "number", "decimal", "bigdecimal" -> "number"
            BOOLEAN, "bool" -> "boolean"
            ARRAY -> "array"
            OBJECT -> "object"
            "null" -> "null"
            else -> "string"
        }
    }

    /**
     * Human-facing type name: what a Markdown/HTML document prints in its type column.
     *
     * The IR vocabulary is intentionally richer than JSON — `date` is what lets
     * `OpenApiSchemaConverter` emit `format: date-time`. Rewriting is needed only where the IR
     * name does **not** tell the reader the wire shape: `date`/`datetime` could be a string, an
     * epoch number or an object, and they disagree with the `string` the YApi schema reports
     * for the same field. `long`/`short`/`file`/`uuid` do tell the reader (a number, an upload,
     * a string), so they print verbatim.
     *
     * This is deliberately *not* [toSchemaType]: that would also collapse `long` and `short`
     * to `integer` and `file` to `string`.
     */
    fun toDisplayType(type: String?): String = when (type) {
        DATE, DATETIME -> STRING
        null -> STRING
        else -> type
    }
}
