package com.itangcent.suv.http

import com.google.inject.Inject
import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.BasicCookie
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Test case of [CookiePersistenceHelper]
 */
class CookiePersistenceHelperTest : AdvancedContextTest() {

    @Inject
    private lateinit var cookiePersistenceHelper: CookiePersistenceHelper

    private val apacheHttpClient = ApacheHttpClient()

    override fun afterBind(actionContext: ActionContext) {
        actionContext.cache("project_path", tempDir.toString())
    }

    @Test
    fun persistenceCookies() {
        val cookieStore = apacheHttpClient.cookieStore()

        assertDoesNotThrow {
            cookiePersistenceHelper.loadCookiesInto(cookieStore)
        }

        assertDoesNotThrow {
            cookiePersistenceHelper.storeCookiesFrom(cookieStore)
        }

        // add a cookie
        val cookie = BasicCookie().apply {
            setName("testCookie")
            setValue("testValue")
            setDomain("test.com")
            setPath("/")
            setExpiryDate(System.currentTimeMillis() + 86400000) // expires in 1 day
        }
        cookieStore.addCookie(cookie)

        // store cookies
        cookiePersistenceHelper.storeCookiesFrom(cookieStore)

        // clear cookies then load cookies
        cookieStore.clear()
        cookiePersistenceHelper.loadCookiesInto(cookieStore)

        // check if cookie is loaded
        val readCookie = cookieStore.cookies().first { it.getName() == "testCookie" }
        assertEquals(cookie.getName(), readCookie.getName())
        assertEquals(cookie.getValue(), readCookie.getValue())
        assertEquals(cookie.getDomain(), readCookie.getDomain())
        assertEquals(cookie.getPath(), readCookie.getPath())
        assertEquals(cookie.getExpiryDate(), readCookie.getExpiryDate())
    }
}