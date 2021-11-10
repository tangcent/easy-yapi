package com.itangcent.idea.plugin.api.export.feign

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.spring.SimpleSpringRequestClassExporter
import com.itangcent.idea.plugin.condition.ConditionOnSetting

/**
 * Support export apis (name only) from client that annotated with @FeignClient
 *
 * @author tangcent
 */
@Singleton
@ConditionOnClass(SpringFeignClassName.FEIGN_CLIENT_ANNOTATION)
@ConditionOnSetting("feignEnable")
open class SimpleFeignRequestClassExporter : SimpleSpringRequestClassExporter() {

    override fun isCtrl(psiClass: PsiClass): Boolean {
        return annotationHelper!!.hasAnn(psiClass, SpringFeignClassName.FEIGN_CLIENT_ANNOTATION)
    }
}