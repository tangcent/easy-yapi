package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.intellij.psi.PsiMember
import com.itangcent.idea.plugin.api.export.core.DefaultDocParseHelper

@ImplementedBy(DefaultDocParseHelper::class)
interface DocParseHelper {
    fun resolveLinkInAttr(attr: String?, psiMember: PsiMember): String?
}