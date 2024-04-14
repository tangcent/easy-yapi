package com.itangcent.idea.plugin.api.export.core

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.SimpleExtensible
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.jvm.element.ExplicitParameter
import kotlin.reflect.KClass

interface ExportContext : Extensible {
    /**
     * the parent context, allowing navigation up the context hierarchy.
     */
    fun parent(): ExportContext?

    /**
     * Returns the PSI element which corresponds to this element
     *
     * @return the psi element
     */
    fun psi(): PsiElement
}

/**
 * Extends ExportContext for contexts dealing with variables (methods or parameters).
 */
interface VariableExportContext : ExportContext {

    /**
     * the name of the variable.
     */
    fun name(): String

    /**
     * the type of the variable, which may be null if the type is not resolved.
     */
    fun type(): DuckType?

    /**
     * the explicit element representation of the variable.
     */
    fun element(): ExplicitElement<*>

    /**
     * Sets a resolved name for the variable, typically used for renaming.
     */
    fun setResolvedName(name: String)
}

//region kits of ExportContext

/**
 * find specific contexts by type.
 */
@Suppress("UNCHECKED_CAST")
fun <T : ExportContext> ExportContext.findContext(condition: KClass<T>): T? {
    return findContext { condition.isInstance(it) } as? T
}

/**
 * find specific contexts by condition.
 */
fun ExportContext.findContext(condition: (ExportContext) -> Boolean): ExportContext? {
    var exportContext: ExportContext? = this
    while (exportContext != null) {
        if (condition(exportContext)) {
            return exportContext
        }
        exportContext = exportContext.parent()
    }
    return null
}

//endregion

/**
 * Base context with no parent, typically used for top-level classes.
 */
abstract class RootExportContext :
    SimpleExtensible(), ExportContext {
    override fun parent(): ExportContext? {
        return null
    }
}

/**
 * General purpose context implementation with a specified parent context.
 */
abstract class AbstractExportContext(private val parent: ExportContext) :
    SimpleExtensible(), VariableExportContext {

    private var resolvedName: String? = null

    override fun parent(): ExportContext? {
        return this.parent
    }

    /**
     * Returns the name of the element.
     *
     * @return the element name.
     */
    override fun name(): String {
        return resolvedName ?: element().name()
    }

    override fun setResolvedName(name: String) {
        this.resolvedName = name
    }
}

/**
 * Context specifically for a class
 */
class ClassExportContext(val cls: PsiClass) : RootExportContext() {
    override fun psi(): PsiClass {
        return cls
    }
}

/**
 * Context for a method, containing specifics about the method being exported.
 */
class MethodExportContext(
    parent: ExportContext,
    private val method: ExplicitMethod
) : AbstractExportContext(parent), VariableExportContext {

    /**
     * Returns the name of the element.
     *
     * @return the element name.
     */
    override fun name(): String {
        return method.name()
    }

    /**
     * Returns the type of the variable.
     *
     * @return the variable type.
     */
    override fun type(): DuckType? {
        return method.getReturnType()
    }

    override fun element(): ExplicitMethod {
        return method
    }

    override fun psi(): PsiMethod {
        return method.psi()
    }
}

/**
 * Context for a parameter, containing specifics about the parameter being exported.
 */
interface ParameterExportContext : VariableExportContext {

    override fun element(): ExplicitParameter

    override fun psi(): PsiParameter
}

fun ParameterExportContext(
    parent: ExportContext,
    parameter: ExplicitParameter
): ParameterExportContext {
    return ParameterExportContextImpl(parent, parameter)
}

class ParameterExportContextImpl(
    parent: ExportContext,
    private val parameter: ExplicitParameter
) : AbstractExportContext(parent), ParameterExportContext {

    /**
     * Returns the type of the variable.
     *
     * @return the variable type.
     */
    override fun type(): DuckType? {
        return parameter.getType()
    }

    override fun element(): ExplicitParameter {
        return parameter
    }

    override fun psi(): PsiParameter {
        return parameter.psi()
    }
}

/**
 * retrieve ClassExportContext based on the current context.
 */
fun ExportContext.classContext(): ClassExportContext? {
    return this.findContext(ClassExportContext::class)
}

/**
 * retrieve MethodExportContext based on the current context.
 */
fun ExportContext.methodContext(): MethodExportContext? {
    return this.findContext(MethodExportContext::class)
}

/**
 * retrieve ParameterExportContext based on the current context.
 */
fun ExportContext.paramContext(): ParameterExportContext? {
    return this.findContext(ParameterExportContext::class)
}

/**
 * Searches for an extended property, first locally then up the context hierarchy.
 */
fun <T> ExportContext.searchExt(attr: String): T? {
    this.getExt<T>(attr)?.let { return it }
    this.parent()?.searchExt<T>(attr)?.let { return it }
    return null
}