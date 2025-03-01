package com.itangcent.cache

import com.google.inject.Inject
import com.itangcent.common.utils.DateUtils
import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.HttpClient
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case of [DefaultHttpContextCacheHelper]
 */
internal open class DefaultHttpContextCacheHelperTest : AdvancedContextTest() {

    @Inject
    protected lateinit var httpContextCacheHelper: HttpContextCacheHelper

    override fun afterBind(actionContext: ActionContext) {
        actionContext.cache("project_path", tempDir.toString())
    }

    class HostDefaultHttpContextCacheHelperTest : DefaultHttpContextCacheHelperTest() {

        @Test
        fun testHosts() {
            assertEquals(listOf("http://localhost:8080"), httpContextCacheHelper.getHosts())
            httpContextCacheHelper.addHost("http://127.0.0.1:8001")
            assertEquals(listOf("http://127.0.0.1:8001", "http://localhost:8080"), httpContextCacheHelper.getHosts())

            for (i in 2..11) {
                httpContextCacheHelper.addHost("http://127.0.0.1:${8000 + i}")
            }
            httpContextCacheHelper.getHosts().let {
                assertEquals(10, it.size)
                assertEquals("http://127.0.0.1:8011", it.first())
                assertEquals("http://127.0.0.1:8002", it.last())
            }

            httpContextCacheHelper.addHost("http://127.0.0.1:8002")
            httpContextCacheHelper.getHosts().let {
                assertEquals(10, it.size)
                assertEquals("http://127.0.0.1:8002", it.first())
                assertEquals("http://127.0.0.1:8003", it.last())
            }
        }
    }

    class CookiesDefaultHttpContextCacheHelperTest : DefaultHttpContextCacheHelperTest() {

        @Test
        fun testCookies() {
            assertTrue(httpContextCacheHelper.getCookies().isEmpty())
            val httpClient: HttpClient = ApacheHttpClient()
            val cookieStore = httpClient.cookieStore()
            assertTrue(cookieStore.cookies().isEmpty())

            val token = cookieStore.newCookie()
            token.setName("token")
            token.setValue("111111")
            token.setExpiryDate(DateUtils.parse("2099-01-01").time)
            token.setDomain("github.com")
            token.setPorts(intArrayOf(9999))
            token.setComment("for auth")
            token.setCommentURL("http://www.apache.org/licenses/LICENSE-2.0")
            token.setSecure(false)
            token.setPath("/")
            token.setVersion(100)

            httpContextCacheHelper.addCookies(listOf(token))

            val cookies = httpContextCacheHelper.getCookies()
            assertEquals(1, cookies.size)
            cookies.first().let {
                assertEquals("token", it.getName())
                assertEquals("111111", it.getValue())
                assertEquals("github.com", it.getDomain())
                assertEquals("for auth", it.getComment())
                assertEquals("http://www.apache.org/licenses/LICENSE-2.0", it.getCommentURL())
                assertEquals("/", it.getPath())
                assertEquals(100, it.getVersion())
                assertEquals(false, it.isSecure())
                assertEquals(DateUtils.parse("2099-01-01").time, it.getExpiryDate())
            }
        }
    }

    class SelectHostDefaultHttpContextCacheHelperTest : DefaultHttpContextCacheHelperTest() {
        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)

            //mock MessagesHelper

            val messagesHelper = mock<MessagesHelper>()

            messagesHelper.stub {
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        Mockito.eq("Select Host"),
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.argThat<Array<String>?> { arrayOf("http://localhost:8080").contentEquals(it) },
                        Mockito.any()
                    )
                ).thenReturn("http://127.0.0.1:8088")
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        Mockito.eq("Select Host"),
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.argThat<Array<String>?> {
                            arrayOf(
                                "http://127.0.0.1:8081",
                                "http://127.0.0.1:8088",
                                "http://localhost:8080"
                            ).contentEquals(it)
                        },
                        Mockito.any()
                    )
                ).thenReturn("http://127.0.0.1:8081")
                this.on(
                    messagesHelper.showEditableChooseDialog(
                        Mockito.eq("Select Host"),
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.argThat<Array<String>?> { it.size > 3 },
                        Mockito.any()
                    )
                ).thenReturn(null)
            }
            builder.bindInstance(MessagesHelper::class, messagesHelper)
        }

        @Test
        fun selectHost() {
            assertEquals("http://127.0.0.1:8088", httpContextCacheHelper.selectHost("Select Host"))
            httpContextCacheHelper.addHost("http://127.0.0.1:8081")
            assertEquals("http://127.0.0.1:8081", httpContextCacheHelper.selectHost("Select Host"))
            httpContextCacheHelper.addHost("http://127.0.0.1:8082")
            assertEquals("http://localhost:8080", httpContextCacheHelper.selectHost("Select Host"))
        }
    }
}