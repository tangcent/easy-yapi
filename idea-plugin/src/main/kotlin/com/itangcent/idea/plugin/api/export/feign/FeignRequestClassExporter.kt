package com.itangcent.idea.plugin.api.export.feign

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.ClassExportContext
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.idea.plugin.condition.ConditionOnSetting

/**
 * Support export apis from client that annotated with @FeignClient
 *
 * @author tangcent
 */
@Singleton
@ConditionOnSimple(false)
@ConditionOnClass(SpringFeignClassName.FEIGN_CLIENT_ANNOTATION)
@ConditionOnSetting("feignEnable")
open class FeignRequestClassExporter : SpringRequestClassExporter() {
    override fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {
        //NOP
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return annotationHelper!!.hasAnn(psiClass, SpringFeignClassName.FEIGN_CLIENT_ANNOTATION)
    }
}