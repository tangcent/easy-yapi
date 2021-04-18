package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.jvm.AbstractLinkResolve
import com.itangcent.intellij.jvm.LinkExtractor

@Singleton
open class DefaultDocParseHelper : DocParseHelper {

    @Inject
    private val linkResolver: LinkResolver? = null

    @Inject
    private val linkExtractor: LinkExtractor? = null

    override fun resolveLinkInAttr(attr: String?, psiMember: PsiMember): String? {
        if (attr.isNullOrBlank()) return attr

        return linkExtractor!!.extract(attr, psiMember, object : AbstractLinkResolve() {
            override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                return linkResolver!!.linkToClass(linkClass)
            }

            override fun linkToField(plainText: String, linkField: PsiField): String? {
                return linkResolver!!.linkToProperty(linkField)
            }

            override fun linkToMethod(plainText: String, linkMethod: PsiMethod): String? {
                return linkResolver!!.linkToMethod(linkMethod)
            }

            override fun linkToUnresolved(plainText: String): String? {
                return plainText
            }
        })
    }
}