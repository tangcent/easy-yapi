package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.intellij.psi.PsiClassHelper
import java.util.regex.Pattern

class DocParseHelper {

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    fun resolveLinkInAttr(attr: String?, psiMember: PsiMember, parseHandle: ParseHandle): String? {
        if (attr.isNullOrBlank()) return attr

        if (attr.contains("@link")) {
            val pattern = Pattern.compile("\\{@link (.*?)\\}")
            val matcher = pattern.matcher(attr)

            val sb = StringBuffer()
            while (matcher.find()) {
                matcher.appendReplacement(sb, "")
                val linkClassAndMethod = matcher.group(1)
                val linkClassName = linkClassAndMethod.substringBefore("#")
                val linkMethodOrProperty = linkClassAndMethod.substringAfter("#", "").trim()
                val linkClass = psiClassHelper!!.resolveClass(linkClassName, psiMember) ?: continue
                if (linkMethodOrProperty.isBlank()) {
                    sb.append(parseHandle.linkToClass(linkClass))
                } else {
                    val methodOrProperty = psiClassHelper.resolvePropertyOrMethodOfClass(linkClass, linkMethodOrProperty)
                            ?: continue
                    when (methodOrProperty) {
                        is PsiMethod -> sb.append(parseHandle.linkToMethod(methodOrProperty))
                        is PsiField -> sb.append(parseHandle.linkToProperty(methodOrProperty))
                        else -> sb.append("[$linkClassAndMethod]")
                    }
                }
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        return attr
    }
}