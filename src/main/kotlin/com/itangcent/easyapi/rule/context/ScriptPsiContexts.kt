package com.itangcent.easyapi.rule.context

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.psi.type.InheritanceHelper
import com.itangcent.easyapi.psi.type.ResolvedField
import com.itangcent.easyapi.psi.type.ResolvedMethod
import com.itangcent.easyapi.psi.type.ResolvedParam
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.SpecialTypeHandler
import com.itangcent.easyapi.psi.type.TypeResolver
import kotlinx.coroutines.runBlocking

//region Context Type Interfaces

/**
 * Interface defining the contract for contextType "class".
 *
 * Implemented by [ScriptPsiClassContext] and its subclasses.
 * Provides class-specific operations for scripts including:
 * - Method and field access
 * - Type checking (isMap, isCollection, isArray, etc.)
 * - Inheritance queries (extends, implements, superClass)
 * - JSON serialization (toJson, toJson5)
 */
interface ClassContext {
    fun methods(): Array<ScriptPsiMethodContext>
    fun methodCnt(): Int
    fun fields(): Array<ScriptPsiFieldContext>
    fun fieldCnt(): Int
    fun type(): ScriptTypeContext
    fun isExtend(superClass: String): Boolean
    fun isMap(): Boolean
    fun isCollection(): Boolean
    fun isArray(): Boolean
    fun isInterface(): Boolean
    fun isAnnotationType(): Boolean
    fun isEnum(): Boolean
    fun isPrimitive(): Boolean
    fun isPrimitiveWrapper(): Boolean
    fun isNormalType(): Boolean
    fun qualifiedName(): String?
    fun packageName(): String?
    fun isPublic(): Boolean
    fun isProtected(): Boolean
    fun isPrivate(): Boolean
    fun isPackagePrivate(): Boolean
    fun isInnerClass(): Boolean
    fun isStatic(): Boolean
    fun outerClass(): ScriptPsiClassContext?
    fun superClass(): ScriptPsiClassContext?
    fun extends(): Array<ScriptPsiClassContext>?
    fun implements(): Array<ScriptPsiClassContext>?
    fun toJson(): String
    fun toJson5(): String
}

/**
 * Interface defining the contract for contextType "method".
 *
 * Implemented by [ScriptPsiMethodContext] and its subclasses.
 * Provides method-specific operations for scripts including:
 * - Return type and parameter access
 * - Containing class access
 * - Method modifier checks
 */
interface MethodContext {
    fun returnType(): ScriptTypeContext?
    fun type(): ScriptTypeContext?
    fun isVarArgs(): Boolean
    fun args(): Array<ScriptPsiParameterContext>
    fun params(): Array<ScriptPsiParameterContext>
    fun parameters(): Array<ScriptPsiParameterContext>
    fun argTypes(): Array<ScriptTypeContext>
    fun argCnt(): Int
    fun paramCnt(): Int
    fun containingClass(): ScriptPsiClassContext?
    fun defineClass(): ScriptPsiClassContext?
    fun isEnumField(): Boolean
    fun isConstructor(): Boolean
    fun isOverride(): Boolean
    fun throwsExceptions(): Array<String>
    fun isDefault(): Boolean
    fun isAbstract(): Boolean
    fun isSynchronized(): Boolean
    fun isNative(): Boolean
    /**
     * Returns the underlying field name derived from this method's name.
     *
     * - `getCode` → `code`
     * - `isActive` → `active`
     * - `name` (or any non-getter/non-is method) → returned as-is
     *
     * Useful for mapping getter methods to their backing instance fields
     * (e.g. Jackson `@JsonValue` on a getter).
     */
    fun fieldName(): String
}

/**
 * Interface defining the contract for contextType "field".
 *
 * Implemented by [ScriptPsiFieldContext] and its subclasses.
 * Provides field-specific operations for scripts including:
 * - Type access
 * - Containing class access
 * - Field modifier checks
 */
interface FieldContext {
    fun type(): ScriptTypeContext
    fun jsonType(): ScriptTypeContext
    fun containingClass(): ScriptPsiClassContext?
    fun defineClass(): ScriptPsiClassContext?
    fun isEnumField(): Boolean
    fun asEnumField(): ScriptPsiEnumConstantContext?
    fun isStatic(): Boolean
    fun isFinal(): Boolean
    fun isTransient(): Boolean
    fun isVolatile(): Boolean
    fun constantValue(): Any?
}

/**
 * Interface defining the contract for contextType "param".
 *
 * Implemented by [ScriptPsiParameterContext] and its subclasses.
 * Provides parameter-specific operations for scripts including:
 * - Type access
 * - Varargs detection
 * - Declaring method access
 */
interface ParameterContext {
    fun type(): ScriptTypeContext
    fun jsonType(): ScriptTypeContext
    fun isVarArgs(): Boolean
    fun isFinal(): Boolean
    fun method(): ScriptPsiMethodContext?
    fun declaration(): ScriptItContext?
}

//endregion

/**
 * Converts a [RuleContext] to a script-friendly context based on the element type.
 *
 * Returns the appropriate context class:
 * - [ScriptPsiClassContext] for PsiClass elements
 * - [ScriptPsiMethodContext] for PsiMethod elements
 * - [ScriptPsiFieldContext] for PsiField elements
 * - [ScriptPsiParameterContext] for PsiParameter elements
 * - [ScriptItContext] for other or null elements
 */
fun RuleContext.asScriptIt(): Any {
    // If a PsiType is set (e.g., for json.rule.convert), expose it as a type context
    if (psiType != null && element == null) {
        return ScriptPsiTypeContext(this, psiType)
    }
    // Dispatch on core first — resolved elements take priority
    return when (core) {
        is ResolvedMethod -> ScriptResolvedMethodContext(this, core)
        is ResolvedField -> ScriptResolvedFieldContext(this, core)
        is ResolvedParam -> ScriptResolvedParameterContext(this, core)
        is ResolvedType.ClassType -> ScriptResolvedClassContext(this, core)
        // Fall through to PSI dispatch
        is PsiClass -> ScriptPsiClassContext(this)
        is PsiMethod -> ScriptPsiMethodContext(this)
        is PsiField -> ScriptPsiFieldContext(this)
        is PsiParameter -> ScriptPsiParameterContext(this)
        null -> ScriptItContext(this)
        else -> ScriptItContext(this)
    }
}

/**
 * Script context for [PsiClass] elements.
 *
 * Provides class-specific operations for scripts:
 * - Method and field access
 * - Type checking (isMap, isCollection, isArray, etc.)
 * - Inheritance queries (extends, implements, superClass)
 *
 * ## Usage in Scripts
 * ```
 * // Get class name
 * it.name()
 *
 * // Check if class extends another
 * it.isExtend("java.lang.Exception")
 *
 * // Get all methods
 * it.methods().each { m -> logger.info(m.name()) }
 *
 * // Check if it's a Map
 * if (it.isMap()) { ... }
 * ```
 */
open class ScriptPsiClassContext(context: RuleContext) : ScriptItContext(context), ClassContext {

    private fun psiClass(): PsiClass = context.element as PsiClass

    override fun methods(): Array<ScriptPsiMethodContext> = readSync {
        val cls = psiClass()
        val methods = cls.allMethods
        Array(methods.size) { i ->
            ScriptPsiMethodInClassContext(context.withElement(methods[i]), cls)
        }
    }

    override fun methodCnt(): Int = readSync { psiClass().allMethods.size }

    override fun fields(): Array<ScriptPsiFieldContext> = readSync {
        val cls = psiClass()
        val fields = cls.allFields
        Array(fields.size) { i ->
            ScriptPsiFieldInClassContext(context.withElement(fields[i]), cls)
        }
    }

    override fun fieldCnt(): Int = readSync { psiClass().allFields.size }

    override fun type(): ScriptTypeContext {
        return ScriptTypeContext(context, ResolvedType.ClassType(psiClass()))
    }

    override fun isExtend(superClass: String): Boolean =
        InheritanceHelper.isInheritor(psiClass(), superClass)

    override fun isMap(): Boolean =
        InheritanceHelper.isMap(psiClass())

    override fun isCollection(): Boolean =
        InheritanceHelper.isCollection(psiClass())

    override fun isArray(): Boolean = name().endsWith("[]")

    override fun isInterface(): Boolean = readSync { psiClass().isInterface }

    override fun isAnnotationType(): Boolean = readSync { psiClass().isAnnotationType }

    override fun isEnum(): Boolean = readSync { psiClass().isEnum }

    override fun isPrimitive(): Boolean = SpecialTypeHandler.isPrimitive(name())

    override fun isPrimitiveWrapper(): Boolean = SpecialTypeHandler.isPrimitiveWrapper(name())

    override fun isNormalType(): Boolean {
        val n = name()
        return isPrimitive() || isPrimitiveWrapper() || n == "java.lang.String" || n == "java.lang.Object"
    }

    override fun qualifiedName(): String? = readSync { psiClass().qualifiedName }

    override fun packageName(): String? = readSync { psiClass().qualifiedName?.substringBeforeLast('.', "") }

    override fun isPublic(): Boolean = readSync { psiClass().hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC) }

    override fun isProtected(): Boolean = readSync { psiClass().hasModifierProperty(com.intellij.psi.PsiModifier.PROTECTED) }

    override fun isPrivate(): Boolean = readSync { psiClass().hasModifierProperty(com.intellij.psi.PsiModifier.PRIVATE) }

    override fun isPackagePrivate(): Boolean = readSync {
        val cls = psiClass()
        !cls.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC) &&
                !cls.hasModifierProperty(com.intellij.psi.PsiModifier.PROTECTED) &&
                !cls.hasModifierProperty(com.intellij.psi.PsiModifier.PRIVATE)
    }

    override fun isInnerClass(): Boolean = readSync { psiClass().containingClass != null }

    override fun isStatic(): Boolean = readSync { psiClass().hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) }

    override fun outerClass(): ScriptPsiClassContext? = readSync {
        psiClass().containingClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    override fun superClass(): ScriptPsiClassContext? = readSync {
        val cls = psiClass()
        if (cls.isInterface || cls.isAnnotationType || cls.isEnum) return@readSync null
        extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let { return@readSync it }
        cls.superClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    override fun extends(): Array<ScriptPsiClassContext>? = readSync {
        psiClass().extendsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    override fun implements(): Array<ScriptPsiClassContext>? = readSync {
        psiClass().implementsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    override fun toJson(): String {
        val helper = PsiClassHelper.getInstance(context.project)
        val model = runBlocking {
            helper.buildObjectModel(ResolvedType.ClassType(psiClass()), JsonOption.READ_GETTER_OR_SETTER)
        }
        return ObjectModelJsonConverter.toJson(model)
    }

    override fun toJson5(): String {
        val helper = PsiClassHelper.getInstance(context.project)
        val model = runBlocking {
            helper.buildObjectModel(ResolvedType.ClassType(psiClass()), JsonOption.ALL)
        }
        return ObjectModelJsonConverter.toJson5(model)
    }

    override fun canonicalText(): String = qualifiedName() ?: name()

    override fun contextType(): String = "class"
}

/**
 * Script context for [PsiMethod] elements.
 *
 * Provides method-specific operations for scripts:
 * - Return type access
 * - Parameter access
 * - Containing class access
 *
 * ## Usage in Scripts
 * ```
 * // Get method name
 * it.name()
 *
 * // Get return type
 * it.returnType().name()
 *
 * // Get parameters
 * it.args().each { p -> logger.info(p.name()) }
 *
 * // Get containing class
 * it.containingClass().name()
 * ```
 */
open class ScriptPsiMethodContext(context: RuleContext) : ScriptItContext(context), MethodContext {

    protected fun psiMethod(): PsiMethod = context.element as PsiMethod

    override fun returnType(): ScriptTypeContext? = readSync {
        val type = psiMethod().returnType ?: return@readSync null
        ScriptTypeContext(context, TypeResolver.resolve(type))
    }

    override fun type(): ScriptTypeContext? = returnType()

    override fun isVarArgs(): Boolean = readSync { psiMethod().isVarArgs }

    override fun args(): Array<ScriptPsiParameterContext> = readSync {
        val params = psiMethod().parameterList.parameters
        Array(params.size) { i -> ScriptPsiParameterContext(context.withElement(params[i])) }
    }

    override fun params(): Array<ScriptPsiParameterContext> = args()

    override fun parameters(): Array<ScriptPsiParameterContext> = args()

    override fun argTypes(): Array<ScriptTypeContext> = readSync {
        val params = psiMethod().parameterList.parameters
        Array(params.size) { i -> ScriptTypeContext(context, TypeResolver.resolve(params[i].type)) }
    }

    override fun argCnt(): Int = readSync { psiMethod().parameterList.parametersCount }

    override fun paramCnt(): Int = argCnt()

    override fun containingClass(): ScriptPsiClassContext? = readSync {
        val cls = psiMethod().containingClass ?: return@readSync null
        ScriptPsiClassContext(context.withElement(cls))
    }

    override fun defineClass(): ScriptPsiClassContext? = containingClass()

    /* for backward compatibility only */
    override fun isEnumField(): Boolean = false

    override fun isConstructor(): Boolean = readSync { psiMethod().isConstructor }

    override fun isOverride(): Boolean = readSync {
        val method = psiMethod()
        method.modifierList.findAnnotation("java.lang.Override") != null ||
                method.findSuperMethods().isNotEmpty()
    }

    override fun throwsExceptions(): Array<String> = readSync {
        psiMethod().throwsList.referencedTypes.map { it.canonicalText }.toTypedArray()
    }

    override fun isDefault(): Boolean = readSync { psiMethod().hasModifierProperty(com.intellij.psi.PsiModifier.DEFAULT) }

    override fun isAbstract(): Boolean = readSync { psiMethod().hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT) }

    override fun isSynchronized(): Boolean =
        readSync { psiMethod().hasModifierProperty(com.intellij.psi.PsiModifier.SYNCHRONIZED) }

    override fun isNative(): Boolean = readSync { psiMethod().hasModifierProperty(com.intellij.psi.PsiModifier.NATIVE) }

    override fun fieldName(): String {
        val n = name()
        return when {
            n.startsWith("get") && n.length > 3 -> n.substring(3, 4).lowercase() + n.substring(4)
            n.startsWith("is") && n.length > 2 -> n.substring(2, 3).lowercase() + n.substring(3)
            else -> n
        }
    }

    override fun canonicalText(): String {
        val cls = readSync { psiMethod().containingClass?.qualifiedName } ?: ""
        return "$cls#${name()}"
    }

    override fun contextType(): String = "method"

    override fun toString(): String = canonicalText()
}

class ScriptPsiMethodInClassContext(
    context: RuleContext,
    private val containingClass: PsiClass
) : ScriptPsiMethodContext(context) {

    override fun containingClass(): ScriptPsiClassContext {
        return ScriptPsiClassContext(context.withElement(containingClass))
    }

    override fun defineClass(): ScriptPsiClassContext? {
        return psiMethod().containingClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }
}

/**
 * Script context for [PsiField] elements.
 *
 * Provides field-specific operations for scripts:
 * - Type access
 * - Containing class access
 * - Enum field detection
 *
 * ## Usage in Scripts
 * ```
 * // Get field name
 * it.name()
 *
 * // Get field type
 * it.type().name()
 *
 * // Get containing class
 * it.containingClass().name()
 *
 * // Check if enum field
 * if (it.isEnumField()) { ... }
 * ```
 */
open class ScriptPsiFieldContext(context: RuleContext) : ScriptItContext(context), FieldContext {

    protected fun psiField(): PsiField = context.element as PsiField

    override fun type(): ScriptTypeContext = readSync {
        ScriptTypeContext(context, TypeResolver.resolve(psiField().type))
    }

    override fun jsonType(): ScriptTypeContext = type()

    override fun containingClass(): ScriptPsiClassContext? = readSync {
        val cls = psiField().containingClass ?: return@readSync null
        ScriptPsiClassContext(context.withElement(cls))
    }

    override fun defineClass(): ScriptPsiClassContext? = containingClass()

    override fun isEnumField(): Boolean = readSync { psiField() is PsiEnumConstant }

    override fun asEnumField(): ScriptPsiEnumConstantContext? = readSync {
        (psiField() as? PsiEnumConstant)?.let { ScriptPsiEnumConstantContext(context, it) }
    }

    override fun isStatic(): Boolean = readSync { psiField().hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) }

    override fun isFinal(): Boolean = readSync { psiField().hasModifierProperty(com.intellij.psi.PsiModifier.FINAL) }

    override fun isTransient(): Boolean = readSync { psiField().hasModifierProperty(com.intellij.psi.PsiModifier.TRANSIENT) }

    override fun isVolatile(): Boolean = readSync { psiField().hasModifierProperty(com.intellij.psi.PsiModifier.VOLATILE) }

    override fun constantValue(): Any? = readSync {
        val field = psiField()
        if (field.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) &&
            field.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)
        ) {
            field.computeConstantValue()
        } else {
            null
        }
    }

    override fun canonicalText(): String {
        val cls = readSync { psiField().containingClass?.qualifiedName } ?: ""
        return "$cls#${name()}"
    }

    override fun contextType(): String = "field"

    override fun toString(): String = canonicalText()
}

class ScriptPsiFieldInClassContext(
    context: RuleContext,
    private val containingClass: PsiClass
) : ScriptPsiFieldContext(context) {

    override fun containingClass(): ScriptPsiClassContext {
        return ScriptPsiClassContext(context.withElement(containingClass))
    }

    override fun defineClass(): ScriptPsiClassContext? {
        return psiField().containingClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }
}

/**
 * Script context for [PsiParameter] elements.
 *
 * Provides parameter-specific operations for scripts:
 * - Type access
 * - Varargs detection
 * - Declaring method access
 *
 * ## Usage in Scripts
 * ```
 * // Get parameter name
 * it.name()
 *
 * // Get parameter type
 * it.type().name()
 *
 * // Check if varargs
 * if (it.isVarArgs()) { ... }
 *
 * // Get declaring method
 * it.method().name()
 * ```
 */
open class ScriptPsiParameterContext(context: RuleContext) : ScriptItContext(context), ParameterContext {

    private fun psiParameter(): PsiParameter = context.element as PsiParameter

    override fun type(): ScriptTypeContext = readSync {
        ScriptTypeContext(context, TypeResolver.resolve(psiParameter().type))
    }

    override fun jsonType(): ScriptTypeContext = type()

    override fun isVarArgs(): Boolean = readSync { psiParameter().isVarArgs }

    override fun isFinal(): Boolean = readSync { psiParameter().hasModifierProperty(com.intellij.psi.PsiModifier.FINAL) }

    /**
     * Returns the method which declares this parameter. May be null.
     */
    override fun method(): ScriptPsiMethodContext? = readSync {
        val scope = psiParameter().declarationScope
        if (scope is PsiMethod) ScriptPsiMethodContext(context.withElement(scope)) else null
    }

    /**
     * Returns the element which declares this parameter.
     */
    override fun declaration(): ScriptItContext? = readSync {
        val scope = psiParameter().declarationScope
        when (scope) {
            is PsiMethod -> ScriptPsiMethodContext(context.withElement(scope))
            else -> ScriptItContext(context.withElement(scope))
        }
    }

    override fun canonicalText(): String = readSync {
        val param = psiParameter()
        val scope = param.declarationScope
        if (scope is PsiMethod) {
            val cls = scope.containingClass?.qualifiedName ?: ""
            "$cls#${scope.name}.${param.name}"
        } else param.name ?: ""
    }

    override fun contextType(): String = "param"

    override fun toString(): String = canonicalText()
}

/**
 * Script context for type information.
 *
 * Wraps a [ResolvedType] to provide type-specific operations for scripts:
 * - Name resolution (full and simple names)
 * - Type checking (isMap, isCollection, isArray, etc.)
 * - Inheritance queries
 * - Method and field access
 *
 * ## Usage in Scripts
 * ```
 * // Get full type name
 * it.type().name() // "java.util.List<java.lang.String>"
 *
 * // Get simple type name
 * it.type().simpleName() // "List"
 *
 * // Check type properties
 * if (it.type().isCollection()) { ... }
 * if (it.type().isArray()) { ... }
 *
 * // Check inheritance
 * if (it.type().isExtend("java.lang.Exception")) { ... }
 * ```
 */
class ScriptTypeContext(private val context: RuleContext, private val resolvedType: ResolvedType) {

    fun name(): String = readSync { resolvedType.qualifiedName() }

    fun getName(): String = name()

    fun simpleName(): String = readSync { resolvedType.simpleName() }

    fun getSimpleName(): String = simpleName()

    fun psi(): Any = resolvedType

    fun contextType(): String = "class"

    fun methods(): Array<ScriptPsiMethodContext> = readSync {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readSync emptyArray()
        val methods = classType.suitableMethods()
        Array(methods.size) { i -> ScriptResolvedMethodContext(context, methods[i]) }
    }

    fun fields(): Array<ScriptPsiFieldContext> = readSync {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readSync emptyArray()
        val fields = classType.suitableFields()
        Array(fields.size) { i -> ScriptResolvedFieldContext(context, fields[i]) }
    }

    override fun toString(): String = resolvedType.qualifiedName()

    fun isExtend(superClass: String): Boolean = when (resolvedType) {
        is ResolvedType.ClassType -> InheritanceHelper.isInheritor(resolvedType.psiClass, superClass)
        else -> false
    }

    fun isMap(): Boolean = when (resolvedType) {
        is ResolvedType.ClassType -> InheritanceHelper.isMap(resolvedType.psiClass)
        else -> false
    }

    fun isCollection(): Boolean = when (resolvedType) {
        is ResolvedType.ClassType -> InheritanceHelper.isCollection(resolvedType.psiClass)
        is ResolvedType.ArrayType -> true
        else -> false
    }

    fun isArray(): Boolean {
        return resolvedType is ResolvedType.ArrayType
    }

    fun isPrimitive(): Boolean {
        return resolvedType is ResolvedType.PrimitiveType
    }

    fun isPrimitiveWrapper(): Boolean {
        return when (resolvedType) {
            is ResolvedType.ClassType -> {
                val name = name()
                name == "java.lang.Integer" ||
                        name == "java.lang.Long" ||
                        name == "java.lang.Float" ||
                        name == "java.lang.Double" ||
                        name == "java.lang.Boolean" ||
                        name == "java.lang.Byte" ||
                        name == "java.lang.Short" ||
                        name == "java.lang.Character"
            }

            else -> false
        }
    }

    fun isNormalType(): Boolean {
        return when (resolvedType) {
            is ResolvedType.PrimitiveType -> true
            is ResolvedType.ClassType -> {
                val name = name()
                isPrimitiveWrapper() || name == "java.lang.String" || name == "java.lang.Object"
            }

            else -> false
        }
    }

    fun isInterface(): Boolean = readSync {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.isInterface
            else -> false
        }
    }

    fun isAnnotationType(): Boolean = readSync {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.isAnnotationType
            else -> false
        }
    }

    fun isEnum(): Boolean = readSync {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.isEnum
            else -> false
        }
    }

    fun superClass(): ScriptPsiClassContext? = readSync {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readSync null
        val cls = classType.psiClass
        if (cls.isInterface || cls.isAnnotationType || cls.isEnum) return@readSync null
        extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let { return@readSync it }
        cls.superClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    fun extends(): Array<ScriptPsiClassContext>? = readSync {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readSync null
        classType.psiClass.extendsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    fun implements(): Array<ScriptPsiClassContext>? = readSync {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readSync null
        classType.psiClass.implementsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    fun toJson(): String {
        val helper = PsiClassHelper.getInstance(context.project)
        val model = runBlocking {
            helper.buildObjectModel(resolvedType, JsonOption.READ_GETTER_OR_SETTER)
        }
        return ObjectModelJsonConverter.toJson(model)
    }

    fun toJson5(): String {
        val helper = PsiClassHelper.getInstance(context.project)
        val model = runBlocking {
            helper.buildObjectModel(resolvedType, JsonOption.ALL)
        }
        return ObjectModelJsonConverter.toJson5(model)
    }
}

/**
 * Script context for a [PsiType] without a specific PSI element.
 *
 * Used when evaluating json.rule.convert rules where the context is a type
 * (e.g., `ResponseEntity<UserInfo>`) rather than a field/method/parameter.
 *
 * Exposes `it.type()` so Groovy scripts can write:
 * ```
 * groovy: it.type().isExtend("org.springframework.http.ResponseEntity")
 * ```
 *
 * Also delegates common operations (name, doc, ann) to the underlying element
 * if one is available as a context element.
 */
class ScriptPsiTypeContext(
    context: RuleContext,
    private val psiType: com.intellij.psi.PsiType
) : ScriptItContext(context), ClassContext {

    private val resolvedType: ResolvedType by lazy { TypeResolver.resolve(psiType) }

    private val typeContext: ScriptTypeContext by lazy { ScriptTypeContext(context, resolvedType) }

    override fun type(): ScriptTypeContext = typeContext

    override fun contextType(): String = "class"

    override fun methods(): Array<ScriptPsiMethodContext> = typeContext.methods()

    override fun methodCnt(): Int = typeContext.methods().size

    override fun fields(): Array<ScriptPsiFieldContext> = typeContext.fields()

    override fun fieldCnt(): Int = typeContext.fields().size

    override fun isExtend(superClass: String): Boolean = typeContext.isExtend(superClass)

    override fun isMap(): Boolean = typeContext.isMap()

    override fun isCollection(): Boolean = typeContext.isCollection()

    override fun isArray(): Boolean = typeContext.isArray()

    override fun isInterface(): Boolean = typeContext.isInterface()

    override fun isAnnotationType(): Boolean = typeContext.isAnnotationType()

    override fun isEnum(): Boolean = typeContext.isEnum()

    override fun isPrimitive(): Boolean = typeContext.isPrimitive()

    override fun isPrimitiveWrapper(): Boolean = typeContext.isPrimitiveWrapper()

    override fun isNormalType(): Boolean = typeContext.isNormalType()

    override fun qualifiedName(): String? = typeContext.name()

    override fun packageName(): String? = readSync {
        val name = typeContext.name()
        val lastDot = name.lastIndexOf('.')
        if (lastDot > 0) name.substring(0, lastDot) else null
    }

    override fun isPublic(): Boolean = true

    override fun isProtected(): Boolean = false

    override fun isPrivate(): Boolean = false

    override fun isPackagePrivate(): Boolean = false

    override fun isInnerClass(): Boolean = false

    override fun isStatic(): Boolean = false

    override fun outerClass(): ScriptPsiClassContext? = null

    override fun superClass(): ScriptPsiClassContext? = typeContext.superClass()

    override fun extends(): Array<ScriptPsiClassContext>? = typeContext.extends()

    override fun implements(): Array<ScriptPsiClassContext>? = typeContext.implements()

    override fun toJson(): String = typeContext.toJson()

    override fun toJson5(): String = typeContext.toJson5()

    override fun toString(): String = resolvedType.qualifiedName()
}

/**
 * Script context for a resolved [ResolvedType.ClassType].
 *
 * Extends [ScriptPsiClassContext] so all PSI operations (annotations, docs, modifiers)
 * work unchanged. Overrides [methods], [fields], [type] to use the resolved ClassType
 * which carries per-level generic context.
 *
 * This ensures that when a script calls `it.containingClass().methods()`, the returned
 * methods have fully-resolved generic types.
 */
class ScriptResolvedClassContext(
    context: RuleContext,
    private val classType: ResolvedType.ClassType
) : ScriptPsiClassContext(context.withElement(classType.psiClass)) {

    override fun methods(): Array<ScriptPsiMethodContext> = readSync {
        val methods = classType.suitableMethods()
        Array(methods.size) { i -> ScriptResolvedMethodContext(context, methods[i]) }
    }

    override fun fields(): Array<ScriptPsiFieldContext> = readSync {
        val fields = classType.suitableFields()
        Array(fields.size) { i -> ScriptResolvedFieldContext(context, fields[i]) }
    }

    override fun type(): ScriptTypeContext = ScriptTypeContext(context, classType)

    override fun superClass(): ScriptPsiClassContext? = readSync {
        classType.superClasses().firstOrNull()?.let { ScriptResolvedClassContext(context, it) }
    }

    override fun extends(): Array<ScriptPsiClassContext>? = readSync {
        val supers = classType.superClasses().toList()
        if (supers.isEmpty()) null
        else supers.map { ScriptResolvedClassContext(context, it) }.toTypedArray()
    }
}

class ScriptResolvedMethodContext(context: RuleContext, private val resolvedMethod: ResolvedMethod) :
    ScriptPsiMethodContext(context.withElement(resolvedMethod.psiMethod)) {

    override fun returnType(): ScriptTypeContext {
        return ScriptTypeContext(context, resolvedMethod.returnType)
    }

    override fun args(): Array<ScriptPsiParameterContext> {
        val params = resolvedMethod.params
        return Array(params.size) { i -> ScriptResolvedParameterContext(context, params[i]) }
    }

    override fun argTypes(): Array<ScriptTypeContext> {
        val params = resolvedMethod.params
        return Array(params.size) { i -> ScriptTypeContext(context, params[i].type) }
    }

    override fun containingClass(): ScriptPsiClassContext? {
        val ownerClassType = resolvedMethod.ownerClassType
        if (ownerClassType != null) return ScriptResolvedClassContext(context, ownerClassType)
        return resolvedMethod.containClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    override fun defineClass(): ScriptPsiClassContext? {
        return resolvedMethod.psiMethod.containingClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }
}

class ScriptResolvedFieldContext(context: RuleContext, private val resolvedField: ResolvedField) :
    ScriptPsiFieldContext(context.withElement(resolvedField.psiField)) {

    override fun type(): ScriptTypeContext {
        return ScriptTypeContext(context, resolvedField.type)
    }

    override fun containingClass(): ScriptPsiClassContext? {
        return resolvedField.containClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    override fun defineClass(): ScriptPsiClassContext? {
        return resolvedField.psiField.containingClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }
}

class ScriptResolvedParameterContext(context: RuleContext, private val resolvedParam: ResolvedParam) :
    ScriptPsiParameterContext(context.withElement(resolvedParam.psiParameter)) {

    override fun type(): ScriptTypeContext {
        return ScriptTypeContext(context, resolvedParam.type)
    }
}

/**
 * Mirrors the legacy `ScriptPsiEnumConstantContext`.
 */
class ScriptPsiEnumConstantContext(private val context: RuleContext, private val psiEnumConstant: PsiEnumConstant) {

    fun name(): String = readSync { psiEnumConstant.name }

    fun ordinal(): Int = readSync {
        psiEnumConstant.containingClass?.fields?.indexOf(psiEnumConstant) ?: 0
    }

    fun getParams(): Map<String, Any?> = readSync {
        val args = psiEnumConstant.argumentList?.expressions ?: return@readSync emptyMap()
        val result = LinkedHashMap<String, Any?>()
        args.forEachIndexed { index, expr ->
            result["arg$index"] = expr.text
        }
        result
    }

    fun getParam(name: String): Any? = getParams()[name]

    override fun toString(): String = name()
}
