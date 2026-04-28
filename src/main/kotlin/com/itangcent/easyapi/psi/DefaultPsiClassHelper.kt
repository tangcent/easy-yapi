package com.itangcent.easyapi.psi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.itangcent.easyapi.cache.JsonConstructionCache
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.*
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.util.GsonUtils
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks the total number of elements processed during a single
 * [DefaultPsiClassHelper.buildObjectModel] invocation.
 *
 * Prevents OOM for deeply nested or circular class hierarchies
 * by enforcing a hard cap on the total number of fields expanded.
 * Mirrors the legacy `maxElements` guard from the old plugin.
 *
 * @param maxElements Maximum number of elements allowed (default 512)
 */
class ElementCounter(private val maxElements: Int = DefaultPsiClassHelper.DEFAULT_MAX_ELEMENTS) {
    private val count = AtomicInteger(0)

    /** Increments the counter and returns true if the limit has been exceeded. */
    fun incrementAndCheckExceeded(): Boolean {
        return count.incrementAndGet() > maxElements
    }

    /** Returns the current element count. */
    fun count(): Int = count.get()

    /** Returns true if the limit has been exceeded. */
    fun isExceeded(): Boolean = count.get() > maxElements
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
 * val model = helper.buildObjectModel(psiClass)
 * ```
 *
 * @see PsiClassHelper for the interface
 * @see ObjectModel for the model structure
 * @see TypeResolver for type resolution
 */
@Service(Service.Level.PROJECT)
class DefaultPsiClassHelper(private val project: Project) : PsiClassHelper {

    companion object : IdeaLog {
        /** Default max depth for nested type resolution. */
        const val DEFAULT_MAX_DEEP = 8

        /** Default max elements (total fields) per build operation. */
        const val DEFAULT_MAX_ELEMENTS = 512

        fun getInstance(project: Project): DefaultPsiClassHelper =
            project.getService(DefaultPsiClassHelper::class.java)
    }

    private val configReader: ConfigReader get() = ConfigReader.getInstance(project)

    /** Reads `max.deep` from config, falling back to [DEFAULT_MAX_DEEP]. */
    private fun maxDeep(): Int =
        configReader.getFirst("max.deep")?.toIntOrNull() ?: DEFAULT_MAX_DEEP

    /** Reads `max.elements` from config, falling back to [DEFAULT_MAX_ELEMENTS]. */
    private fun maxElements(): Int =
        configReader.getFirst("max.elements")?.toIntOrNull() ?: DEFAULT_MAX_ELEMENTS

    override suspend fun buildObjectModel(
        psiClass: PsiClass,
        option: Int
    ): ObjectModel? {
        val engine = RuleEngine.getInstance(project)
        val elementCounter = ElementCounter(maxElements())
        val maxDepth = maxDeep()

        val converted = engine.evaluate(
            RuleKeys.JSON_RULE_CONVERT,
            com.intellij.psi.util.PsiTypesUtil.getClassType(psiClass),
            psiClass
        )
        if (!converted.isNullOrBlank()) {
            return buildObjectModelFromConvertedType(
                convertedType = converted,
                contextElement = psiClass,
                option = option,
                maxDepth = maxDepth,
                genericContext = GenericContext.EMPTY,
                elementCounter = elementCounter
            )
        }

        val docHelper = UnifiedDocHelper.getInstance(project)
        val cache = JsonConstructionCache()
        val visited = HashSet<String>()
        return buildTypeObject(
            psiClass, engine, docHelper, cache,
            option, maxDepth, depth = 0, visited = visited,
            elementCounter = elementCounter
        )
    }

    override suspend fun buildObjectModel(
        resolvedType: ResolvedType,
        option: Int
    ): ObjectModel? {
        if (resolvedType is ResolvedType.PrimitiveType && resolvedType.kind == PrimitiveKind.VOID) return null
        val elementCounter = ElementCounter(maxElements())
        val maxDepth = maxDeep()
        val engine = RuleEngine.getInstance(project)

        // Apply json.rule.convert uniformly to all ResolvedType instances
        val qualifiedName = resolvedType.qualifiedName()
        LOG.info("buildObjectModel(resolvedType): evaluating json.rule.convert for: $qualifiedName")
        val converted = engine.evaluate(RuleKeys.JSON_RULE_CONVERT, resolvedType, resolvedType.contextElement())
        LOG.info("buildObjectModel(resolvedType): json.rule.convert result for '$qualifiedName': ${converted ?: "(null)"}")

        if (!converted.isNullOrBlank()) {
            val genericContext = if (resolvedType is ResolvedType.ClassType) {
                resolvedType.genericContext
            } else {
                GenericContext.EMPTY
            }
            val psiType = resolvedType.contextElement()?.let { element ->
                readSync {
                    when (element) {
                        is PsiClass -> com.intellij.psi.util.PsiTypesUtil.getClassType(element)
                        else -> null
                    }
                }
            }
            val result = buildObjectModelFromConvertedType(
                convertedType = converted,
                sourcePsiType = psiType,
                contextElement = resolvedType.contextElement(),
                option = option,
                maxDepth = maxDepth,
                genericContext = genericContext,
                elementCounter = elementCounter
            )
            LOG.info("buildObjectModel(resolvedType): converted '$qualifiedName' → '$converted' → model: $result")
            if (result != null) return result
        }

        val docHelper = UnifiedDocHelper.getInstance(project)
        val cache = JsonConstructionCache()
        val visited = HashSet<String>()
        return buildFieldValue(
            resolvedType, engine, docHelper, cache,
            option, maxDepth, depth = 0, visited = visited,
            elementCounter = elementCounter
        )
    }

    private suspend fun buildObjectModelFromConvertedType(
        resolvedType: ResolvedType,
        option: Int,
        maxDepth: Int,
        genericContext: GenericContext,
        elementCounter: ElementCounter
    ): ObjectModel? {
        val substitutedType = TypeResolver.substitute(resolvedType, genericContext)
        if (substitutedType is ResolvedType.PrimitiveType && substitutedType.kind == PrimitiveKind.VOID) return null

        val engine = RuleEngine.getInstance(project)
        val docHelper = UnifiedDocHelper.getInstance(project)
        val cache = JsonConstructionCache()
        val visited = HashSet<String>()

        return buildFieldValue(
            substitutedType, engine, docHelper, cache,
            option, maxDepth, depth = 0, visited = visited,
            elementCounter = elementCounter
        )
    }

    private suspend fun buildObjectModelFromConvertedType(
        convertedType: String,
        sourcePsiType: PsiType? = null,
        contextElement: PsiElement?,
        option: Int,
        maxDepth: Int,
        genericContext: GenericContext,
        elementCounter: ElementCounter
    ): ObjectModel? {
        val rawResolved = TypeResolver.resolveFromCanonicalText(convertedType, project, contextElement, genericContext)

        if (rawResolved is ResolvedType.UnresolvedType) {
            val trimmed = convertedType.trim()
            val collectionElementText = extractCollectionElementType(trimmed)
            if (collectionElementText != null) {
                val elementResolved =
                    resolveElementTypeFromSource(sourcePsiType, collectionElementText, genericContext, contextElement)
                        ?: TypeResolver.resolveFromCanonicalText(
                            collectionElementText,
                            project,
                            contextElement,
                            genericContext
                        )
                val elementModel = buildObjectModelFromConvertedType(
                    elementResolved, option, maxDepth, genericContext, elementCounter
                )
                return if (elementModel != null) ObjectModel.array(elementModel) else null
            }
        }

        return buildObjectModelFromConvertedType(
            rawResolved,
            option,
            maxDepth,
            genericContext,
            elementCounter
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
        engine: RuleEngine,
        docHelper: DocHelper,
        cache: JsonConstructionCache,
        option: Int,
        maxDepth: Int,
        depth: Int,
        visited: MutableSet<String>,
        genericContext: GenericContext = GenericContext.EMPTY,
        parentPath: String? = null,
        elementCounter: ElementCounter
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
        if (elementCounter.isExceeded()) {
            visited.remove(qualifiedName)
            return null
        }

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

        val accessibleFields = collectAccessibleFields(psiClass, option, genericContext)

        for (accessibleField in accessibleFields) {
            val fieldName = accessibleField.name
            if (fields.containsKey(fieldName)) continue

            // Build the field path for fieldContext injection into rule scripts
            val fieldPath = if (parentPath != null) "$parentPath.$fieldName" else fieldName

            if (engine.evaluate(RuleKeys.FIELD_IGNORE, accessibleField.psi, fieldContext = fieldPath)) {
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

            engine.evaluate(RuleKeys.JSON_FIELD_PARSE_BEFORE, accessibleField.psi, fieldContext = fieldPath)

            val customName = engine.evaluate(RuleKeys.FIELD_NAME, accessibleField.psi, fieldContext = fieldPath)
            val prefix =
                engine.evaluate(RuleKeys.FIELD_NAME_PREFIX, accessibleField.psi, fieldContext = fieldPath) ?: ""
            val suffix =
                engine.evaluate(RuleKeys.FIELD_NAME_SUFFIX, accessibleField.psi, fieldContext = fieldPath) ?: ""
            val baseName = customName?.takeIf { it.isNotBlank() } ?: fieldName
            val jsonFieldName = prefix + baseName + suffix

            if (fields.containsKey(jsonFieldName)) continue

            // Increment element counter and check if we've exceeded the limit
            if (elementCounter.incrementAndCheckExceeded()) {
                LOG.info(
                    "Element limit exceeded while parsing ${qualifiedName}. " +
                            "Processed ${elementCounter.count()} elements. Stopping field expansion."
                )
                break
            }

            val fieldDefaultValue =
                engine.evaluate(RuleKeys.FIELD_DEFAULT_VALUE, accessibleField.psi, fieldContext = fieldPath)
            val fieldRequired = engine.evaluate(RuleKeys.FIELD_REQUIRED, accessibleField.psi, fieldContext = fieldPath)
            val fieldMock = engine.evaluate(RuleKeys.FIELD_MOCK, accessibleField.psi, fieldContext = fieldPath)
            val fieldDemo = engine.evaluate(RuleKeys.FIELD_DEMO, accessibleField.psi, fieldContext = fieldPath)
            val fieldAdvancedStr =
                engine.evaluate(RuleKeys.FIELD_ADVANCED, accessibleField.psi, fieldContext = fieldPath)
            val fieldAdvanced = if (!fieldAdvancedStr.isNullOrBlank()) {
                runCatching { GsonUtils.fromJson<Map<String, Any?>>(fieldAdvancedStr) }.getOrNull()
            } else null

            val fieldComment = if (JsonOption.has(option, JsonOption.READ_COMMENT)) {
                val directComment = when (val psi = accessibleField.psi) {
                    is PsiField -> docHelper.getAttrOfField(psi)
                    else -> docHelper.getAttrOfDocComment(psi)
                }
                val ruleComment = engine.evaluate(RuleKeys.FIELD_DOC, accessibleField.psi, fieldContext = fieldPath)
                mergeComments(directComment, ruleComment)
            } else null

            val converted = engine.evaluate(RuleKeys.JSON_RULE_CONVERT, accessibleField.type, accessibleField.psi)
            val rawResolved: ResolvedType
            val fieldType: ResolvedType
            // Use the field's declaring context if available (per-level generic resolution),
            // falling back to the flat effectiveContext for backward compatibility.
            val fieldContext = accessibleField.declaringContext ?: effectiveContext
            if (!converted.isNullOrBlank()) {
                rawResolved = TypeResolver.resolveFromCanonicalText(
                    canonicalText = converted,
                    project = project,
                    contextElement = accessibleField.psi,
                    context = fieldContext
                )
                fieldType = TypeResolver.substitute(rawResolved, fieldContext)
            } else {
                rawResolved = TypeResolver.resolve(accessibleField.type, fieldContext)
                fieldType = TypeResolver.resolveAndSubstitute(accessibleField.type, fieldContext)
            }

            // A field is "generic" if its declared type is a type parameter (e.g., T in Result<T>)
            val isGenericField = rawResolved is ResolvedType.UnresolvedType
                    && (effectiveContext.genericMap.containsKey(rawResolved.canonicalText)
                    || fieldContext.genericMap.containsKey(rawResolved.canonicalText))

            val fieldModel = buildFieldModel(
                fieldType, engine, docHelper, cache,
                option, maxDepth, depth + 1, visited,
                fieldComment, fieldRequired, fieldDefaultValue, fieldMock, fieldDemo, fieldAdvanced,
                psiElement = accessibleField.psi,
                generic = isGenericField,
                elementCounter = elementCounter
            )

            // Check json.unwrapped — if true, merge the field's object fields into the parent
            val unwrapped = engine.evaluate(RuleKeys.JSON_UNWRAPPED, accessibleField.psi, fieldContext = fieldPath)
            if (unwrapped && fieldModel.model is ObjectModel.Object) {
                for ((nestedName, nestedField) in (fieldModel.model as ObjectModel.Object).fields) {
                    val unwrappedName = prefix + nestedName + suffix
                    if (!fields.containsKey(unwrappedName)) {
                        fields[unwrappedName] = nestedField
                    }
                }
            } else {
                fields[jsonFieldName] = fieldModel
            }

            engine.evaluate(RuleKeys.JSON_FIELD_PARSE_AFTER, accessibleField.psi, fieldContext = fieldPath)
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
        val psi: PsiElement,
        /** The generic context of the class that declares this field.
         *  For inherited fields, this is the context of the superclass at the level
         *  where the field is declared, with type parameters resolved through the
         *  full inheritance chain. This ensures that a field `T value` in Layer1<T>
         *  gets the correct binding for T at that level (e.g., Wrapper<Pair<String, Integer>>)
         *  rather than the bottom-level binding (e.g., Integer). */
        val declaringContext: GenericContext? = null
    )

    private fun collectAccessibleFields(psiClass: PsiClass, option: Int): List<AccessibleField> {
        return collectAccessibleFields(psiClass, option, GenericContext.EMPTY)
    }

    /**
     * Collects accessible fields by walking the class hierarchy level by level.
     *
     * Unlike using `psiClass.allFields` with a single flat generic context, this method
     * builds a per-level generic context for each class in the hierarchy. This ensures
     * that when different levels reuse the same type parameter name (e.g., `T`), each
     * field gets resolved with the correct binding for its declaring class.
     *
     * For example, in the chain:
     *   ConcreteLayer3 extends Layer3<Integer>
     *   Layer3<T> extends Layer2<Pair<String, T>>
     *   Layer2<T> extends Layer1<Wrapper<T>>
     *   Layer1<T> { T value; }
     *
     * Layer1's `value` field needs T=Wrapper<Pair<String, Integer>>, not T=Integer.
     * This method achieves this by building a fresh context at each level.
     */
    private fun collectAccessibleFields(
        psiClass: PsiClass,
        option: Int,
        genericContext: GenericContext
    ): List<AccessibleField> {
        val fields = mutableListOf<AccessibleField>()
        val fieldNames = mutableSetOf<String>()

        // First, collect fields from superclasses (with per-level generic contexts)
        collectSuperFields(psiClass, option, genericContext, fields, fieldNames)

        // Then collect this class's own declared fields
        val ownContext = buildOwnContext(psiClass, genericContext)
        for (field in psiClass.fields) {
            if (shouldIgnoreField(field)) continue
            val name = field.name
            if (fieldNames.add(name)) {
                fields.add(AccessibleField(name = name, type = field.type, psi = field, declaringContext = ownContext))
            }
        }

        // Collect getter-based properties
        try {
            if (JsonOption.has(option, JsonOption.READ_GETTER)) {
                for (method in psiClass.allMethods) {
                    if (isFromObject(method)) continue
                    if (isGetter(method)) {
                        val propertyName = getPropertyNameFromGetter(method.name)
                        if (fieldNames.add(propertyName)) {
                            val returnType = method.returnType ?: continue
                            val methodContext = resolveContextForMember(method, psiClass, genericContext)
                            fields.add(
                                AccessibleField(
                                    name = propertyName,
                                    type = returnType,
                                    psi = method,
                                    declaringContext = methodContext
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("failed collect fields from getters. class: ${psiClass.name}", e)
        }

        // Collect setter-based properties
        try {
            if (JsonOption.has(option, JsonOption.READ_SETTER)) {
                for (method in psiClass.allMethods) {
                    if (isFromObject(method)) continue
                    if (isSetter(method)) {
                        val propertyName = getPropertyNameFromSetter(method.name)
                        if (fieldNames.add(propertyName)) {
                            val paramType = method.parameterList.parameters.firstOrNull()?.type ?: continue
                            val methodContext = resolveContextForMember(method, psiClass, genericContext)
                            fields.add(
                                AccessibleField(
                                    name = propertyName,
                                    type = paramType,
                                    psi = method,
                                    declaringContext = methodContext
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("failed collect fields from setters. class: ${psiClass.name}", e)
        }

        return fields
    }

    /**
     * Recursively collects fields from superclasses, building per-level generic contexts.
     */
    private fun collectSuperFields(
        psiClass: PsiClass,
        option: Int,
        genericContext: GenericContext,
        fields: MutableList<AccessibleField>,
        fieldNames: MutableSet<String>
    ) {
        val ownContext = buildOwnContext(psiClass, genericContext)

        for (superType in psiClass.superTypes) {
            val classType = superType as? PsiClassType ?: continue
            val superClass = classType.resolve() ?: continue
            if (superClass.qualifiedName == "java.lang.Object") continue

            // Build the generic context for this superclass by resolving its type arguments
            // using the current class's context
            val superContext = buildSuperContext(superClass, classType, ownContext)

            // Recurse into the superclass first (depth-first, so base fields come first)
            collectSuperFields(superClass, option, superContext, fields, fieldNames)

            // Collect the superclass's own declared fields with its context
            for (field in superClass.fields) {
                if (shouldIgnoreField(field)) continue
                val name = field.name
                if (fieldNames.add(name)) {
                    val fieldContext = buildOwnContext(superClass, superContext)
                    fields.add(
                        AccessibleField(
                            name = name,
                            type = field.type,
                            psi = field,
                            declaringContext = fieldContext
                        )
                    )
                }
            }
        }
    }

    /**
     * Builds the generic context for the given class itself, resolving its own type parameters
     * from the provided external context.
     */
    private fun buildOwnContext(psiClass: PsiClass, externalContext: GenericContext): GenericContext {
        if (externalContext == GenericContext.EMPTY && psiClass.typeParameters.isEmpty()) {
            return GenericContext.EMPTY
        }
        val map = LinkedHashMap<String, ResolvedType>()
        for (tp in psiClass.typeParameters) {
            val name = tp.name ?: continue
            map[name] = externalContext.genericMap[name] ?: ResolvedType.UnresolvedType(name)
        }
        return if (map.isEmpty()) GenericContext.EMPTY else GenericContext(map)
    }

    /**
     * Builds the generic context for a superclass by resolving its type arguments
     * using the current class's context.
     *
     * For example, if Layer2<T> extends Layer1<Wrapper<T>> and T=Pair<String, Integer>,
     * this builds {T → Wrapper<Pair<String, Integer>>} for Layer1.
     */
    private fun buildSuperContext(
        superClass: PsiClass,
        superClassType: PsiClassType,
        currentContext: GenericContext
    ): GenericContext {
        val superParams = superClass.typeParameters
        val superArgs = superClassType.parameters
        if (superParams.isEmpty()) return GenericContext.EMPTY

        val map = LinkedHashMap<String, ResolvedType>()
        for (i in superParams.indices) {
            val name = superParams[i].name ?: continue
            val arg = superArgs.getOrNull(i)
            map[name] = TypeResolver.resolveAndSubstitute(arg, currentContext)
        }
        return GenericContext(map)
    }

    /**
     * Resolves the generic context for a method/getter/setter member by finding
     * which class in the hierarchy declares it and building the appropriate context.
     */
    private fun resolveContextForMember(
        method: PsiMethod,
        rootClass: PsiClass,
        rootContext: GenericContext
    ): GenericContext {
        val declaringClass = method.containingClass ?: return rootContext
        if (declaringClass == rootClass || declaringClass.qualifiedName == rootClass.qualifiedName) {
            return buildOwnContext(rootClass, rootContext)
        }
        // Walk the hierarchy to find the context for the declaring class
        return findContextForClass(rootClass, declaringClass, rootContext)
            ?: buildOwnContext(rootClass, rootContext)
    }

    /**
     * Walks the hierarchy from rootClass to find the generic context for targetClass.
     */
    private fun findContextForClass(
        currentClass: PsiClass,
        targetClass: PsiClass,
        currentContext: GenericContext
    ): GenericContext? {
        val ownContext = buildOwnContext(currentClass, currentContext)
        for (superType in currentClass.superTypes) {
            val classType = superType as? PsiClassType ?: continue
            val superClass = classType.resolve() ?: continue
            if (superClass.qualifiedName == "java.lang.Object") continue

            val superContext = buildSuperContext(superClass, classType, ownContext)
            if (superClass.qualifiedName == targetClass.qualifiedName) {
                return superContext
            }
            val found = findContextForClass(superClass, targetClass, superContext)
            if (found != null) return found
        }
        return null
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
        generic: Boolean = false,
        elementCounter: ElementCounter
    ): FieldModel {
        val model = buildFieldValue(
            resolvedType, engine, docHelper, cache,
            option, maxDepth, depth, visited,
            elementCounter = elementCounter
        )
        var options = resolveFieldOptions(resolvedType, docHelper)
        if (options == null && psiElement != null) {
            options = SeeTagResolver(project).resolveOptions(psiElement, docHelper)
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
    private suspend fun resolveFieldOptions(
        resolvedType: ResolvedType,
        docHelper: DocHelper
    ): List<FieldOption>? {
        return when (resolvedType) {
            is ResolvedType.ClassType -> {
                val psiClass = resolvedType.psiClass
                if (isEnum(psiClass)) {
                    resolveEnumOptions(psiClass, docHelper)
                } else if (isCollection(psiClass)) {
                    resolvedType.typeArgs.firstOrNull()?.let {
                        resolveFieldOptions(it, docHelper)
                    }
                } else null
            }

            is ResolvedType.ArrayType -> resolveFieldOptions(resolvedType.componentType, docHelper)
            is ResolvedType.WildcardType -> resolvedType.upper?.let {
                resolveFieldOptions(it, docHelper)
            }

            else -> null
        }
    }

    /**
     * Resolves the effective enum field name for a given enum class.
     *
     * This is used for **Case 1: field declared as enum type** (`XxxEnum field`).
     * Frameworks like Spring serialize enums by name by default, so the fallback is `"name()"`.
     *
     * Resolution order:
     * 1. Explicit `enum.use.custom` rule → use that field name directly
     *    - `"name"` / `"name()"` → enum constant name
     *    - `"ordinal"` / `"ordinal()"` → enum ordinal
     *    - any other string → that instance field name (e.g. `"code"`)
     * 2. Fallback → `"name()"` (enum constant name as String)
     *
     * Note: auto-match by type is NOT applied here. It only makes sense for
     * Case 2 (normal-typed field with `@see EnumClass`), which is handled by [SeeTagResolver].
     */
    private suspend fun resolveEnumFieldName(
        psiClass: PsiClass,
        engine: RuleEngine
    ): String {
        val custom = engine.evaluate(RuleKeys.ENUM_USE_CUSTOM, psiClass)
        if (!custom.isNullOrBlank()) return custom
        return "name()"
    }

    private suspend fun resolveEnumOptions(
        psiClass: PsiClass,
        docHelper: DocHelper
    ): List<FieldOption>? {
        val constants = psiClass.fields.filterIsInstance<PsiEnumConstant>()
        if (constants.isEmpty()) return null

        val engine = RuleEngine.getInstance(project)
        val fieldName = resolveEnumFieldName(psiClass, engine)

        return resolveEnumOptionsByField(psiClass, constants, fieldName, docHelper)
    }

    /**
     * Resolves enum options using a specific field's constructor argument values.
     *
     * Handles special pseudo-fields:
     * - `"name"` / `"name()"` → enum constant name (String)
     * - `"ordinal"` / `"ordinal()"` → enum ordinal index (Integer)
     * - any other string → looks up that instance field's constructor argument
     *
     * For example, given:
     * ```java
     * enum UserType {
     *     GUEST(30, "unspecified"),
     *     ADMIN(1100, "administrator");
     *     private final Integer code;
     *     private final String desc;
     * }
     * ```
     * With `fieldName = "code"`, produces options: `[{value=30, desc="unspecified"}, ...]`
     */
    private suspend fun resolveEnumOptionsByField(
        psiClass: PsiClass,
        constants: List<PsiEnumConstant>,
        fieldName: String,
        docHelper: DocHelper
    ): List<FieldOption>? {
        val normalizedName = fieldName.removeSuffix("()")

        // Handle pseudo-fields: name and ordinal
        if (normalizedName == "name") {
            return constants.mapNotNull { constant ->
                val name = constant.name ?: return@mapNotNull null
                val desc = docHelper.getAttrOfDocComment(constant)
                FieldOption(value = name, desc = desc)
            }.ifEmpty { null }
        }

        if (normalizedName == "ordinal") {
            return constants.mapIndexedNotNull { index, constant ->
                constant.name ?: return@mapIndexedNotNull null
                val desc = docHelper.getAttrOfDocComment(constant)
                    ?: constant.name
                FieldOption(value = index, desc = desc)
            }.ifEmpty { null }
        }

        // Instance field lookup
        val instanceFields = readSync {
            psiClass.allFields
                .filter { it !is PsiEnumConstant && !it.hasModifierProperty(PsiModifier.STATIC) }
        }
        val fieldIndex = instanceFields.indexOfFirst { it.name == normalizedName }
        if (fieldIndex < 0) {
            // Field not found — fall back to name
            return constants.mapNotNull { constant ->
                val name = constant.name ?: return@mapNotNull null
                val desc = docHelper.getAttrOfDocComment(constant)
                FieldOption(value = name, desc = desc)
            }.ifEmpty { null }
        }

        return constants.mapNotNull { constant ->
            val name = constant.name ?: return@mapNotNull null
            val desc = docHelper.getAttrOfDocComment(constant)

            val value: Any? = readSync {
                val args = constant.argumentList?.expressions
                if (args != null && fieldIndex < args.size) {
                    SeeTagResolver.extractConstantValue(args[fieldIndex])
                } else name
            }

            // Build description: prefer doc comment, then fall back to other field values
            val effectiveDesc = desc ?: buildDescFromOtherFields(constant, instanceFields, fieldIndex)

            FieldOption(value = value ?: name, desc = effectiveDesc)
        }.ifEmpty { null }
    }

    /**
     * Builds a description string from enum constructor arguments other than the value field.
     */
    private fun buildDescFromOtherFields(
        constant: PsiEnumConstant,
        instanceFields: List<PsiField>,
        excludeIndex: Int
    ): String? {
        val args = constant.argumentList?.expressions ?: return null
        val parts = mutableListOf<String>()
        for ((i, field) in instanceFields.withIndex()) {
            if (i == excludeIndex) continue
            if (i < args.size) {
                val value = SeeTagResolver.extractConstantValue(args[i])
                if (value != null) {
                    parts.add(value.toString())
                }
            }
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    /**
     * Determines the JSON type for an enum based on the resolved field.
     *
     * - `"name"` / `"name()"` → STRING
     * - `"ordinal"` / `"ordinal()"` → INT
     * - instance field name → type of that field (e.g. `Integer code` → INT)
     */
    private fun resolveEnumJsonType(psiClass: PsiClass, fieldName: String): ObjectModel.Single {
        val normalizedName = fieldName.removeSuffix("()")

        if (normalizedName == "name") return ObjectModel.single(JsonType.STRING)
        if (normalizedName == "ordinal") return ObjectModel.single(JsonType.INT)

        val field = readSync {
            psiClass.allFields.firstOrNull {
                it !is PsiEnumConstant && it.name == normalizedName
            }
        }
        if (field != null) {
            val typeName = readSync { field.type.canonicalText }
            return ObjectModel.single(JsonType.fromJavaType(typeName))
        }
        return ObjectModel.single(JsonType.STRING)
    }

    private suspend fun buildFieldValue(
        resolvedType: ResolvedType,
        engine: RuleEngine,
        docHelper: DocHelper,
        cache: JsonConstructionCache,
        option: Int,
        maxDepth: Int,
        depth: Int,
        visited: MutableSet<String>,
        elementCounter: ElementCounter
    ): ObjectModel {
        if (elementCounter.isExceeded()) {
            return ObjectModel.emptyObject()
        }
        return when (resolvedType) {
            is ResolvedType.PrimitiveType -> getDefaultValueForPrimitive(resolvedType.kind)
            is ResolvedType.ArrayType -> {
                val componentModel = buildFieldValue(
                    resolvedType.componentType,
                    engine, docHelper, cache, option, maxDepth, depth, visited,
                    elementCounter = elementCounter
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
                            elementType, engine, docHelper, cache,
                            option, maxDepth, depth, visited,
                            elementCounter = elementCounter
                        )
                    } else ObjectModel.single(JsonType.OBJECT)
                    ObjectModel.array(elementModel)
                } else if (isMap(psiClass)) {
                    val keyType = resolvedType.typeArgs.getOrNull(0)
                    val valueType = resolvedType.typeArgs.getOrNull(1)
                    val keyModel = if (keyType != null) {
                        buildFieldValue(
                            keyType, engine, docHelper, cache,
                            option, maxDepth, depth, visited,
                            elementCounter = elementCounter
                        )
                    } else ObjectModel.single(JsonType.STRING)
                    val valueModel = if (valueType != null) {
                        buildFieldValue(
                            valueType, engine, docHelper, cache,
                            option, maxDepth, depth, visited,
                            elementCounter = elementCounter
                        )
                    } else ObjectModel.single(JsonType.OBJECT)
                    ObjectModel.map(keyModel, valueModel)
                } else if (isEnum(psiClass)) {
                    val fieldName = resolveEnumFieldName(psiClass, engine)
                    resolveEnumJsonType(psiClass, fieldName)
                } else {
                    val nestedContext = if (resolvedType.typeArgs.isNotEmpty()) {
                        GenericContext(TypeResolver.resolveGenericParams(psiClass, resolvedType.typeArgs))
                    } else {
                        GenericContext.EMPTY
                    }
                    buildTypeObject(
                        psiClass, engine, docHelper, cache,
                        option, maxDepth, depth, visited, nestedContext,
                        elementCounter = elementCounter
                    ) ?: ObjectModel.emptyObject()
                }
            }

            is ResolvedType.UnresolvedType -> ObjectModel.single(JsonType.fromJavaType(resolvedType.canonicalText))
            is ResolvedType.WildcardType -> {
                resolvedType.upper?.let {
                    buildFieldValue(
                        it, engine, docHelper, cache,
                        option, maxDepth, depth, visited,
                        elementCounter = elementCounter
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

    private fun isCollection(psiClass: PsiClass): Boolean = InheritanceHelper.isCollection(psiClass)

    private fun isMap(psiClass: PsiClass): Boolean = InheritanceHelper.isMap(psiClass)

    private fun isEnum(psiClass: PsiClass): Boolean = readSync {
        psiClass.isEnum || psiClass.supers.any { it.qualifiedName == ClassNameConstants.JAVA_LANG_ENUM }
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
                if (SpecialTypeHandler.isFileType(qualifiedName)) {
                    ObjectModel.single(JsonType.FILE)
                } else {
                    val specialDefault = SpecialTypeHandler.getDefaultValueForSpecialType(qualifiedName)
                    if (specialDefault != null) {
                        ObjectModel.single(JsonType.STRING)
                    } else {
                        ObjectModel.single(JsonType.OBJECT)
                    }
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
