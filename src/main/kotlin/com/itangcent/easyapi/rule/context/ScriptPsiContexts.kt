package com.itangcent.easyapi.rule.context

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.InheritanceUtil
import com.itangcent.easyapi.psi.type.ResolvedField
import com.itangcent.easyapi.psi.type.ResolvedMethod
import com.itangcent.easyapi.psi.type.ResolvedParam
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.TypeResolver

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
    return when (element) {
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
open class ScriptPsiClassContext(context: RuleContext) : ScriptItContext(context) {

    private fun psiClass(): PsiClass = context.element as PsiClass

    open fun methods(): Array<ScriptPsiMethodContext> = readAction {
        val cls = psiClass()
        val methods = cls.allMethods
        Array(methods.size) { i ->
            ScriptPsiMethodInClassContext(context.withElement(methods[i]), cls)
        }
    }

    fun methodCnt(): Int = readAction { psiClass().allMethods.size }

    open fun fields(): Array<ScriptPsiFieldContext> = readAction {
        val cls = psiClass()
        val fields = cls.allFields
        Array(fields.size) { i ->
            ScriptPsiFieldInClassContext(context.withElement(fields[i]), cls)
        }
    }

    fun fieldCnt(): Int = readAction { psiClass().allFields.size }

    fun type(): ScriptTypeContext {
        return ScriptTypeContext(context, ResolvedType.ClassType(psiClass()))
    }

    fun isExtend(superClass: String): Boolean = readAction {
        InheritanceUtil.isInheritor(psiClass(), superClass)
    }

    fun isMap(): Boolean = readAction {
        val fqn = psiClass().qualifiedName ?: return@readAction false
        fqn == "java.util.Map" || InheritanceUtil.isInheritor(psiClass(), "java.util.Map")
    }

    fun isCollection(): Boolean = readAction {
        val fqn = psiClass().qualifiedName ?: return@readAction false
        fqn == "java.util.Collection" || InheritanceUtil.isInheritor(psiClass(), "java.util.Collection")
    }

    fun isArray(): Boolean = name().endsWith("[]")

    fun isInterface(): Boolean = readAction { psiClass().isInterface }

    fun isAnnotationType(): Boolean = readAction { psiClass().isAnnotationType }

    fun isEnum(): Boolean = readAction { psiClass().isEnum }

    fun isPrimitive(): Boolean = PRIMITIVE_NAMES.contains(name())

    fun isPrimitiveWrapper(): Boolean = PRIMITIVE_WRAPPER_NAMES.contains(name())

    fun isNormalType(): Boolean {
        val n = name()
        return isPrimitive() || isPrimitiveWrapper() || n == "java.lang.String" || n == "java.lang.Object"
    }

    open fun superClass(): ScriptPsiClassContext? = readAction {
        val cls = psiClass()
        if (cls.isInterface || cls.isAnnotationType || cls.isEnum) return@readAction null
        extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let { return@readAction it }
        cls.superClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    open fun extends(): Array<ScriptPsiClassContext>? = readAction {
        psiClass().extendsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    open fun implements(): Array<ScriptPsiClassContext>? = readAction {
        psiClass().implementsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    override fun contextType(): String = "class"

    companion object {
        private val PRIMITIVE_NAMES = setOf(
            "int", "long", "short", "byte", "float", "double", "boolean", "char"
        )
        private val PRIMITIVE_WRAPPER_NAMES = setOf(
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.lang.Character"
        )
    }
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
open class ScriptPsiMethodContext(context: RuleContext) : ScriptItContext(context) {

    protected fun psiMethod(): PsiMethod = context.element as PsiMethod

    open fun returnType(): ScriptTypeContext? = readAction {
        val type = psiMethod().returnType ?: return@readAction null
        ScriptTypeContext(context, TypeResolver.resolve(type))
    }

    fun type(): ScriptTypeContext? = returnType()

    fun isVarArgs(): Boolean = readAction { psiMethod().isVarArgs }

    open fun args(): Array<ScriptPsiParameterContext> = readAction {
        val params = psiMethod().parameterList.parameters
        Array(params.size) { i -> ScriptPsiParameterContext(context.withElement(params[i])) }
    }

    fun params(): Array<ScriptPsiParameterContext> = args()

    fun parameters(): Array<ScriptPsiParameterContext> = args()

    open fun argTypes(): Array<ScriptTypeContext> = readAction {
        val params = psiMethod().parameterList.parameters
        Array(params.size) { i -> ScriptTypeContext(context, TypeResolver.resolve(params[i].type)) }
    }

    fun argCnt(): Int = readAction { psiMethod().parameterList.parametersCount }

    fun paramCnt(): Int = argCnt()

    open fun containingClass(): ScriptPsiClassContext? = readAction {
        val cls = psiMethod().containingClass ?: return@readAction null
        ScriptPsiClassContext(context.withElement(cls))
    }

    open fun defineClass(): ScriptPsiClassContext? = containingClass()

    /* for backward compatibility only */
    fun isEnumField(): Boolean = false

    override fun contextType(): String = "method"

    override fun toString(): String {
        val cls = containingClass()
        return if (cls != null) "${cls.name()}#${name()}" else name()
    }
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
open class ScriptPsiFieldContext(context: RuleContext) : ScriptItContext(context) {

    protected fun psiField(): PsiField = context.element as PsiField

    open fun type(): ScriptTypeContext = readAction {
        ScriptTypeContext(context, TypeResolver.resolve(psiField().type))
    }

    open fun containingClass(): ScriptPsiClassContext? = readAction {
        val cls = psiField().containingClass ?: return@readAction null
        ScriptPsiClassContext(context.withElement(cls))
    }

    open fun defineClass(): ScriptPsiClassContext? = containingClass()

    fun isEnumField(): Boolean = readAction { psiField() is PsiEnumConstant }

    fun asEnumField(): ScriptPsiEnumConstantContext? = readAction {
        (psiField() as? PsiEnumConstant)?.let { ScriptPsiEnumConstantContext(context, it) }
    }

    override fun contextType(): String = "field"

    override fun toString(): String {
        val cls = containingClass()
        return if (cls != null) "${cls.name()}#${name()}" else name()
    }
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
open class ScriptPsiParameterContext(context: RuleContext) : ScriptItContext(context) {

    private fun psiParameter(): PsiParameter = context.element as PsiParameter

    open fun type(): ScriptTypeContext = readAction {
        ScriptTypeContext(context, TypeResolver.resolve(psiParameter().type))
    }

    fun isVarArgs(): Boolean = readAction { psiParameter().isVarArgs }

    /**
     * Returns the method which declares this parameter. May be null.
     */
    open fun method(): ScriptPsiMethodContext? = readAction {
        val scope = psiParameter().declarationScope
        if (scope is PsiMethod) ScriptPsiMethodContext(context.withElement(scope)) else null
    }

    /**
     * Returns the element which declares this parameter.
     */
    open fun declaration(): ScriptItContext? = readAction {
        val scope = psiParameter().declarationScope
        when (scope) {
            is PsiMethod -> ScriptPsiMethodContext(context.withElement(scope))
            else -> ScriptItContext(context.withElement(scope))
        }
    }

    override fun contextType(): String = "param"

    override fun toString(): String = name()
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

    private fun <T> readAction(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        return if (app.isReadAccessAllowed) {
            block()
        } else {
            app.runReadAction<T>(block)
        }
    }

    fun name(): String = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> (resolvedType.psiClass.qualifiedName ?: resolvedType.psiClass.name ?: "Anonymous") +
                    resolvedType.typeArgs.takeIf { it.isNotEmpty() }?.joinToString(prefix = "<", postfix = ">") { ScriptTypeContext(context, it).name() }
                        .orEmpty()
            is ResolvedType.ArrayType -> ScriptTypeContext(context, resolvedType.componentType).name() + "[]"
            is ResolvedType.UnresolvedType -> resolvedType.canonicalText
            is ResolvedType.PrimitiveType -> resolvedType.kind.name.lowercase()
            is ResolvedType.WildcardType -> when {
                resolvedType.upper != null -> "? extends " + ScriptTypeContext(context, resolvedType.upper).name()
                resolvedType.lower != null -> "? super " + ScriptTypeContext(context, resolvedType.lower).name()
                else -> "?"
            }
        }
    }

    fun getName(): String = name()

    fun simpleName(): String = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.name ?: resolvedType.psiClass.qualifiedName ?: "Anonymous"
            is ResolvedType.ArrayType -> ScriptTypeContext(context, resolvedType.componentType).simpleName() + "[]"
            is ResolvedType.UnresolvedType -> resolvedType.canonicalText.substringAfterLast('.')
            is ResolvedType.PrimitiveType -> resolvedType.kind.name.lowercase()
            is ResolvedType.WildcardType -> "?"
        }
    }

    fun getSimpleName(): String = simpleName()

    fun psi(): Any = resolvedType

    fun contextType(): String = "class"

    fun methods(): Array<ScriptPsiMethodContext> = readAction {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readAction emptyArray()
        val methods = classType.methods()
        Array(methods.size) { i -> ScriptResolvedMethodContext(context, methods[i]) }
    }

    fun fields(): Array<ScriptPsiFieldContext> = readAction {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readAction emptyArray()
        val fields = classType.fields()
        Array(fields.size) { i -> ScriptResolvedFieldContext(context, fields[i]) }
    }

    override fun toString(): String = name()

    fun isExtend(superClass: String): Boolean = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> InheritanceUtil.isInheritor(resolvedType.psiClass, superClass)
            else -> false
        }
    }

    fun isMap(): Boolean = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> {
                val fqn = resolvedType.psiClass.qualifiedName ?: return@readAction false
                fqn == "java.util.Map" || InheritanceUtil.isInheritor(resolvedType.psiClass, "java.util.Map")
            }
            else -> false
        }
    }

    fun isCollection(): Boolean = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> {
                val fqn = resolvedType.psiClass.qualifiedName ?: return@readAction false
                fqn == "java.util.Collection" || InheritanceUtil.isInheritor(resolvedType.psiClass, "java.util.Collection")
            }
            is ResolvedType.ArrayType -> true
            else -> false
        }
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

    fun isInterface(): Boolean = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.isInterface
            else -> false
        }
    }

    fun isAnnotationType(): Boolean = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.isAnnotationType
            else -> false
        }
    }

    fun isEnum(): Boolean = readAction {
        when (resolvedType) {
            is ResolvedType.ClassType -> resolvedType.psiClass.isEnum
            else -> false
        }
    }

    fun superClass(): ScriptPsiClassContext? = readAction {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readAction null
        val cls = classType.psiClass
        if (cls.isInterface || cls.isAnnotationType || cls.isEnum) return@readAction null
        extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let { return@readAction it }
        cls.superClass?.let { ScriptPsiClassContext(context.withElement(it)) }
    }

    fun extends(): Array<ScriptPsiClassContext>? = readAction {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readAction null
        classType.psiClass.extendsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
    }

    fun implements(): Array<ScriptPsiClassContext>? = readAction {
        val classType = resolvedType as? ResolvedType.ClassType ?: return@readAction null
        classType.psiClass.implementsList?.referencedTypes?.mapNotNull { ref ->
            ref.resolve()?.let { ScriptPsiClassContext(context.withElement(it)) }
        }?.toTypedArray()
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

    private fun <T> readAction(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        return if (app.isReadAccessAllowed) {
            block()
        } else {
            app.runReadAction<T>(block)
        }
    }

    fun name(): String = readAction { psiEnumConstant.name }

    fun ordinal(): Int = readAction {
        psiEnumConstant.containingClass?.fields?.indexOf(psiEnumConstant) ?: 0
    }

    fun getParams(): Map<String, Any?> = readAction {
        val args = psiEnumConstant.argumentList?.expressions ?: return@readAction emptyMap()
        val result = LinkedHashMap<String, Any?>()
        args.forEachIndexed { index, expr ->
            result["arg$index"] = expr.text
        }
        result
    }

    fun getParam(name: String): Any? = getParams()[name]

    override fun toString(): String = name()
}
