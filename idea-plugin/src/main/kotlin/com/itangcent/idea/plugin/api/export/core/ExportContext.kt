package com.itangcent.idea.plugin.api.export.core

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.common.utils.Extensible
import com.itangcent.common.utils.SimpleExtensible
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

class MethodExportContext(val parent: ExportContext,
                          val method: ExplicitMethod) : AbstractExportContext(parent) {

    override fun psi(): PsiMethod {
        return method.psi()
    }
}

class ParameterExportContext(val parent: ExportContext,
                             val parameter: ExplicitParameter) : AbstractExportContext(parent) {

    override fun psi(): PsiParameter {
        return parameter.psi()
    }
}

fun ExportContext.classContext(): ClassExportContext? {
    return this.findContext(ClassExportContext::class)
}

fun ExportContext.paramContext(): ParameterExportContext? {
    return this.findContext(ParameterExportContext::class)
}