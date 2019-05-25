package com.itangcent.idea.plugin.api.export

import com.intellij.psi.PsiMember
import com.itangcent.common.exporter.ParseHandle

interface DocParseHelper {
    fun resolveLinkInAttr(attr: String?, psiMember: PsiMember, parseHandle: ParseHandle): String?
}