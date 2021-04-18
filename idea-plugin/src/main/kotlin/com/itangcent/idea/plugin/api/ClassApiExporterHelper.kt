package com.itangcent.idea.plugin.api

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.*
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.LinkResolver
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod
import java.util.*

@Singleton
open class ClassApiExporterHelper {

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject
    private val docHelper: DocHelper? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @Inject
    private val linkResolver: LinkResolver? = null

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    fun extractParamComment(psiMethod: PsiMethod): KV<String, Any>? {
        val subTagMap = docHelper!!.getSubTagMapOfDocComment(psiMethod, "param")

        var methodParamComment: KV<String, Any>? = null
        subTagMap.entries.forEach { entry ->
            val name: String = entry.key
            val value: String? = entry.value
            if (methodParamComment == null) methodParamComment = KV.create()
            if (value.notNullOrBlank()) {

                val options: ArrayList<HashMap<String, Any?>> = ArrayList()
                val comment = linkExtractor!!.extract(value, psiMethod, object : AbstractLinkResolve() {

                    override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {

                        psiClassHelper!!.resolveEnumOrStatic(plainText, psiMethod, name)
                                ?.let { options.addAll(it) }

                        return super.linkToPsiElement(plainText, linkTo)
                    }

                    override fun linkToClass(plainText: String, linkClass: PsiClass): String? {
                        return linkResolver!!.linkToClass(linkClass)
                    }

                    override fun linkToType(plainText: String, linkType: PsiType): String? {
                        return jvmClassHelper!!.resolveClassInType(linkType)?.let {
                            linkResolver!!.linkToClass(it)
                        }
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

                methodParamComment!![name] = comment ?: ""
                if (options.notNullOrEmpty()) {
                    methodParamComment!!["$name@options"] = options
                }
            }

        }

        return methodParamComment
    }

    fun foreachMethod(cls: PsiClass, handle: (ExplicitMethod) -> Unit) {
        duckTypeHelper!!.explicit(cls)
                .methods()
                .stream()
                .filter { !jvmClassHelper!!.isBasicMethod(it.psi().name) }
                .filter { !it.psi().hasModifierProperty("static") }
                .filter { !it.psi().isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    protected open fun shouldIgnore(explicitElement: ExplicitElement<*>): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, explicitElement) ?: false
    }

    protected open fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

}