package com.itangcent.utils

import java.util.regex.Pattern

/**
 * @author tangcent
 */
object GiteeSupport {
    /**
     * https://raw.githubusercontent.com/$user/$project/$path
     * ->
     * https://gitee.com/$user/$project/raw/$path
     */
    fun convertUrlFromGithub(url: String): String? {
        val matcher =
            Pattern.compile("https://raw.githubusercontent.com/(.*?)/(.*?)/(.*?)")
                .matcher(url)
        if (!matcher.matches()) {
            return null
        }
        return "https://gitee.com/${matcher.group(1)}/${matcher.group(2)}/raw/${matcher.group(3)}"
    }
}