package com.itangcent.easyapi.core.psi.type

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.rule.RuleKeys

/**
 * Handler for special types that require custom processing.
 *
 * Handles:
 * - File upload types (MultipartFile, Part, File, etc.)
 * - Primitive wrapper types (Integer, Long, Boolean, etc.)
 * - **Configured** scalar types — non-basic types a rule maps to a scalar
 *
 * Provides:
 * - Type detection utilities
 * - Type resolution for special types
 * - Default value generation
 *
 * ## File Types
 * Treated as binary upload:
 * - `org.springframework.web.multipart.MultipartFile`
 * - `javax.servlet.http.Part`
 * - `java.io.File`
 * - `java.nio.file.Path`
 *
 * ## Configured scalars
 * Non-basic types (`java.util.Date`, `java.time.Duration`, `java.util.UUID`, …)
 * are **not** listed in this file. They are declared by the active configuration
 * as convert rules — see
 * `src/main/resources/extensions/converts.config` — and
 * [convertedScalarTarget] reads that declaration, so a project can turn the
 * extension off and map these types its own way (a custom serializer, `long`,
 * `date`, …). See [resolveSpecialType] for why the resolver must know about
 * them at all.
 *
 * @see ResolvedType for the type system
 * @see JsonType for standard JSON types
 */
object SpecialTypeHandler {

    private val FILE_TYPES = setOf(
        "org.springframework.web.multipart.MultipartFile",
        "javax.servlet.http.Part",
        "jakarta.servlet.http.Part",
        "java.io.File",
        "java.nio.file.Path",
        "org.springframework.core.io.Resource",
        "org.springframework.web.multipart.commons.CommonsMultipartFile"
    )

    /**
     * JSON-native scalars the type resolver must accept as a convert target: a rule
     * rewriting a type **to** one of these makes it a scalar, not an object.
     *
     * Deliberately just these three — `String` is what
     * `extensions/converts.config` maps the date types to, and `BigInteger` /
     * `BigDecimal` complete the set of classes that
     * `DefaultPsiClassHelper.isSimpleType` treats as JSON-native. Primitive
     * wrappers are covered by [isPrimitiveWrapper] instead.
     */
    private val JSON_NATIVE_SCALARS = setOf(
        "java.lang.String",
        "java.math.BigInteger",
        "java.math.BigDecimal"
    )

    /**
     * Wrapper FQN → primitive keyword (`java.lang.Integer` → `int`).
     *
     * Derived from [PrimitiveFamilies] rather than spelled out here, so this table and the
     * type-comparison / canonical-text tables can never drift apart.
     */
    private val PRIMITIVE_WRAPPER_TYPES = PrimitiveFamilies.WRAPPER_TO_KEYWORD

    private val PRIMITIVE_TYPES = PrimitiveFamilies.PRIMITIVE_KEYWORDS

    /**
     * Every accepted spelling of a file type: the FQNs plus their bare simple names
     * (`MultipartFile`, `Part`, `File`, `Path`, `Resource`). Stored lowercased —
     * [mentionsFileType] is case-insensitive because one of its callers feeds it a
     * lowercased spelling (`JsonType.fromJavaType` normalizes before dispatching).
     */
    private val FILE_TYPE_SPELLINGS: Set<String> = buildSet {
        FILE_TYPES.forEach {
            add(it)
            add(it.substringAfterLast('.'))
        }
    }.mapTo(HashSet()) { it.lowercase() }

    /** Splits a type spelling into identifier tokens: `List<MultipartFile>` → [`List`, `MultipartFile`]. */
    private val TYPE_NAME_TOKENS = Regex("[^\\w$.]+")

    fun isFileType(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return FILE_TYPES.contains(qualifiedName)
    }
    
    fun isFileTypeCanonical(canonicalText: String?): Boolean {
        if (canonicalText == null) return false
        if (FILE_TYPES.contains(canonicalText)) return true
        return FILE_TYPES.any { canonicalText == it.substringAfterLast('.') }
    }

    /**
     * Returns the element type name by stripping a trailing `[]` suffix.
     * e.g. `"MultipartFile[]"` → `"MultipartFile"`, `"String"` → `"String"`
     */
    fun singleTypeName(typeName: String): String = typeName.removeSuffix("[]")

    /**
     * Returns true if the given string represents a file upload type in any form:
     * - Fully-qualified class name (e.g. `"org.springframework.web.multipart.MultipartFile"`)
     * - Simple class name (e.g. `"MultipartFile"`, `"Part"`)
     * - JSON type string (e.g. `"file"`, `"file[]"`)
     * - Array variants of any of the above (e.g. `"MultipartFile[]"`)
     *
     * Use this when the input may come from either PSI type resolution or JSON type mapping.
     */
    fun isFileTypeName(typeName: String?): Boolean {
        if (typeName.isNullOrBlank()) return false
        val t = singleTypeName(typeName.trim())
        return t == "file" || t == "__file__" || isFileTypeCanonical(t)
    }

    /**
     * True when [typeName] *mentions* a file type anywhere in its spelling — as the type
     * itself (`MultipartFile`, `org.springframework.web.multipart.MultipartFile`), as an
     * array (`MultipartFile[]`) or as the element of a container (`List<MultipartFile>`).
     *
     * Deliberately looser than [isFileTypeName], which must stay strict because
     * `TypeResolver` uses it to decide *whether the spelling itself is already a file* —
     * collapsing `List<MultipartFile>` there would drop the container. This variant answers
     * the other question ("does this declaration carry files?"), which is what Spring's
     * `@RequestParam` binding and the model-based parameter typing need.
     *
     * Matching is per identifier token, so a class whose name merely *contains* a file type
     * name — `Department`, `java.io.FileInputStream` — is not a file. Matching is
     * case-insensitive: callers hand it both real spellings (`Part`) and lowercased ones
     * (`JsonType.fromJavaType` normalizes before dispatching), and case never distinguishes
     * two file types.
     */
    fun mentionsFileType(typeName: String?): Boolean {
        if (typeName.isNullOrBlank()) return false
        val t = singleTypeName(typeName.trim()).lowercase()
        if (t == "file" || t == "__file__") return true
        return TYPE_NAME_TOKENS.split(t).any { it in FILE_TYPE_SPELLINGS }
    }

    fun isPrimitiveWrapper(qualifiedName: String?): Boolean {
        if (qualifiedName == null) return false
        return PRIMITIVE_WRAPPER_TYPES.containsKey(qualifiedName)
    }

    fun isPrimitive(typeName: String?): Boolean {
        if (typeName == null) return false
        return PRIMITIVE_TYPES.contains(typeName)
    }

    fun isSpecialType(qualifiedName: String?): Boolean {
        return isFileType(qualifiedName) || isPrimitiveWrapper(qualifiedName)
    }

    /**
     * True when [target] is a scalar a convert rule may legitimately rewrite a type to:
     * a file spelling, a primitive wrapper, a [JsonType] name (including the `date` /
     * `datetime` / `uuid` tokens a rule may opt into) or one of [JSON_NATIVE_SCALARS].
     *
     * This is the guard that keeps a rule rewriting a type to a *composite* — a project's
     * own DTO — from being mistaken for a scalar: such a target still gets expanded.
     */
    private fun isScalarTarget(target: String): Boolean =
        isFileTypeName(target) ||
                target in JSON_NATIVE_SCALARS ||
                isPrimitiveWrapper(target) ||
                JsonType.isValid(target)

    /**
     * The [JsonType] the active configuration rewrites [psiClass] to, when that target is a
     * scalar — `null` when no rule applies or the target is composite.
     *
     * Reads the very declaration the rule engine honours (`json.rule.convert[<fqn>]`), so the
     * type resolver and the field-level engine can never disagree about which types are
     * scalar. Rules with a scripted / regex filter are not visible here (only the plain
     * `<fqn>` filter is), which matches how the shipped `extensions/converts.config` is
     * written; a scripted rule still fires at field level via the engine.
     */
    fun convertedScalarTarget(psiClass: PsiClass): String? {
        val qualifiedName = psiClass.qualifiedName ?: return null
        // Built as a local rather than inline: a literal inside `getFirst(...)` is claimed by
        // `ImplicitKeyCompletenessTest` as a fixed key needing registration, and this one is a
        // filter of the registered `json.rule.convert` key, not a key of its own.
        val filterKey = "${RuleKeys.JSON_RULE_CONVERT.name}[$qualifiedName]"
        val target = runCatching {
            ConfigReader.getInstance(psiClass.project).getFirst(filterKey)
        }.getOrNull()?.trim() ?: return null
        if (target.isEmpty() || target == qualifiedName) return null
        return target.takeIf { isScalarTarget(it) }
    }

    fun getSimpleTypeName(qualifiedName: String?): String? {
        if (qualifiedName == null) return null

        if (isFileType(qualifiedName)) {
            return "file"
        }

        return PRIMITIVE_WRAPPER_TYPES[qualifiedName]
    }

    fun resolveSpecialType(psiClass: PsiClass): ResolvedType? {
        val qualifiedName = psiClass.qualifiedName ?: return null

        if (isFileType(qualifiedName)) {
            return ResolvedType.UnresolvedType("__file__")
        }

        // A type the configuration maps to a scalar resolves to an `UnresolvedType` carrying
        // that target, *not* to a `ClassType`. Two reasons:
        //  - expanding the class instead would document the JDK class' internals
        //    (`java.util.UUID` → `{"type":"object"}`, `java.time.Duration` → `{seconds, nanos}`);
        //  - the field-level engine only rewrites the field's own type, so without this a
        //    container element (`List<java.util.Date>`) would bypass the rule entirely and
        //    disagree with a plain `java.util.Date` field.
        // Either way the target reaches `JsonType.fromJavaType`, which is the only thing that
        // decides the JSON type.
        convertedScalarTarget(psiClass)?.let { return ResolvedType.UnresolvedType(it) }

        val simpleTypeName = getSimpleTypeName(qualifiedName) ?: return null

        return when (simpleTypeName) {
            "file" -> ResolvedType.UnresolvedType("__file__")
            // A wrapper class resolves to its primitive kind, flagged as boxed so that
            // ScriptTypeContext can still tell `Integer` from `int` afterwards.
            else -> PrimitiveFamilies.KIND_BY_SPELLING[simpleTypeName]?.let { kind ->
                ResolvedType.PrimitiveType(kind, boxed = true)
            }
        }
    }

    fun getDefaultValueForSpecialType(qualifiedName: String?): Any? {
        if (qualifiedName == null) return null

        if (isFileType(qualifiedName)) {
            return "(binary)"
        }

        return when (qualifiedName) {
            "java.lang.Boolean" -> false
            "java.lang.Byte" -> 0.toByte()
            "java.lang.Character" -> '\u0000'
            "java.lang.Short" -> 0.toShort()
            "java.lang.Integer" -> 0
            "java.lang.Long" -> 0L
            "java.lang.Float" -> 0.0f
            "java.lang.Double" -> 0.0
            else -> null
        }
    }

    fun getAllFileTypePatterns(): List<String> {
        return FILE_TYPES.map { "json.rule.convert[$it]=__file__" }
    }
}
