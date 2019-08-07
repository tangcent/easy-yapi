package com.itangcent.idea.plugin.api.export

import com.google.inject.ImplementedBy
import com.intellij.psi.PsiMember
import com.itangcent.common.exporter.RequestHelper

@ImplementedBy(DefaultDocParseHelper::class)
interface DocParseHelper {
    fun resolveLinkInAttr(attr: String?, psiMember: PsiMember, requestHelper: RequestHelper): String?
}