package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.PsiResolver
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.duck.SingleUnresolvedDuckType
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

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null


    fun resolveCommentForType(duckType: DuckType, context: PsiElement): String? {

        if (!duckType.isSingle()) {
            return null

        }

        if (jvmClassHelper!!.isEnum(duckType)) {

            if (duckType is SingleUnresolvedDuckType) {
                return resolveCommentForType(duckType.psiType(), context)
            }

            val convertTo = ruleComputer!!.computer(ClassRuleKeys.ENUM_CONVERT, duckType, context)

            if (convertTo.notNullOrBlank()) {
                if (convertTo!!.contains("#")) {
                    val options = psiClassHelper!!.resolveEnumOrStatic(convertTo, context, "")
                    if (options.notNullOrEmpty()) {
                        return KVUtils.getOptionDesc(options!!)
                    }
                } else {
                    val resolveClass = duckTypeHelper!!.resolveClass(convertTo, context)
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

            if (duckType is SingleDuckType) {
                val enumClass = duckType.psiClass()

                val constants = psiClassHelper!!.parseEnumConstant(enumClass)
                if (constants.isEmpty()) {
                    logger!!.error("nothing be found at:$convertTo")
                    return null
                }

                return KVUtils.getConstantDesc(constants)
            }
        }

        return null
    }

    fun resolveCommentForType(psiType: PsiType, context: PsiElement): String? {

        if (jvmClassHelper!!.isEnum(psiType)) {

            val convertTo = ruleComputer!!.computer(ClassRuleKeys.ENUM_CONVERT, psiType, context)

            if (convertTo.notNullOrBlank()) {
                if (convertTo!!.contains("#")) {
                    val options = psiClassHelper!!.resolveEnumOrStatic(convertTo, context, "")
                    if (options.notNullOrEmpty()) {
                        return KVUtils.getOptionDesc(options!!)
                    }
                } else {
                    val resolveClass = duckTypeHelper!!.resolveClass(convertTo, context)
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