package com.itangcent.utils

import java.util.regex.Pattern

/**
 * Converts a raw GitHub URL to a Gitee raw URL.
 *
 * Raw URLs have the format:
 * `https://raw.githubusercontent.com/$user/$project/$file`
 *
 * This converts it to the Gitee raw URL format:
 * `https://gitee.com/$user/$project/raw/$file`
 *
 * @author tangcent
 */
object GiteeSupport {

    private val GITHUB_RAW_URL_REGEX =
        Pattern.compile("https://raw.githubusercontent.com/(.*?)/(.*?)/(.*?)")

    /**
     * Converts a raw GitHub URL to a Gitee raw URL.
     *
     * https://raw.githubusercontent.com/$user/$project/$path
     * ->
     * https://gitee.com/$user/$project/raw/$path
     */
    fun convertUrlFromGithub(githubUrl: String): String? {
        val matcher = GITHUB_RAW_URL_REGEX.matcher(githubUrl)

        if (!matcher.matches()) return null

        val giteeUser = matcher.group(1)
        val giteeProject = matcher.group(2)
        val giteeFilePath = matcher.group(3)
        return "https://gitee.com/$giteeUser/$giteeProject/raw/$giteeFilePath"
    }
}