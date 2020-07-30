package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.DefaultFormatFolderHelper
import com.itangcent.idea.plugin.api.export.Folder
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.computer
import com.itangcent.intellij.jvm.element.ExplicitClass

/**
 * 1.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.CLASS_POST_PRE_REQUEST]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.CLASS_POST_TEST]
 *
 * @see [https://learning.postman.com/docs/writing-scripts/intro-to-scripts/]
 */
@Singleton
class PostmanFormatFolderHelper : DefaultFormatFolderHelper() {

    @Inject
    private val ruleComputer: RuleComputer? = null

    override fun resolveFolderOfPsiClass(resource: PsiClass): Folder {
        val folder = super.resolveFolderOfPsiClass(resource)

        val preRequest = ruleComputer!!.computer(ClassExportRuleKeys.CLASS_POST_PRE_REQUEST, resource)
        if (preRequest.notNullOrBlank()) {
            folder.setExt(ClassExportRuleKeys.POST_PRE_REQUEST.name(), preRequest)
        }

        val test = ruleComputer.computer(ClassExportRuleKeys.CLASS_POST_TEST, resource)
        if (test.notNullOrBlank()) {
            folder.setExt(ClassExportRuleKeys.POST_TEST.name(), test)
        }

        return folder
    }

    override fun resolveFolderOfExplicitClass(resource: ExplicitClass): Folder {
        val folder = super.resolveFolderOfExplicitClass(resource)

        val preRequest = ruleComputer!!.computer(ClassExportRuleKeys.CLASS_POST_PRE_REQUEST, resource)
        if (preRequest.notNullOrBlank()) {
            folder.setExt(ClassExportRuleKeys.POST_PRE_REQUEST.name(), preRequest)
        }

        val test = ruleComputer.computer(ClassExportRuleKeys.CLASS_POST_TEST, resource)
        if (test.notNullOrBlank()) {
            folder.setExt(ClassExportRuleKeys.POST_TEST.name(), test)
        }

        return folder
    }

}