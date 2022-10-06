package com.itangcent.idea.plugin.rule

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.SimpleExtensible
import com.itangcent.idea.plugin.api.export.core.ClassExportContext
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.rule.MethodDocRuleWrap
import com.itangcent.idea.plugin.api.export.rule.RequestRuleWrap
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.element.ExplicitMethod
import java.util.concurrent.ConcurrentHashMap

class SuvRuleContext : SimpleExtensible(), RuleContext {

    override fun asPsiDocCommentOwner(): PsiDocCommentOwner? {
        return null
    }

    override fun asPsiModifierListOwner(): PsiModifierListOwner? {
        return null
    }

    override fun getName(): String? {
        return null
    }

    override fun getResource(): PsiElement? {
        return null
    }

}

fun SuvRuleContext.setDoc(doc: Doc) {
    this.setExt("doc", doc)//for compatible
    if (doc is Request) {
        this.setExt("type", "request")
        this.setExt("api", RequestRuleWrap(doc.createMethodExportContext(), doc))
    } else if (doc is MethodDoc) {
        this.setExt("type", "methodDoc")
        this.setExt("methodDoc", MethodDocRuleWrap(doc.createMethodExportContext(), doc))
    }
}


private val explicitCache = ConcurrentHashMap<PsiClass, ArrayList<ExplicitMethod>>()

fun Doc.createMethodExportContext(): MethodExportContext? {
    val resourceClass = this.resourceClass() ?: return null
    val resourceMethod = this.resourceMethod() ?: return null
    val explicitMethods = explicitCache.computeIfAbsent(resourceClass) {
        val actionContext = ActionContext.getContext()
        return@computeIfAbsent actionContext!!.callInReadUI {
            actionContext.instance(DuckTypeHelper::class).explicit(resourceClass).methods()
        }!!
    }
    val explicitMethod = explicitMethods.find { it.psi() == resourceMethod } ?: return null
    val classExportContext = ClassExportContext(resourceClass)
    return MethodExportContext(classExportContext, explicitMethod)
}
