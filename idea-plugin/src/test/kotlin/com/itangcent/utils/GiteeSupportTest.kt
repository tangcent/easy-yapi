package com.itangcent.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Test case for [GiteeSupport]
 *
 * @author tangcent
 */
internal class GiteeSupportTest {

    @Test
    fun testConvertUrlFromGithub() {
        val githubUrl = "https://raw.githubusercontent.com/user/project/file.txt"
        val expectedGiteeUrl = "https://gitee.com/user/project/raw/file.txt"

        val actualGiteeUrl = GiteeSupport.convertUrlFromGithub(githubUrl)

        assertEquals(expectedGiteeUrl, actualGiteeUrl)
    }

    @Test
    fun testInvalidUrl() {
        val invalidUrl = "some invalid url"
        val giteeUrl = GiteeSupport.convertUrlFromGithub(invalidUrl)

        assertNull(giteeUrl)
    }
}