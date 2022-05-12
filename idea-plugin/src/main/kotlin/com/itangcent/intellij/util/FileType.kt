package com.itangcent.intellij.util

import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.tip.OnlyOnceInContextTip
import com.itangcent.intellij.tip.TipsHelper

/**
 * All file types currently supported.
 */
enum class FileType(private val suffix: String) {

    JAVA("java"),
    KOTLIN("kt")
    //, SCALA("scala")
    ;

    fun suffix(): String {
        return suffix
    }

    companion object {
        private val SUPPORT_SUPPORT_TIP = OnlyOnceInContextTip(
            "For support scala," +
                    " please download plugin from https://github.com/tangcent/easy-yapi/releases"
        )

        fun acceptable(fileName: String): Boolean {
            if (fileName.endsWith("scala")) {
                ActionContext.getContext()?.instance(TipsHelper::class)?.showTips(SUPPORT_SUPPORT_TIP)
            }
            return values().any { fileName.endsWith(it.suffix()) }
        }
    }
}