package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.idea.plugin.settings.helper.IntelligentSettingsHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.findCurrentMethod

@ImplementedBy(EmptyMethodFilter::class)
interface MethodFilter {
    fun checkMethod(method: PsiMethod): Boolean
}

@Singleton
class EmptyMethodFilter : MethodFilter {
    override fun checkMethod(method: PsiMethod): Boolean {
        return true
    }
}

@Singleton
class ConfigurableMethodFilter : MethodFilter {

    @Inject
    private lateinit var intelligentSettingsHelper: IntelligentSettingsHelper

    private val selectedMethod by lazy {
        return@lazy ActionContext.getContext()?.findCurrentMethod()
    }

    override fun checkMethod(method: PsiMethod): Boolean {
        if (intelligentSettingsHelper.selectedOnly()) {
            return selectedMethod == null || selectedMethod == method
        }
        return true
    }
}

@Singleton
class SpecialMethodFilter(
    private val specialMethod: PsiMethod
) : MethodFilter {
    override fun checkMethod(method: PsiMethod): Boolean {
        return specialMethod == method
    }
}

@Singleton
class CustomizedMethodFilter : MethodFilter {

    private val methodFilters: MutableList<MethodFilter> = mutableListOf()

    fun addMethodFilter(methodFilter: MethodFilter) {
        methodFilters.add(methodFilter)
    }

    fun setMethodFilters(methodFilters: List<MethodFilter>) {
        this.methodFilters.clear()
        this.methodFilters.addAll(methodFilters)
    }

    fun clearMethodFilters() {
        methodFilters.clear()
    }

    override fun checkMethod(method: PsiMethod): Boolean {
        return methodFilters.all { it.checkMethod(method) }
    }
}