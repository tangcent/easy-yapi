package com.itangcent.easyapi.psi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder

/**
 * Shared value-field resolver for enum usages, used by both Case 1
 * (`DefaultPsiClassHelper` — field declared as enum type) and Case 2
 * (`SeeTagResolver` — normal-typed field with `@see EnumClass`).
 *
 * ## Resolution Algorithm (canonical priority order)
 *
 * 1. **`enum.use.custom` rule result** (includes annotation-driven config
 *    recipes such as `jackson.config`'s `@JsonValue` detection and
 *    `mybatis-plus.config`'s `@EnumValue` detection). The rule result
 *    always wins per Req 2.4; there is no fall-through to step 2/3.
 *    - `"name()"` → [ValueField.Name] (pseudo-field, unambiguous)
 *    - `"ordinal()"` → [ValueField.Ordinal]
 *    - bare `"name"` / `"ordinal"` → [ValueField.Name] / [ValueField.Ordinal]
 *      **unless** a same-named instance field exists (issue #1383 — then
 *      [ValueField.Instance] wins)
 *    - any other string → [ValueField.Instance] for the matching field,
 *      or [ValueField.Name] fallback if no such field exists
 * 2. **Explicit `@see` member** (Case 2 only, `seeMemberName != null`):
 *    same `name()`/`name` disambiguation as step 1; for any other member
 *    ending in `()` (e.g. `"getCode()"`), strip the `()` then apply
 *    [LinkResolver.getterToPropertyName] to map getters to properties.
 * 3. **`INTELLIGENT` heuristics** (mode-gated):
 *    - Case 1 (`context == null`): exactly one non-static non-enum instance
 *      field → [ValueField.Instance]
 *    - Case 2 (`context != null`): adapt `findEnumFieldByType` logic
 *      (type + name coincidence)
 * 4. **Fallback** → [ValueField.Name] (Spring default).
 *
 * Annotation intent enters at step 1 *via config* (D-GATE/D-RULE), so it
 * keeps top priority among non-`@see` signals. The `NAME`/`INTELLIGENT`
 * CODE heuristics (single-field, type-match) stay mode-gated for the cases
 * where no annotation exists.
 *
 * ## Threading
 * All PSI access occurs inside `readSync { }` per `AGENTS.md` threading rules.
 */
@Service(Service.Level.PROJECT)
class EnumValueResolver(private val project: Project) : IdeaLog {

    /**
     * The resolved value field of an enum.
     *
     * - [Name] is the pseudo-field `name()` — the enum constant name.
     * - [Ordinal] is the pseudo-field `ordinal()` — the enum constant ordinal.
     * - [Instance] is a real instance field whose constructor argument
     *   (or static initializer) becomes the option value.
     */
    sealed interface ValueField {
        /** Pseudo-field: enum constant name → JSON type STRING. */
        object Name : ValueField

        /** Pseudo-field: enum constant ordinal → JSON type INT. */
        object Ordinal : ValueField

        /**
         * Real instance field → JSON type = the field's type.
         * The constant's constructor argument at this field's position
         * (or the field's static initializer) becomes the option value.
         */
        data class Instance(val field: PsiField) : ValueField
    }

    /**
     * How the value field was chosen — for logging/debugging.
     *
     * Note: there is no `ANNOTATION` source. Annotation detection rides
     * the `enum.use.custom` rule (D-RULE), so its source is
     * [ENUM_USE_CUSTOM_RULE].
     */
    enum class Source {
        /** Explicit `enum.use.custom` rule (incl. annotation-driven config recipes). */
        ENUM_USE_CUSTOM_RULE,

        /** Explicit `@see Enum#member` (Case 2 only). */
        SEE_MEMBER,

        /** INTELLIGENT mode + exactly one instance field (Case 1). */
        INTELLIGENT_SINGLE,

        /** INTELLIGENT mode + Case 2 type match. */
        INTELLIGENT_TYPE_MATCH,

        /** Safe default: enum constant name (Spring default). */
        FALLBACK_NAME
    }

    /**
     * The result of resolving an enum's value field.
     *
     * @param valueField The resolved [ValueField].
     * @param source How it was chosen — for logging/debugging.
     */
    data class Resolution(val valueField: ValueField, val source: Source)

    /**
     * Reads `enumFieldAutoInferEnabled` from settings.
     *
     * When `true`, auto-inference is enabled for ambiguous references
     * (Case 1 single-field heuristic, Case 2 type-match heuristic).
     * Defaults to `false` (fall back to enum constant name).
     */
    private fun readAutoInferEnabled(): Boolean =
        runCatching {
            SettingBinder.getInstance(project).read().enumFieldAutoInferEnabled
        }.getOrNull() ?: false

    /**
     * Core decision. See [Resolution Algorithm][EnumValueResolver] above.
     *
     * @param enumClass The enum class to resolve.
     * @param context The referencing element (Case 2 declaring field/method),
     *        or null for Case 1.
     * @param seeMemberName The parsed `@see` member (`"code"`, `"getCode()"`,
     *        `"name()"`, …), or null for Case 1 / class-only `@see`.
     * @param autoInferEnabled Whether auto-inference is enabled for ambiguous
     *        references; defaults to [readAutoInferEnabled].
     */
    suspend fun resolve(
        enumClass: PsiClass,
        context: PsiElement? = null,
        seeMemberName: String? = null,
        autoInferEnabled: Boolean = readAutoInferEnabled()
    ): Resolution {
        // Step 1: enum.use.custom rule (includes annotation-driven config recipes).
        val engine = RuleEngine.getInstance(project)
        val ruleResult = engine.evaluate(RuleKeys.ENUM_USE_CUSTOM, enumClass)
        if (!ruleResult.isNullOrBlank()) {
            val resolved = parseRuleMember(enumClass, ruleResult)
            // Multi-annotation warning (Req 2.5): best-effort FQN-based scan.
            warnIfMultipleAnnotatedMembers(enumClass)
            return Resolution(resolved, Source.ENUM_USE_CUSTOM_RULE)
        }

        // Step 2: explicit @see member (Case 2 only).
        if (seeMemberName != null) {
            val resolved = parseSeeMember(enumClass, seeMemberName)
            if (resolved != null) {
                return Resolution(resolved, Source.SEE_MEMBER)
            }
        }

        // Step 3: auto-inference heuristics (setting-gated).
        if (autoInferEnabled) {
            // Case 1: context == null → single-field heuristic.
            if (context == null) {
                val instanceFields = instanceFields(enumClass)
                if (instanceFields.size == 1) {
                    return Resolution(ValueField.Instance(instanceFields.first()), Source.INTELLIGENT_SINGLE)
                }
            } else {
                // Case 2: context != null → type-match heuristic.
                val matched = findEnumFieldByType(enumClass, context)
                if (matched != null) {
                    return Resolution(ValueField.Instance(matched), Source.INTELLIGENT_TYPE_MATCH)
                }
            }
        }

        // Step 4: fallback → Name (Spring default).
        return Resolution(ValueField.Name, Source.FALLBACK_NAME)
    }

    /**
     * Parse a rule result (`enum.use.custom`) into a [ValueField].
     *
     * - `"name()"` → [ValueField.Name] (parenthesized = pseudo-field wins)
     * - `"ordinal()"` → [ValueField.Ordinal]
     * - bare `"name"` / `"ordinal"` → [ValueField.Name] / [ValueField.Ordinal]
     *   **unless** a same-named instance field exists (issue #1383)
     * - any other string → [ValueField.Instance] for the matching field,
     *   or [ValueField.Name] fallback if no such field exists
     *
     * The rule result always wins per Req 2.4; there is no fall-through.
     */
    private fun parseRuleMember(enumClass: PsiClass, ruleResult: String): ValueField {
        val normalized = ruleResult.removeSuffix("()")
        val isPseudoCall = ruleResult.endsWith("()")
        val instanceFields = instanceFields(enumClass)
        val hasInstanceFieldWithName = instanceFields.any { it.name == normalized }

        if (normalized == "name" && (isPseudoCall || !hasInstanceFieldWithName)) {
            return ValueField.Name
        }
        if (normalized == "ordinal" && (isPseudoCall || !hasInstanceFieldWithName)) {
            return ValueField.Ordinal
        }
        // Instance field lookup (rule result always wins; no fall-through).
        val field = instanceFields.firstOrNull { it.name == normalized }
        return field?.let { ValueField.Instance(it) } ?: ValueField.Name
    }

    /**
     * Parse a `@see` member into a [ValueField], or null if it cannot be resolved
     * (so the caller can fall through to step 3/4).
     *
     * - `"name()"` / `"ordinal()"` (with parens, preserved by `parseLinkReference`)
     *   → [ValueField.Name] / [ValueField.Ordinal]
     * - bare `"name"` / `"ordinal"` → [ValueField.Name] / [ValueField.Ordinal]
     *   **unless** a same-named instance field exists (issue #1383 parity with step 1)
     * - any other member ending in `()` (e.g. `"getCode()"`): strip the `()` first,
     *   then apply [LinkResolver.getterToPropertyName] (`getCode` → `code`,
     *   `isAlive` → `active`) and select the matching instance field
     * - otherwise (no trailing `()`, not a getter): literal field name →
     *   [ValueField.Instance] if found, else null
     */
    private fun parseSeeMember(enumClass: PsiClass, seeMemberName: String): ValueField? {
        val instanceFields = instanceFields(enumClass)

        // Pseudo-field forms (with parens preserved by parseLinkReference).
        if (seeMemberName == "name()") return ValueField.Name
        if (seeMemberName == "ordinal()") return ValueField.Ordinal

        // Bare name/ordinal — yield to a same-named instance field if present (issue #1383).
        if (seeMemberName == "name" || seeMemberName == "ordinal") {
            val hasInstanceFieldWithName = instanceFields.any { it.name == seeMemberName }
            if (!hasInstanceFieldWithName) {
                return if (seeMemberName == "name") ValueField.Name else ValueField.Ordinal
            }
            val field = instanceFields.first { it.name == seeMemberName }
            return ValueField.Instance(field)
        }

        // Getter form: `getCode()` → strip `()` → `getCode` → `code`.
        if (seeMemberName.endsWith("()")) {
            val methodName = seeMemberName.removeSuffix("()")
            val derived = LinkResolver.getterToPropertyName(methodName) ?: methodName
            val field = instanceFields.firstOrNull { it.name == derived }
            return field?.let { ValueField.Instance(it) }
        }

        // Plain field name.
        val field = instanceFields.firstOrNull { it.name == seeMemberName }
        return field?.let { ValueField.Instance(it) }
    }

    /**
     * Find an enum instance field whose type matches the referencing element's
     * declared type (Case 2 INTELLIGENT type-match).
     *
     * When multiple fields match, prefers the one whose name matches the
     * referencing field name.
     */
    private fun findEnumFieldByType(enumClass: PsiClass, contextElement: PsiElement): PsiField? {
        val targetCanonical = readSync {
            when (contextElement) {
                is PsiField -> contextElement.type.canonicalText
                is PsiMethod -> contextElement.returnType?.canonicalText
                is PsiParameter -> contextElement.type.canonicalText
                else -> null
            }
        } ?: return null

        val instanceFields = instanceFields(enumClass)
        val candidates = instanceFields.filter { field ->
            isTypeCompatible(field.type.canonicalText, targetCanonical)
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // Multiple candidates: prefer the one whose name matches the referencing field.
        val contextName = readSync {
            when (contextElement) {
                is PsiField -> contextElement.name
                is PsiMethod -> LinkResolver.getterToPropertyName(contextElement.name) ?: contextElement.name
                is PsiParameter -> contextElement.name
                else -> null
            }
        }
        if (contextName != null) {
            candidates.firstOrNull { it.name == contextName }?.let { return it }
        }
        return candidates.first()
    }

    /**
     * Best-effort multi-annotation warning (Req 2.5).
     *
     * The groovy recipe returns the first `find` hit and cannot emit warnings
     * (it returns a string). This method does a separate FQN-based scan of the
     * enum's fields and methods (NFR-3: `PsiAnnotation` qualified-name match,
     * no class-load) counting members annotated with any recognized annotation
     * FQN (`@JsonValue`, `@EnumValue`). If >1, logs a warning. The scan is
     * best-effort — failures are silently ignored.
     */
    private fun warnIfMultipleAnnotatedMembers(enumClass: PsiClass) {
        runCatching {
            val recognizedFqns = setOf(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.baomidou.mybatisplus.annotation.EnumValue"
            )
            val annotatedCount = readSync {
                val fields = enumClass.fields.filter { it !is PsiEnumConstant }
                val methods = enumClass.methods
                val annotatedFields = fields.count { field ->
                    field.annotations.any { it.qualifiedName in recognizedFqns }
                }
                val annotatedMethods = methods.count { method ->
                    method.annotations.any { it.qualifiedName in recognizedFqns }
                }
                annotatedFields + annotatedMethods
            }
            if (annotatedCount > 1) {
                LOG.warn(
                    "Enum ${enumClass.qualifiedName ?: enumClass.name} has $annotatedCount members " +
                        "annotated with a recognized value annotation (@JsonValue/@EnumValue). " +
                        "Selecting the first by declaration order; please remove the extra annotations."
                )
            }
        }
    }

    /**
     * Build options from enum constants per the resolved [ValueField].
     *
     * Description logic per Req 8:
     * 1. `docHelper.getAttrOfDocComment(constant)` (NOT `SeeTagResolver.extractDocText` —
     *    unifies Case 1/Case 2 per Req 6.2; changes Case 2 behavior, acceptable per NFR-1)
     * 2. else if the enum has an instance field in the preferred set
     *    (`desc`, `description`, `label`, `text`, `remark`, `message`) → use that
     *    field's value for the constant
     * 3. else join remaining instance-field values (current behavior)
     *
     * @param enumClass The enum class.
     * @param resolution The resolved [Resolution] from [resolve].
     * @param docHelper The [DocHelper] for doc-comment extraction.
     */
    suspend fun buildOptions(
        enumClass: PsiClass,
        resolution: Resolution,
        docHelper: DocHelper
    ): List<FieldOption>? {
        val constants = readSync { enumClass.fields.filterIsInstance<PsiEnumConstant>() }
        if (constants.isEmpty()) return null

        val instanceFields = instanceFields(enumClass)
        val preferredDescFields = listOf("desc", "description", "label", "text", "remark", "message")

        return constants.mapIndexedNotNull { index, constant ->
            val name = constant.name ?: return@mapIndexedNotNull null
            val desc = docHelper.getAttrOfDocComment(constant)

            val value: Any? = when (val vf = resolution.valueField) {
                is ValueField.Name -> name
                is ValueField.Ordinal -> index
                is ValueField.Instance -> readSync {
                    val fieldIndex = instanceFields.indexOfFirst { it.name == vf.field.name }
                    if (fieldIndex >= 0) {
                        val args = constant.argumentList?.expressions
                        if (args != null && fieldIndex < args.size) {
                            SeeTagResolver.extractConstantValue(args[fieldIndex])
                        } else {
                            // Fall back to the field's static initializer if no constructor arg.
                            SeeTagResolver.extractConstantValue(vf.field.initializer)
                        }
                    } else {
                        name
                    }
                }
            }

            // Description: (1) doc comment, (2) preferred-name field, (3) join remaining.
            val effectiveDesc = desc ?: buildDescForConstant(constant, instanceFields, resolution.valueField, preferredDescFields)

            FieldOption(value = value ?: name, desc = effectiveDesc)
        }.ifEmpty { null }
    }

    /**
     * Build a description for a constant when no doc comment is present.
     *
     * 1. If the enum has an instance field in the preferred set
     *    (`desc`, `description`, `label`, `text`, `remark`, `message`) → use that
     *    field's value for the constant (Req 8.2). First in the preferred set wins.
     * 2. Else join remaining instance-field values (current behavior, Req 8.3).
     */
    private fun buildDescForConstant(
        constant: PsiEnumConstant,
        instanceFields: List<PsiField>,
        valueField: ValueField,
        preferredDescFields: List<String>
    ): String? {
        val args = constant.argumentList?.expressions ?: return null

        // (1) Preferred-name field.
        for (preferredName in preferredDescFields) {
            val preferredIndex = instanceFields.indexOfFirst { it.name == preferredName }
            if (preferredIndex >= 0 && preferredIndex < args.size) {
                val value = SeeTagResolver.extractConstantValue(args[preferredIndex])
                if (value != null) return value.toString()
            }
        }

        // (2) Join remaining instance-field values (excluding the value field).
        val excludeIndex = when (valueField) {
            is ValueField.Instance -> instanceFields.indexOfFirst { it.name == valueField.field.name }
            else -> -1
        }
        val parts = mutableListOf<String>()
        for ((i, _) in instanceFields.withIndex()) {
            if (i == excludeIndex) continue
            if (i < args.size) {
                val value = SeeTagResolver.extractConstantValue(args[i])
                if (value != null) parts.add(value.toString())
            }
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    /**
     * Derive the JSON type for the resolved [ValueField].
     *
     * Returns the JSON type as a `String` (e.g. [JsonType.STRING], [JsonType.INT]).
     * Callers wrap the result in `ObjectModel.single(...)`.
     *
     * - [ValueField.Name] → [JsonType.STRING]
     * - [ValueField.Ordinal] → [JsonType.INT]
     * - [ValueField.Instance] → [JsonType.fromJavaType] of the field's PSI type
     */
    fun resolveJsonType(enumClass: PsiClass, resolution: Resolution): String {
        return when (val vf = resolution.valueField) {
            is ValueField.Name -> JsonType.STRING
            is ValueField.Ordinal -> JsonType.INT
            is ValueField.Instance -> {
                val typeName = readSync { vf.field.type.canonicalText }
                JsonType.fromJavaType(typeName)
            }
        }
    }

    /**
     * Reconcile a Case 2 declaring type against the value-field type.
     *
     * Takes the already-computed value-field JSON type (from [resolveJsonType])
     * rather than re-accessing PSI — this keeps the function non-suspend and
     * avoids a redundant read action.
     *
     * Per design.md D-TYPE compatibility table:
     * - primitive↔boxed → boxed canonical
     * - same type → that type
     * - numeric widening/narrowing → narrower (value-field)
     * - `Object`/`Serializable`/`Comparable` → value-field type
     * - `String`↔numeric → value-field type
     * - default → value-field type (values are authoritative)
     *
     * Logs at DEBUG when a reconciliation changes the declared type (Req 7.3).
     */
    fun reconcileType(declared: String, valueFieldJsonType: String): String {
        if (declared == valueFieldJsonType) return declared

        val normalizedDeclared = normalizeJsonType(declared)
        val normalizedValue = normalizeJsonType(valueFieldJsonType)

        if (normalizedDeclared == normalizedValue) {
            // primitive↔boxed normalization — keep the value-field's form.
            if (declared != valueFieldJsonType) {
                LOG.debug("Reconciled enum JSON type: $declared → $valueFieldJsonType (primitive↔boxed)")
            }
            return valueFieldJsonType
        }

        // Declared is wider/uninformative → value-field wins.
        val widerTypes = setOf(JsonType.OBJECT, "object")
        if (normalizedDeclared in widerTypes) {
            LOG.debug("Reconciled enum JSON type: $declared → $valueFieldJsonType (declared is Object/uninformative)")
            return valueFieldJsonType
        }

        // Numeric widening/narrowing → narrower (value-field).
        if (JsonType.isNumber(normalizedDeclared) && JsonType.isNumber(normalizedValue)) {
            LOG.debug("Reconciled enum JSON type: $declared → $valueFieldJsonType (numeric narrowing)")
            return valueFieldJsonType
        }

        // String↔numeric → value-field wins (incompatible; values authoritative).
        // Default → value-field wins.
        LOG.debug("Reconciled enum JSON type: $declared → $valueFieldJsonType (value-field authoritative)")
        return valueFieldJsonType
    }

    /**
     * Normalize a JSON type string for comparison (handles primitive↔boxed).
     */
    private fun normalizeJsonType(type: String): String {
        return when (type) {
            "int", "integer", "java.lang.Integer" -> JsonType.INT
            "long", "java.lang.Long" -> JsonType.LONG
            "short", "java.lang.Short" -> JsonType.SHORT
            "byte", "java.lang.Byte" -> JsonType.INT
            "float", "java.lang.Float" -> JsonType.FLOAT
            "double", "java.lang.Double" -> JsonType.DOUBLE
            "boolean", "java.lang.Boolean" -> JsonType.BOOLEAN
            "char", "character", "java.lang.Character" -> JsonType.STRING
            "string", "java.lang.String" -> JsonType.STRING
            else -> type
        }
    }

    /**
     * Get the non-static, non-enum instance fields of an enum class.
     */
    private fun instanceFields(enumClass: PsiClass): List<PsiField> = readSync {
        enumClass.allFields.filter {
            it !is PsiEnumConstant && !it.hasModifierProperty(PsiModifier.STATIC)
        }
    }

    /**
     * Check whether two type canonical texts are compatible (same or
     * primitive↔boxed of the same kind).
     */
    private fun isTypeCompatible(fieldType: String, targetType: String): Boolean {
        if (fieldType == targetType) return true
        return normalizeBoxedType(fieldType) == normalizeBoxedType(targetType)
    }

    /**
     * Normalize a canonical type text to its boxed form for comparison.
     */
    private fun normalizeBoxedType(type: String): String {
        return when (type) {
            "int", "Integer", "java.lang.Integer" -> "java.lang.Integer"
            "long", "Long", "java.lang.Long" -> "java.lang.Long"
            "short", "Short", "java.lang.Short" -> "java.lang.Short"
            "byte", "Byte", "java.lang.Byte" -> "java.lang.Byte"
            "float", "Float", "java.lang.Float" -> "java.lang.Float"
            "double", "Double", "java.lang.Double" -> "java.lang.Double"
            "boolean", "Boolean", "java.lang.Boolean" -> "java.lang.Boolean"
            "char", "Character", "java.lang.Character" -> "java.lang.Character"
            else -> type
        }
    }

    companion object {
        /**
         * Get the [EnumValueResolver] instance for a project.
         *
         * Needed by [SeeTagResolver], which is not a `@Service` itself.
         */
        fun getInstance(project: Project): EnumValueResolver = project.service()
    }
}
