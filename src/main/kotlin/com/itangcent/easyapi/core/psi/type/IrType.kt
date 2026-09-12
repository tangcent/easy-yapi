package com.itangcent.easyapi.core.psi.type

/**
 * The **IR type vocabulary** — the type words that survive from PSI to Doc.
 *
 * `ObjectModel.Single.type` holds one of these words (as a `String`; see `ObjectModel.ref` for
 * the declaration it was projected from). The name is deliberately **not** `JsonType`: JSON's own
 * types are a **proper subset** of this vocabulary. JSON has no `file`, and
 * [SHORT]/[LONG]/[FLOAT]/[DOUBLE] are finer-grained than JSON Schema's single
 * `integer`/`number`.
 *
 * Date-like and UUID types have **no word here**: on the wire they are a `string` (or a number,
 * per the project's serializer), the declaration survives in `ObjectModel.ref`, and a channel
 * that wants to say more — OpenAPI's `format: date-time`, a YApi mock expression — derives it
 * from the ref.
 *
 * ## The domain is three rings
 *
 * - **Core** — [ALL_TYPES], the 10 constants below. Closed, and every exit table is pinned to it
 *   by a test, so adding an 11th word breaks those tests until each table answers for it.
 * - **Named extensions** — `file[]` (a file *collection* spelled as one word) and `"null"` (the
 *   value of an unresolved or `void` type, see `ObjectModel.nullValue()`). Enumerated; exits
 *   handle them explicitly.
 * - **Pass-through** — protobuf type references (a failed deserialisation hands the FQN back
 *   verbatim, because that position renders a type *reference*) and any word a third-party
 *   channel or rule script invents. Open by design.
 *
 * [isValid] answers for the **core ring only** — it is not a guard for "can this field hold it".
 *
 * ## Nothing here renders
 *
 * Every consumer must translate, because no 1:1 relation exists between these words and any
 * target protocol's: `file` collapses into draft-04's single `string`. So each exit owns its own
 * table — draft-04 `toSchemaType` (with the rule-script API, `core/rule/parser`), OpenAPI
 * `OpenApiSchemaConverter.primitiveSchema`, example values [defaultValueForType], YApi mock
 * expressions `MockDataGenerator`. Those tables must **not** be merged: draft-04 accepts
 * `"null"` and needs no `items`, OAS 3.0.3 has neither.
 *
 * ## Usage
 * ```kotlin
 * val default = IrType.defaultValueForType("string")     // ""
 * val word    = IrType.fromJavaType("java.lang.String")  // "string"
 * val isNum   = IrType.isNumber("int")                   // true
 * ```
 */
object IrType {
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

    /**
     * The **core ring**: the closed set of words this pipeline owns. Every exit table is pinned
     * to it by a test (`Jsr223ScriptParserTest` for the draft-04 `toSchemaType`,
     * `ObjectModelValueConverterTypesTest` for [defaultValueForType],
     * `OpenApiSchemaConverterTest.REACHABLE_SINGLE_TYPES` for the OAS table), so a new member here
     * fails those tests until each table has answered for it.
     */
    val ALL_TYPES = setOf(
        STRING, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, ARRAY, OBJECT, FILE
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

    /**
     * Whether [type] is a member of the **core ring** ([ALL_TYPES]) — *not* whether a
     * `ObjectModel.Single` may hold it. `"null"` and `"file[]"` are legitimate values that this
     * returns `false` for (see the three rings on `IrType`), so it is a lookup, never an input
     * validator.
     */
    fun isValid(type: String?): Boolean = type != null && type in ALL_TYPES

    /**
     * The example value for a JSON type.
     *
     * The only table of its kind — `ObjectModelValueConverter` delegates its scalar cases here
     * so the two cannot disagree (they did: a date rendered as `""` in one path and `null` in
     * the other). Structural shapes (`object`/`array`) have no scalar default and are not
     * listed; `null` deliberately falls through to `null`.
     */
    fun defaultValueForType(type: String): Any? {
        return when (type) {
            STRING -> ""
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
     * consistent by `IrTypeTest.fromPrimitiveKindAgreesWithFromJavaType`.
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
     * `java.lang.String`, …), a file spelling, a bare container spelling a rule script may hand
     * us (`List<User>`, `MultipartFile[]`) or — as a legacy remnant — the retired
     * `date`/`datetime`/`uuid` words (see the shim branch below).
     *
     * There is deliberately **no table of non-basic types here**. Which FQNs are scalar-shaped
     * and what each becomes is declared by the active configuration
     * (`json.rule.convert[<fqn>]` — see `extensions/converts.config`) and applied by
     * `SpecialTypeHandler.resolveSpecialType` *before* this function is reached, so a project
     * can remap or drop the built-in answers without a code change. This function only owns the
     * closed [IrType] vocabulary.
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
            // `date` / `datetime` / `uuid` were IR words until 3.x; a rule or config may still
            // carry the old spelling (`json.rule.convert[X]=date`, `json.additional.field`,
            // `param.type`). Normalize to [STRING] here rather than letting the words fall
            // through to `object` below, which would expand a scalar into an empty object.
            // `SpecialTypeHandler.convertedScalarTarget` warns when it sees the legacy spelling
            // in a convert rule; this branch is the silent net for the remaining entry points.
            simple == "date" || simple == "datetime" || simple == "uuid" -> STRING

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
}
