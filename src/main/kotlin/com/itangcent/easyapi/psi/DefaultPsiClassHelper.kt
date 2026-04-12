package com.itangcent.easyapi.psi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.cache.JsonConstructionCache
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.context.project
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.*
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.util.GsonUtils

/**
 * Options for JSON model building.
 *
 * Controls which elements are included in the object model:
 * - READ_COMMENT - Include field comments
 * - READ_GETTER - Include properties from getter methods
 * - READ_SETTER - Include properties from setter methods
 */
object JsonOption {
    const val NONE = 0b0000
    const val READ_COMMENT = 0b0001
    const val READ_GETTER = 0b0010
    const val READ_SETTER = 0b0100
    const val READ_GETTER_OR_SETTER = READ_GETTER or READ_SETTER
    const val ALL = READ_GETTER_OR_SETTER or READ_COMMENT

    fun has(option: Int, flag: Int): Boolean = (option and flag) != 0
}

/**
 * Default implementation of PsiClassHelper.
 *
 * Builds ObjectModel from PSI classes with support for:
 * - Generic type resolution and substitution
 * - Field, getter, and setter analysis
 * - Enum constant extraction with descriptions
 * - Collection and map handling
 * - Recursive type detection
 * - Rule-based customization
 *
 * ## Features
 * - Caches results to avoid redundant processing
 * - Handles circular references gracefully
 * - Supports custom field names via rules
 * - Extracts field comments from JavaDoc
 *
 * ## Usage
 * ```kotlin
 * val helper = DefaultPsiClassHelper.getInstance(project)
 * val model = helper.buildObjectModel(psiClass, actionContext)
 * ```
 *
 * @see PsiClassHelper for the interface
 * @see ObjectModel for the model structure
 * @see TypeResolver for type resolution
 */
@Service(Service.Level.PROJECT)
class DefaultPsiClassHelper : PsiClassHelper {

    companion object {
        fun getInstance(project: Project): DefaultPsiClassHelper =
            project.getService(DefaultPsiClassHelper::class.java)
    }

    override suspend fun buildObjectModelFromType(
        psiType: PsiType,
        actionContext: ActionContext,
        option: Int,
        maxDepth: Int,
        genericContext: GenericContext,
        contextElement: PsiElement?
    ): ObjectModel? {
        val engine = RuleEngine.getInstance(actionContext)
        val converted = engine.evaluate(RuleKeys.JSON_RULE_CONVERT, psiType, contextElement)
        if (!converted.isNullOrBlank()) {
            val result = buildObjectModelFromConvertedType(
                convertedType = converted,
                sourcePsiType = psiType,
                contextElement = contextElement,
                actionContext = actionContext,
                option = option,
                maxDepth = maxDepth,
                genericContext = genericContext
            )
            if (result != null) return result
        }

        val resolvedType = TypeResolver.resolve(psiType, genericContext)
        return buildObjectModelFromConvertedType(resolvedType, actionContext, option, maxDepth, genericContext)
    }

    override suspend fun buildObjectModel(
        psiClass: PsiClass,
        actionContext: ActionContext,
        option: Int,
        maxDepth: Int
    ): ObjectModel? {
        val engine = RuleEngine.getInstance(actionContext)

        val converted = engine.evaluate(
            RuleKeys.JSON_RULE_CONVERT,
            com.intellij.psi.util.PsiTypesUtil.getClassType(psiClass),
            psiClass
        )
        if (!converted.isNullOrBlank()) {
            return buildObjectModelFromConvertedType(
                convertedType = converted,
                contextElement = psiClass,
                actionContext = actionContext,
                option = option,
                maxDepth = maxDepth,
                genericContext = GenericContext.EMPTY
            )
        }

        val docHelper = actionContext.instanceOrNull(DocHelper::class) ?: StandardDocHelper()
        val cache = JsonConstructionCache()
        val visited = HashSet<String>()
        return buildTypeObject(
            psiClass, actionContext, engine, docHelper, cache,
            option, maxDepth, depth = 0, visited = visited
        )
    }

    private suspend fun buildObjectModelFromConvertedType(
        resolvedType: ResolvedType,
        actionContext: ActionContext,
        option: Int,
        maxDepth: Int,
        genericContext: GenericContext
    ): ObjectModel? {
        val substitutedType = TypeResolver.substitute(resolvedType, genericContext)
        if (substitutedType is ResolvedType.PrimitiveType && substitutedType.kind == PrimitiveKind.VOID) return null

        val engine = RuleEngine.getInstance(actionContext)
        val docHelper = actionContext.instanceOrNull(DocHelper::class) ?: StandardDocHelper()
        val cache = JsonConstructionCache()
        val visited = HashSet<String>()

        return buildFieldValue(
            substitutedType, actionContext, engine, docHelper, cache,
            option, maxDepth, depth = 0, visited = visited
        )
    }

    private suspend fun buildObjectModelFromConvertedType(
        convertedType: String,
        sourcePsiType: PsiType? = null,
        contextElement: PsiElement?,
        actionContext: ActionContext,
        option: Int,
        maxDepth: Int,
        genericContext: GenericContext
    ): ObjectModel? {
        val project = actionContext.project()
        val rawResolved = TypeResolver.resolveFromCanonicalText(convertedType, project, contextElement, genericContext)

        // If the type couldn't be fully resolved but looks like a well-known collection,
        // try to resolve the element type and build an array model.
        if (rawResolved is ResolvedType.UnresolvedType) {
            val trimmed = convertedType.trim()
            val collectionElementText = extractCollectionElementType(trimmed)
            if (collectionElementText != null) {
                // Try resolving the element type from the source PsiType's type arguments first
                // (these are already-resolved PsiType objects, not text)
                val elementResolved = resolveElementTypeFromSource(sourcePsiType, collectionElementText, genericContext, contextElement)
                    ?: TypeResolver.resolveFromCanonicalText(collectionElementText, project, contextElement, genericContext)
                val elementModel = buildObjectModelFromConvertedType(
                    elementResolved, actionContext, option, maxDepth, genericContext
                )
                return if (elementModel != null) ObjectModel.array(elementModel) else null
            }
        }

        return buildObjectModelFromConvertedType(
            rawResolved,
            actionContext,
            option,
            maxDepth,
            genericContext
        )
    }

    /**
     * Extracts the element type from a collection type text.
     * e.g. "java.util.List<com.example.User>" → "com.example.User"
     * Returns null if the text is not a recognized collection type.
     */
    private fun extractCollectionElementType(typeText: String): String? {
        val collectionPrefixes = listOf(
            "java.util.List<", "java.util.Set<", "java.util.Collection<",
            "java.util.ArrayList<", "java.util.LinkedList<",
            "java.util.HashSet<", "java.util.LinkedHashSet<"
        )
        for (prefix in collectionPrefixes) {
            if (typeText.startsWith(prefix) && typeText.endsWith(">")) {
                return typeText.removePrefix(prefix).removeSuffix(">").trim()
            }
        }
        return null
    }

    /**
     * Tries to resolve the element type from the source PsiType's type arguments.
     * When a convert rule transforms `Flux<UserInfo>` to `List<UserInfo>`, the `UserInfo`
     * type argument in the source PsiType is already resolved. We can use it directly
     * instead of trying to resolve from canonical text (which may fail in test environments).
     *
     * Falls back to creating a PsiType from text using the context element's scope.
     */
    private fun resolveElementTypeFromSource(
        sourcePsiType: PsiType?,
        elementText: String,
        genericContext: GenericContext,
        contextElement: PsiElement? = null
    ): ResolvedType? {
        val classType = sourcePsiType as? PsiClassType ?: return null
        // Try resolving from the source type's type arguments
        val typeArgs = readSync { classType.parameters }
        for (arg in typeArgs) {
            if (readSync { arg.canonicalText } == elementText) {
                val resolved = readSync { TypeResolver.resolve(arg, genericContext) }
                if (resolved !is ResolvedType.UnresolvedType) return resolved
            }
        }
        // Fallback: create a PsiType from text using the context element's scope
        if (contextElement != null) {
            val project = contextElement.project
            val psiType = readSync {
                runCatching {
                    JavaPsiFacade.getInstance(project).elementFactory
                        .createTypeFromText(elementText, contextElement)
                }.getOrNull()
            }
            if (psiType != null) {
                val resolved = readSync { TypeResolver.resolve(psiType, genericContext) }
                if (resolved !is ResolvedType.UnresolvedType) return resolved
            }
        }
        return null
    }

    private suspend fun buildTypeObject(
        psiClass: PsiClass,
        actionContext: ActionContext,
        engine: RuleEngine,
        docHelper: DocHelper,
        cache: JsonConstructionCache,
        option: Int,
        maxDepth: Int,
        depth: Int,
        visited: MutableSet<String>,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel.Object? {
        val qualifiedName = psiClass.qualifiedName ?: psiClass.name ?: return null

        if (qualifiedName == "java.lang.Object") {
            return ObjectModel.emptyObject()
        }

        if (genericContext == GenericContext.EMPTY) {
            val cacheKey = "$qualifiedName@$option"
            val cached = cache.get(cacheKey)
            if (cached != null) return cached as? ObjectModel.Object
        }

        if (depth >= maxDepth) return null
        if (!visited.add(qualifiedName)) return null

        // Build the effective generic context by resolving the class's own type parameters
        // and its super type bindings via TypeResolver.resolveGenericParams (which recurses
        // through the full hierarchy). Then merge with any externally-provided context.
        val effectiveContext = if (genericContext != GenericContext.EMPTY) {
            val map = LinkedHashMap<String, ResolvedType>()
            map.putAll(genericContext.genericMap)
            val superBindings = TypeResolver.resolveGenericParams(
                psiClass,
                psiClass.typeParameters.map { tp ->
                    genericContext.genericMap[tp.name] ?: ResolvedType.UnresolvedType(tp.name ?: "?")
                })
            for ((k, v) in superBindings) {
                if (!map.containsKey(k)) map[k] = v
            }
            GenericContext(map)
        } else {
            val bindings = TypeResolver.resolveGenericParams(psiClass, emptyList())
            if (bindings.isEmpty()) GenericContext.EMPTY else GenericContext(bindings)
        }

        engine.evaluate(RuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)

        // Check field.max.depth rule for this class
        val fieldMaxDepth = engine.evaluate(RuleKeys.FIELD_MAX_DEPTH, psiClass)
        val effectiveMaxDepth = fieldMaxDepth ?: maxDepth

        val fields = linkedMapOf<String, FieldModel>()

        // Create the result early and cache it so that self-referencing types
        // (e.g., Node.parent -> Node) can resolve via cache lookup instead of
        // returning an empty object. The fields map is mutable and will be
        // populated as fields are processed below.
        val result = ObjectModel.Object(fields)
        if (genericContext == GenericContext.EMPTY) {
            val cacheKey = "$qualifiedName@$option"
            cache.put(cacheKey, result)
        }

        val accessibleFields = collectAccessibleFields(psiClass, option)

        for (accessibleField in accessibleFields) {
            val fieldName = accessibleField.name
            if (fields.containsKey(fieldName)) continue

            if (engine.evaluate(RuleKeys.FIELD_IGNORE, accessibleField.psi)) {
                continue
            }

            // Check constant.field.ignore for static final fields
            if (accessibleField.psi is PsiField) {
                val psiField = accessibleField.psi as PsiField
                if (psiField.hasModifierProperty(PsiModifier.STATIC) && psiField.hasModifierProperty(PsiModifier.FINAL)) {
                    if (engine.evaluate(RuleKeys.CONSTANT_FIELD_IGNORE, psiField)) {
                        continue
                    }
                }
            }

            engine.evaluate(RuleKeys.JSON_FIELD_PARSE_BEFORE, accessibleField.psi)

            val customName = engine.evaluate(RuleKeys.FIELD_NAME, accessibleField.psi)
            val prefix = engine.evaluate(RuleKeys.FIELD_NAME_PREFIX, accessibleField.psi) ?: ""
            val suffix = engine.evaluate(RuleKeys.FIELD_NAME_SUFFIX, accessibleField.psi) ?: ""
            val baseName = customName?.takeIf { it.isNotBlank() } ?: fieldName
            val jsonFieldName = prefix + baseName + suffix

            if (fields.containsKey(jsonFieldName)) continue

            val fieldDefaultValue =
                engine.evaluate(RuleKeys.FIELD_DEFAULT_VALUE, accessibleField.psi)
            val fieldRequired = engine.evaluate(RuleKeys.FIELD_REQUIRED, accessibleField.psi)
            val fieldMock = engine.evaluate(RuleKeys.FIELD_MOCK, accessibleField.psi)
            val fieldDemo = engine.evaluate(RuleKeys.FIELD_DEMO, accessibleField.psi)
            val fieldAdvancedStr = engine.evaluate(RuleKeys.FIELD_ADVANCED, accessibleField.psi)
            val fieldAdvanced = if (!fieldAdvancedStr.isNullOrBlank()) {
                runCatching { GsonUtils.fromJson<Map<String, Any?>>(fieldAdvancedStr) }.getOrNull()
            } else null

            val fieldComment = if (JsonOption.has(option, JsonOption.READ_COMMENT)) {
                val directComment = when (val psi = accessibleField.psi) {
                    is PsiField -> docHelper.getAttrOfField(psi)
                    else -> docHelper.getAttrOfDocComment(psi)
                }
                val ruleComment = engine.evaluate(RuleKeys.FIELD_DOC, accessibleField.psi)
                mergeComments(directComment, ruleComment)
            } else null

            val converted = engine.evaluate(RuleKeys.JSON_RULE_CONVERT, accessibleField.type, accessibleField.psi)
            val rawResolved: ResolvedType
            val fieldType: ResolvedType
            if (!converted.isNullOrBlank()) {
                val project = actionContext.project()
                rawResolved = TypeResolver.resolveFromCanonicalText(
                    canonicalText = converted,
                    project = project,
                    contextElement = accessibleField.psi,
                    context = effectiveContext
                )
                fieldType = TypeResolver.substitute(rawResolved, effectiveContext)
            } else {
                rawResolved = TypeResolver.resolve(accessibleField.type, effectiveContext)
                fieldType = TypeResolver.substitute(rawResolved, effectiveContext)
            }

            // A field is "generic" if its declared type is a type parameter (e.g., T in Result<T>)
            val isGenericField = rawResolved is ResolvedType.UnresolvedType
                    && effectiveContext.genericMap.containsKey(rawResolved.canonicalText)

            val fieldModel = buildFieldModel(
                fieldType, actionContext, engine, docHelper, cache,
                option, effectiveMaxDepth, depth + 1, visited,
                fieldComment, fieldRequired, fieldDefaultValue, fieldMock, fieldDemo, fieldAdvanced,
                psiElement = accessibleField.psi,
                generic = isGenericField
            )

            // Check json.unwrapped — if true, merge the field's object fields into the parent
            val unwrapped = engine.evaluate(RuleKeys.JSON_UNWRAPPED, accessibleField.psi)
            if (unwrapped && fieldModel.model is ObjectModel.Object) {
                for ((nestedName, nestedField) in (fieldModel.model as ObjectModel.Object).fields) {
                    if (!fields.containsKey(nestedName)) {
                        fields[nestedName] = nestedField
                    }
                }
            } else {
                fields[jsonFieldName] = fieldModel
            }

            engine.evaluate(RuleKeys.JSON_FIELD_PARSE_AFTER, accessibleField.psi)
        }

        val additional = engine.evaluate(RuleKeys.JSON_ADDITIONAL_FIELD, psiClass)
        if (!additional.isNullOrBlank()) {
            for (line in additional.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                try {
                    val additionalField = GsonUtils.fromJson<Map<String, Any?>>(trimmed)
                    val addFieldName = additionalField["name"]?.toString()
                    val addFieldType = additionalField["type"]?.toString()
                    val addFieldDesc = additionalField["desc"]?.toString()
                    if (!addFieldName.isNullOrBlank() && !addFieldType.isNullOrBlank()) {
                        if (!fields.containsKey(addFieldName)) {
                            val addFieldModel = FieldModel(
                                model = ObjectModel.single(JsonType.fromJavaType(addFieldType)),
                                comment = if (!addFieldDesc.isNullOrBlank()) addFieldDesc else null
                            )
                            fields[addFieldName] = addFieldModel
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        engine.evaluate(RuleKeys.JSON_CLASS_PARSE_AFTER, psiClass)
        visited.remove(qualifiedName)

        // Apply field ordering if field.order rules are configured
        val orderedFields = applyFieldOrdering(fields, accessibleFields, engine)
        if (orderedFields != null) {
            fields.clear()
            fields.putAll(orderedFields)
        }

        return result
    }

    private data class AccessibleField(
        val name: String,
        val type: PsiType,
        val psi: PsiElement
    )

    private fun collectAccessibleFields(psiClass: PsiClass, option: Int): List<AccessibleField> {
        val fields = mutableListOf<AccessibleField>()
        val fieldNames = mutableSetOf<String>()

        for (field in psiClass.allFields) {
            if (shouldIgnoreField(field)) continue
            val name = field.name
            if (fieldNames.add(name)) {
                fields.add(AccessibleField(name = name, type = field.type, psi = field))
            }
        }

        if (JsonOption.has(option, JsonOption.READ_GETTER)) {
            for (method in psiClass.allMethods) {
                if (isFromObject(method)) continue
                if (isGetter(method)) {
                    val propertyName = getPropertyNameFromGetter(method.name)
                    if (fieldNames.add(propertyName)) {
                        val returnType = method.returnType ?: continue
                        fields.add(AccessibleField(name = propertyName, type = returnType, psi = method))
                    }
                }
            }
        }

        if (JsonOption.has(option, JsonOption.READ_SETTER)) {
            for (method in psiClass.allMethods) {
                if (isFromObject(method)) continue
                if (isSetter(method)) {
                    val propertyName = getPropertyNameFromSetter(method.name)
                    if (fieldNames.add(propertyName)) {
                        val paramType = method.parameterList.parameters.firstOrNull()?.type ?: continue
                        fields.add(AccessibleField(name = propertyName, type = paramType, psi = method))
                    }
                }
            }
        }

        return fields
    }

    private fun shouldIgnoreField(field: PsiField): Boolean {
        if (field.hasModifierProperty(PsiModifier.STATIC)) return true
        if (field.hasModifierProperty(PsiModifier.TRANSIENT)) return true
        return false
    }

    private fun isFromObject(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return containingClass.qualifiedName == "java.lang.Object"
    }

    private fun isGetter(method: PsiMethod): Boolean {
        val name = method.name
        if (method.parameterList.parameters.isNotEmpty()) return false
        if (method.returnType == null || method.returnType == PsiTypes.voidType()) return false
        if (name.startsWith("get") && name.length > 3) return true
        if (name.startsWith("is") && name.length > 2 && method.returnType == PsiTypes.booleanType()) return true
        return false
    }

    private fun isSetter(method: PsiMethod): Boolean {
        val name = method.name
        if (!name.startsWith("set") || name.length <= 3) return false
        if (method.parameterList.parameters.size != 1) return false
        return true
    }

    private fun getPropertyNameFromGetter(name: String): String {
        return when {
            name.startsWith("get") && name.length > 3 -> name[3].lowercaseChar() + name.substring(4)
            name.startsWith("is") && name.length > 2 -> name[2].lowercaseChar() + name.substring(3)
            else -> name
        }
    }

    private fun getPropertyNameFromSetter(name: String): String {
        return if (name.startsWith("set") && name.length > 3) {
            name[3].lowercaseChar() + name.substring(4)
        } else {
            name
        }
    }

    private suspend fun buildFieldModel(
        resolvedType: ResolvedType,
        actionContext: ActionContext,
        engine: RuleEngine,
        docHelper: DocHelper,
        cache: JsonConstructionCache,
        option: Int,
        maxDepth: Int,
        depth: Int,
        visited: MutableSet<String>,
        comment: String?,
        required: Boolean,
        defaultValue: String?,
        mock: String? = null,
        demo: String? = null,
        advanced: Map<String, Any?>? = null,
        psiElement: PsiElement? = null,
        generic: Boolean = false
    ): FieldModel {
        val model = buildFieldValue(
            resolvedType, actionContext, engine, docHelper, cache,
            option, maxDepth, depth, visited
        )
        // First try: resolve options from the field's own type (e.g., field is an enum type)
        var options = resolveFieldOptions(resolvedType, docHelper)
        // Second try: resolve options from @see tags in the field's doc comment
        // This handles the pattern: @see com.example.UserType (where field type is Integer)
        if (options == null && psiElement != null) {
            val project = actionContext.instanceOrNull(com.intellij.openapi.project.Project::class)
            if (project != null) {
                options = SeeTagResolver(project).resolveOptions(psiElement, docHelper)
            }
        }
        return FieldModel(
            model = model,
            comment = comment,
            required = required,
            defaultValue = defaultValue,
            options = options,
            mock = mock,
            demo = demo,
            advanced = advanced,
            generic = generic
        )
    }

    /**
     * Resolves enum constants (with doc descriptions) for enum types.
     * Unwraps arrays/collections to find the element type.
     */
    private suspend fun resolveFieldOptions(resolvedType: ResolvedType, docHelper: DocHelper): List<FieldOption>? {
        return when (resolvedType) {
            is ResolvedType.ClassType -> {
                val psiClass = resolvedType.psiClass
                if (isEnum(psiClass)) {
                    resolveEnumOptions(psiClass, docHelper)
                } else if (isCollection(psiClass)) {
                    resolvedType.typeArgs.firstOrNull()?.let { resolveFieldOptions(it, docHelper) }
                } else null
            }

            is ResolvedType.ArrayType -> resolveFieldOptions(resolvedType.componentType, docHelper)
            is ResolvedType.WildcardType -> resolvedType.upper?.let { resolveFieldOptions(it, docHelper) }
            else -> null
        }
    }

    private suspend fun resolveEnumOptions(psiClass: PsiClass, docHelper: DocHelper): List<FieldOption>? {
        val constants = psiClass.fields.filterIsInstance<PsiEnumConstant>()
        if (constants.isEmpty()) return null
        return constants.mapNotNull { constant ->
            val name = constant.name ?: return@mapNotNull null
            val desc = docHelper.getAttrOfDocComment(constant)
            FieldOption(value = name, desc = desc)
        }.ifEmpty { null }
    }

    private suspend fun buildFieldValue(
        resolvedType: ResolvedType,
        actionContext: ActionContext,
        engine: RuleEngine,
        docHelper: DocHelper,
        cache: JsonConstructionCache,
        option: Int,
        maxDepth: Int,
        depth: Int,
        visited: MutableSet<String>
    ): ObjectModel {
        return when (resolvedType) {
            is ResolvedType.PrimitiveType -> getDefaultValueForPrimitive(resolvedType.kind)
            is ResolvedType.ArrayType -> {
                val componentModel = buildFieldValue(
                    resolvedType.componentType,
                    actionContext, engine, docHelper, cache, option, maxDepth, depth, visited
                )
                ObjectModel.array(componentModel)
            }

            is ResolvedType.ClassType -> {
                val psiClass = resolvedType.psiClass
                val qualifiedName = psiClass.qualifiedName

                if (qualifiedName != null && SpecialTypeHandler.isDateTimeAsString(qualifiedName)) {
                    return ObjectModel.single(JsonType.STRING)
                }

                if (isSimpleType(psiClass)) {
                    getDefaultValueForSimpleType(psiClass)
                } else if (isCollection(psiClass)) {
                    val elementType = resolvedType.typeArgs.firstOrNull()
                    val elementModel = if (elementType != null) {
                        buildFieldValue(
                            elementType, actionContext, engine, docHelper, cache,
                            option, maxDepth, depth, visited
                        )
                    } else ObjectModel.single(JsonType.OBJECT)
                    ObjectModel.array(elementModel)
                } else if (isMap(psiClass)) {
                    val keyType = resolvedType.typeArgs.getOrNull(0)
                    val valueType = resolvedType.typeArgs.getOrNull(1)
                    val keyModel = if (keyType != null) {
                        buildFieldValue(
                            keyType, actionContext, engine, docHelper, cache,
                            option, maxDepth, depth, visited
                        )
                    } else ObjectModel.single(JsonType.STRING)
                    val valueModel = if (valueType != null) {
                        buildFieldValue(
                            valueType, actionContext, engine, docHelper, cache,
                            option, maxDepth, depth, visited
                        )
                    } else ObjectModel.single(JsonType.OBJECT)
                    ObjectModel.map(keyModel, valueModel)
                } else if (isEnum(psiClass)) {
                    // Check enum.use.custom rule for custom enum serialization
                    val customEnumField = engine.evaluate(RuleKeys.ENUM_USE_CUSTOM, psiClass)
                    if (!customEnumField.isNullOrBlank()) {
                        // Use the specified field of the enum constant as the value
                        ObjectModel.single(JsonType.STRING)
                    } else {
                        ObjectModel.single(JsonType.STRING)
                    }
                } else {
                    val nestedContext = if (resolvedType.typeArgs.isNotEmpty()) {
                        GenericContext(TypeResolver.resolveGenericParams(psiClass, resolvedType.typeArgs))
                    } else {
                        GenericContext.EMPTY
                    }
                    buildTypeObject(
                        psiClass, actionContext, engine, docHelper, cache,
                        option, maxDepth, depth, visited, nestedContext
                    ) ?: ObjectModel.emptyObject()
                }
            }

            is ResolvedType.UnresolvedType -> ObjectModel.single(JsonType.fromJavaType(resolvedType.canonicalText))
            is ResolvedType.WildcardType -> {
                resolvedType.upper?.let {
                    buildFieldValue(
                        it, actionContext, engine, docHelper, cache,
                        option, maxDepth, depth, visited
                    )
                } ?: ObjectModel.nullValue()
            }
        }
    }

    private fun getDefaultValueForPrimitive(kind: PrimitiveKind): ObjectModel.Single {
        return when (kind) {
            PrimitiveKind.BOOLEAN -> ObjectModel.single(JsonType.BOOLEAN)
            PrimitiveKind.BYTE -> ObjectModel.single(JsonType.INT)
            PrimitiveKind.CHAR -> ObjectModel.single(JsonType.STRING)
            PrimitiveKind.SHORT -> ObjectModel.single(JsonType.SHORT)
            PrimitiveKind.INT -> ObjectModel.single(JsonType.INT)
            PrimitiveKind.LONG -> ObjectModel.single(JsonType.LONG)
            PrimitiveKind.FLOAT -> ObjectModel.single(JsonType.FLOAT)
            PrimitiveKind.DOUBLE -> ObjectModel.single(JsonType.DOUBLE)
            PrimitiveKind.VOID -> ObjectModel.nullValue()
        }
    }

    private fun isCollection(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        return qualifiedName == "java.util.Collection" ||
                qualifiedName == "java.util.List" ||
                qualifiedName == "java.util.Set" ||
                qualifiedName == "java.util.ArrayList" ||
                qualifiedName == "java.util.HashSet" ||
                qualifiedName == "java.util.LinkedList"
    }

    private fun isMap(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        return qualifiedName == "java.util.Map" ||
                qualifiedName == "java.util.HashMap" ||
                qualifiedName == "java.util.LinkedHashMap" ||
                qualifiedName == "java.util.TreeMap"
    }

    private fun isEnum(psiClass: PsiClass): Boolean {
        return psiClass.isEnum || psiClass.supers.any { it.qualifiedName == "java.lang.Enum" }
    }

    private fun isSimpleType(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        return SpecialTypeHandler.isSpecialType(qualifiedName) ||
                qualifiedName == "java.lang.String" ||
                qualifiedName == "java.lang.Integer" ||
                qualifiedName == "java.lang.Long" ||
                qualifiedName == "java.lang.Double" ||
                qualifiedName == "java.lang.Float" ||
                qualifiedName == "java.lang.Boolean" ||
                qualifiedName == "java.lang.Byte" ||
                qualifiedName == "java.lang.Short" ||
                qualifiedName == "java.lang.Character" ||
                qualifiedName == "java.math.BigInteger" ||
                qualifiedName == "java.math.BigDecimal"
    }

    private fun getDefaultValueForSimpleType(psiClass: PsiClass): ObjectModel.Single {
        val qualifiedName = psiClass.qualifiedName ?: return ObjectModel.single(JsonType.OBJECT)
        return when (qualifiedName) {
            "java.lang.String", "java.lang.Character" -> ObjectModel.single(JsonType.STRING)
            "java.lang.Integer", "java.lang.Byte" -> ObjectModel.single(JsonType.INT)
            "java.lang.Long", "java.math.BigInteger" -> ObjectModel.single(JsonType.LONG)
            "java.lang.Float" -> ObjectModel.single(JsonType.FLOAT)
            "java.lang.Double", "java.math.BigDecimal" -> ObjectModel.single(JsonType.DOUBLE)
            "java.lang.Boolean" -> ObjectModel.single(JsonType.BOOLEAN)
            "java.lang.Short" -> ObjectModel.single(JsonType.SHORT)
            else -> {
                val specialDefault = SpecialTypeHandler.getDefaultValueForSpecialType(qualifiedName)
                if (specialDefault != null) {
                    ObjectModel.single(JsonType.STRING)
                } else {
                    ObjectModel.single(JsonType.OBJECT)
                }
            }
        }
    }

    /**
     * Apply field ordering based on field.order and field.order.with rules.
     *
     * - field.order: assigns a numeric order to each field independently
     * - field.order.with: comparator-style rule that receives two fields (a, b) via context
     *   extensions and returns an int for sorting (negative = a first, positive = b first)
     *
     * Returns a new ordered map if ordering rules are configured, null otherwise.
     */
    private suspend fun applyFieldOrdering(
        fields: LinkedHashMap<String, FieldModel>,
        accessibleFields: List<AccessibleField>,
        engine: RuleEngine
    ): LinkedHashMap<String, FieldModel>? {
        // First try field.order.with (comparator-style)
        val fieldByName = accessibleFields.associateBy { it.name }
        val hasOrderWith = accessibleFields.any { af ->
            // Probe: check if any rule is configured for field.order.with
            engine.evaluate(RuleKeys.FIELD_ORDER_WITH, af.psi) { ctx ->
                ctx.setExt("a", af.psi)
                ctx.setExt("b", af.psi)
            } != null
        }

        if (hasOrderWith) {
            val indexed = fields.entries.toList().withIndex().toList()
            val sorted = indexed.sortedWith { (index1, entry1), (index2, entry2) ->
                val af1 = fieldByName[entry1.key]
                val af2 = fieldByName[entry2.key]
                if (af1 != null && af2 != null) {
                    val result = kotlinx.coroutines.runBlocking {
                        engine.evaluate(RuleKeys.FIELD_ORDER_WITH, af1.psi) { ctx ->
                            ctx.setExt("a", af1.psi)
                            ctx.setExt("b", af2.psi)
                        }
                    }
                    result?.toIntOrNull() ?: index1.compareTo(index2)
                } else {
                    index1.compareTo(index2)
                }
            }
            val result = LinkedHashMap<String, FieldModel>()
            for ((_, entry) in sorted) {
                result[entry.key] = entry.value
            }
            return result
        }

        // Fall back to field.order (static numeric order)
        val fieldOrderMap = LinkedHashMap<String, String?>()
        var hasOrder = false
        for (af in accessibleFields) {
            val order = engine.evaluate(RuleKeys.FIELD_ORDER, af.psi)
            if (order != null) hasOrder = true
            fieldOrderMap[af.name] = order
        }
        if (!hasOrder) return null

        val sorted = fields.entries.sortedWith(Comparator { a, b ->
            val orderA = fieldOrderMap[a.key]?.toIntOrNull() ?: Int.MAX_VALUE
            val orderB = fieldOrderMap[b.key]?.toIntOrNull() ?: Int.MAX_VALUE
            orderA.compareTo(orderB)
        })
        val result = LinkedHashMap<String, FieldModel>()
        for (entry in sorted) {
            result[entry.key] = entry.value
        }
        return result
    }

    private fun mergeComments(vararg comments: String?): String? {
        return comments.filterNotNull().filter { it.isNotBlank() }.ifEmpty { null }?.joinToString(" ")
    }
}
