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
    fun parent(): ExportContext?

    /**
     * Returns the PSI element which corresponds to this element
     *
     * @return the psi element
     */
    fun psi(): PsiElement
}

interface VariableExportContext : ExportContext {

    fun name(): String

    fun type(): DuckType?

    fun element(): ExplicitElement<*>
}

//region kits of ExportContext

@Suppress("UNCHECKED_CAST")
fun <T : ExportContext> ExportContext.findContext(condition: KClass<T>): T? {
    return findContext { condition.isInstance(it) } as? T
}

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

fun <T> ExportContext.findExt(attr: String): T? {
    var exportContext: ExportContext? = this
    while (exportContext != null) {
        exportContext.getExt<T>(attr)?.let { return it }
        exportContext = exportContext.parent()
    }
    return null
}

//endregion

abstract class RootExportContext :
    SimpleExtensible(), ExportContext {
    override fun parent(): ExportContext? {
        return null
    }
}

abstract class AbstractExportContext(private val parent: ExportContext) :
    SimpleExtensible(), ExportContext {
    override fun parent(): ExportContext? {
        return this.parent
    }
}

class ClassExportContext(val cls: PsiClass) : RootExportContext() {
    override fun psi(): PsiClass {
        return cls
    }
}

class MethodExportContext(
    private val parent: ExportContext,
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

class ParameterExportContext(
    private val parent: ExportContext,
    private val parameter: ExplicitParameter
) : AbstractExportContext(parent), VariableExportContext {

    /**
     * Returns the name of the element.
     *
     * @return the element name.
     */
    override fun name(): String {
        return parameter.name()
    }

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

fun ExportContext.classContext(): ClassExportContext? {
    return this.findContext(ClassExportContext::class)
}

fun ExportContext.methodContext(): MethodExportContext? {
    return this.findContext(MethodExportContext::class)
}

fun ExportContext.paramContext(): ParameterExportContext? {
    return this.findContext(ParameterExportContext::class)
}