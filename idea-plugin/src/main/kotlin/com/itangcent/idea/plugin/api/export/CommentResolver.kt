package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.itangcent.common.utils.KVUtils
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.PsiResolver
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ClassRuleKeys

@Singleton
class CommentResolver {

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected val psiResolver: PsiResolver? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null


    fun resolveCommentForType(psiType: PsiType, context: PsiElement): String? {

        if (jvmClassHelper!!.isEnum(psiType)) {

            val convertTo = ruleComputer!!.computer(ClassRuleKeys.ENUM_CONVERT, psiType, context)

            if (!convertTo.isNullOrBlank()) {
                if (convertTo.contains("#")) {
                    val options = psiClassHelper!!.resolveEnumOrStatic(convertTo, context, "")
                    if (!options.isNullOrEmpty()) {
                        return KVUtils.getOptionDesc(options)
                    }
                } else {
                    val resolveClass = psiResolver!!.resolveClass(convertTo, context)
                    if (resolveClass == null) {
                        logger!!.error("failed to resolve class:$convertTo")
                        return null
                    }
                    val constants = psiClassHelper!!.parseEnumConstant(resolveClass)
                    if (constants.isEmpty()) {
                        logger!!.error("nothing be found at:$convertTo")
                        return null
                    }

                    return KVUtils.getConstantDesc(constants)
                }
            }

            val enumClass = jvmClassHelper.resolveClassInType(psiType)!!
            val constants = psiClassHelper!!.parseEnumConstant(enumClass)
            if (constants.isEmpty()) {
                logger!!.error("nothing be found at:$convertTo")
                return null
            }

            return KVUtils.getConstantDesc(constants)
        }

        return null
    }
}