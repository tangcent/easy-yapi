package com.itangcent.mock

import com.intellij.psi.PsiElement
import com.itangcent.idea.plugin.api.export.core.ExportContext
import com.itangcent.idea.plugin.api.export.core.RootExportContext

class FakeExportContext: ExportContext , RootExportContext() {
    override fun psi(): PsiElement {
        TODO("Not yet implemented")
    }

    companion object{
        val INSTANCE = FakeExportContext()
    }
}