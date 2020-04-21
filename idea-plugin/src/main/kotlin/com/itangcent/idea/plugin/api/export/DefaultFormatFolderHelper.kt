package com.itangcent.idea.plugin.api.export

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.itangcent.common.kit.headLine
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod

@Singleton
class DefaultFormatFolderHelper : FormatFolderHelper {

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null

    /**
     * cache class -> folder
     */
    private val folderCache: Cache<Any, Folder> = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build()

    override fun resolveFolder(resource: Any): Folder {
        if (resource is String) {
            return resource to ""
        }

        if (resource is PsiResource) {
            (resource.resource() ?: resource.resourceClass())?.let {
                return resolveFolder(it)
            }
        }

        if (resource is PsiClass) {
            return resolveFolderOfPsiClass(resource)
        }

        if (resource is ExplicitClass) {
            return resolveFolderOfExplicitClass(resource)
        }

        if (resource is PsiMethod) {
            val folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
            if (folder.notNullOrBlank()) {
                return folder!! to ""
            }
        }

        if (resource is ExplicitMethod) {
            val folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
            if (folder.notNullOrBlank()) {
                return folder!! to ""
            }
        }

        if (resource is PsiMember) {
            resource.containingClass?.let {
                return resolveFolderOfPsiClass(it)
            }
        }

        if (resource is ExplicitElement<*>) {
            return resolveFolderOfExplicitClass(resource.containClass())
        }

        return resource.toString() to ""
    }

    private fun resolveFolderOfPsiClass(resource: PsiClass): Pair<String, String> {
        return folderCache.get(resource) {
            var folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
            val attr = findAttrOfClass(resource)
            if (folder.isNullOrBlank()) {
                folder = if (attr.isNullOrBlank()) {
                    resource.name
                } else {
                    attr.headLine()
                }
            }
            return@get (folder ?: resource.toString()) to (attr ?: "")
        }
    }

    private fun resolveFolderOfExplicitClass(resource: ExplicitClass): Pair<String, String> {
        return folderCache.get(resource) {
            var folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
            val attr = findAttrOfClass(resource.psi())
            if (folder.isNullOrBlank()) {
                folder = if (attr.isNullOrBlank()) {
                    resource.name()
                } else {
                    attr.headLine()
                }
            }
            return@get (folder ?: resource.toString()) to (attr ?: "")
        }
    }

    private fun findAttrOfClass(cls: PsiClass): String? {
        val docText = resourceHelper!!.findAttrOfClass(cls)
        return if (docText.isNullOrBlank()) null
        else docParseHelper!!.resolveLinkInAttr(docText, cls)
    }

}