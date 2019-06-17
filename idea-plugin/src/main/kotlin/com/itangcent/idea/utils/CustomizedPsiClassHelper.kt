package com.itangcent.idea.utils

import com.intellij.psi.PsiElement
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.intellij.psi.SingleDuckType

class CustomizedPsiClassHelper : DefaultPsiClassHelper() {

    override fun getTypeObject(clsWithParam: SingleDuckType?, context: PsiElement, option: Int): Any? {
        if (clsWithParam != null) {
            val psiClassName = clsWithParam.psiClass().qualifiedName
            if (psiClassName == HTTP_ENTITY
                    || psiClassName == RESPONSE_ENTITY
                    || psiClassName == REQUEST_ENTITY) {
                if (clsWithParam.genericInfo == null) {
                    return Object()
                }
                var entityType = clsWithParam.genericInfo!!["T"]
                if (entityType == null && clsWithParam.genericInfo!!.size == 1) {
                    entityType = clsWithParam.genericInfo!!.values.first()
                }
                if (entityType == null) {
                    return Object()
                }
                return super.getTypeObject(entityType, context, option)
            }
        }

        return super.getTypeObject(clsWithParam, context, option)
    }

    companion object {
        const val HTTP_ENTITY = "org.springframework.http.HttpEntity"

        const val REQUEST_ENTITY = "org.springframework.http.RequestEntity"

        const val RESPONSE_ENTITY = "org.springframework.http.ResponseEntity"
    }
}