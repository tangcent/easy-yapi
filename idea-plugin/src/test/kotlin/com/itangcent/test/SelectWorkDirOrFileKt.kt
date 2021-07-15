package com.itangcent.test

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.intellij.context.ActionContext.ActionContextBuilder

fun ActionContextBuilder.workAt(file: PsiFile) {
    this.bind(DataContext::class) {
        it.toInstance(WorkAtFile(file))
    }
}

fun ActionContextBuilder.workAt(psiDirectory: PsiDirectory) {
    this.bind(DataContext::class) {
        it.toInstance(WorkAtDirectory(psiDirectory))
    }
}

fun ActionContextBuilder.workAt(vararg psiDirectory: PsiDirectory) {
    this.bind(DataContext::class) {
        it.toInstance(WorkAtDirectoryArray(psiDirectory.mapToTypedArray { it }))
    }
}

private class WorkAtFile(private val psiFile: PsiFile) : DataContext {
    override fun getData(dataId: String?): Any? {
        return if (CommonDataKeys.PSI_FILE.name == dataId) psiFile else null
    }
}

private class WorkAtDirectory(private val psiDirectory: PsiDirectory) : DataContext {
    override fun getData(dataId: String?): Any? {
        return if (CommonDataKeys.NAVIGATABLE.name == dataId) psiDirectory else null
    }
}

private class WorkAtDirectoryArray(private val psiDirectory: Array<PsiDirectory>) : DataContext {
    override fun getData(dataId: String?): Any? {
        return if (CommonDataKeys.NAVIGATABLE_ARRAY.name == dataId) psiDirectory else null
    }
}