package com.itangcent.idea.plugin.fields

import com.itangcent.common.utils.GsonUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import java.io.IOException


class FieldJsonGenerator {

    private val logger: Logger = ActionContext.local()

    @Throws(IOException::class)
    fun generateFieldJson(): String {

        val currentClass = ActionUtils.findCurrentClass()
        if (currentClass == null) {
            logger.info("no class be selected!")
            return ""
        }
        val kv = ActionContext.getContext()!!.instance(PsiClassHelper::class).getFields(currentClass)
        return GsonUtils.prettyJson(kv)
    }
}
