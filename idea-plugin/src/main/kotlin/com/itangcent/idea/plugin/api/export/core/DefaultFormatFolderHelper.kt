package com.itangcent.idea.plugin.api.export.core

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
import com.itangcent.idea.psi.UltimateDocHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitElement
import com.itangcent.intellij.jvm.element.ExplicitMethod

@Singleton
open class DefaultFormatFolderHelper : FormatFolderHelper {

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private val ultimateDocHelper: UltimateDocHelper? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null

    /**
     * cache class -> folder
     */
    private val folderCache: Cache<Any, Folder> = CacheBuilder.newBuilder()
        .maximumSize(20)
        .build()

    override fun resolveFolder(resource: Any): Folder {
        var folder = folderCache.getIfPresent(resource)
        if (folder != null) {
            return folder
        }
        folder = tryResolveFolder(resource) ?: Folder(resource.toString(), "")
        folderCache.put(resource, folder)
        return folder
    }

    private fun tryResolveFolder(resource: Any, resolveByContainClass: Boolean = true): Folder? {
        if (resource is String) {
            return Folder(resource, "")
        }

        if (resource is PsiResource) {
            tryResolveFolder(resource.resource(), false)?.let {
                return it
            }
            return tryResolveFolder(resource.resourceClass())
        }

        if (resource is PsiClass) {
            return folderCache[resource, {
                resolveFolderOfPsiClass(resource)
            }]
        }

        if (resource is ExplicitClass) {
            return folderCache[resource, {
                resolveFolderOfExplicitClass(resource)
            }]
        }

        if (resource is PsiMethod) {
            resolveFolderOfPsiMethod(resource)?.let { return it }
        }

        if (resource is ExplicitMethod) {
            resolveFolderOfExplicitMethod(resource)?.let { return it }
        }

        if (resolveByContainClass) {

            if (resource is PsiMember) {
                resource.containingClass?.let {
                    return folderCache[it, {
                        resolveFolderOfPsiClass(it)
                    }]
                }
            }

            if (resource is ExplicitElement<*>) {
                return folderCache[resource.containClass(), {
                    resolveFolderOfExplicitClass(resource.containClass())
                }]
            }
        }

        return null
    }

    private fun resolveFolderOfPsiMethod(resource: PsiMethod): Folder? {
        val folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
        return if (folder.notNullOrBlank()) {
            Folder(folder!!, "")
        } else null
    }

    private fun resolveFolderOfExplicitMethod(resource: ExplicitMethod): Folder? {
        val folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
        return if (folder.notNullOrBlank()) {
            Folder(folder!!, "")
        } else null
    }

    protected open fun resolveFolderOfPsiClass(resource: PsiClass): Folder {
        var folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
        val attr = findAttrOfClass(resource)
        if (folder.isNullOrBlank()) {
            folder = if (attr.isNullOrBlank()) {
                resource.name
            } else {
                attr.headLine()
            }
        }
        return Folder((folder ?: resource.toString()), (attr ?: ""))
    }

    protected open fun resolveFolderOfExplicitClass(resource: ExplicitClass): Folder {
        var folder = ruleComputer!!.computer(ClassExportRuleKeys.API_FOLDER, resource)
        val attr = findAttrOfClass(resource.psi())
        if (folder.isNullOrBlank()) {
            folder = if (attr.isNullOrBlank()) {
                resource.name()
            } else {
                attr.headLine()
            }
        }
        return Folder((folder ?: resource.toString()), (attr ?: ""))
    }

    private fun findAttrOfClass(cls: PsiClass): String? {
        val docText = ultimateDocHelper!!.findUltimateDescOfClass(cls)
        return if (docText.isNullOrBlank()) null
        else docParseHelper!!.resolveLinkInAttr(docText, cls)
    }

}