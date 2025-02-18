package com.itangcent.idea.config

import com.google.inject.Inject
import com.intellij.openapi.ui.Messages
import com.itangcent.common.utils.readString
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelperImpl
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test case of [CachedResourceResolver]
 */
//@DisabledOnOs(OS.WINDOWS)
internal class CachedResourceResolverTest : AdvancedContextTest() {

    @Inject
    private lateinit var resourceResolver: ResourceResolver

    @Inject
    private lateinit var httpSettingsHelper: HttpSettingsHelper

    private val delegateHttpSettingsHelper: HttpSettingsHelper = HttpSettingsHelperImpl()

    private val settings: Settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
        builder.bind(ResourceResolver::class) {
            it.with(CachedResourceResolver::class)
        }

        //mock MessagesHelper
        val messagesHelper = Mockito.mock(MessagesHelper::class.java)
        Mockito.`when`(
            messagesHelper.showYesNoDialog(
                Mockito.eq("Do you trust [https://raw.githubusercontent.com/tangcent]?"),
                Mockito.anyString(),
                Mockito.any()
            )
        ).thenReturn(Messages.YES)
        Mockito.`when`(
            messagesHelper.showYesNoDialog(
                Mockito.eq("Do you trust [http://www.apache.org]?"),
                Mockito.anyString(),
                Mockito.any()
            )
        ).thenReturn(Messages.NO)
        builder.bindInstance(MessagesHelper::class, messagesHelper)

        //spy HttpSettingsHelper
        builder.bindInstance(HttpSettingsHelper::class, Mockito.spy(delegateHttpSettingsHelper))
    }

    override fun afterBind(actionContext: ActionContext) {
        super.afterBind(actionContext)
        actionContext.init(delegateHttpSettingsHelper)
        actionContext.cache("project_path", tempDir.toString())
    }

    @Test
    fun testCachedResourceFromGithub() {
        settings.httpTimeOut = 5

        try {
            val resource =
                resourceResolver.resolve("https://raw.githubusercontent.com/tangcent/easy-yapi/master/LICENSE")

            //the first time, should get it from http
            val content = resource.content
            logger.info("load LICENSE:\n$content")
            assertNotNull(content)

            //the second time, should get it from cache
            val content2 = resource.content
            logger.info("load LICENSE:\n$content2")
            assertNotNull(content2)
            assertEquals(content, content2)

            //only the first time call the checkTrustUrl
            Mockito.verify(httpSettingsHelper, times(1))
                .checkTrustUrl(eq("https://raw.githubusercontent.com/tangcent/easy-yapi/master/LICENSE"), any())

            resource.bytes.let {
                assertNotNull(it)
                assertEquals(content, String(it, Charsets.UTF_8))
            }
            resource.inputStream.let {
                assertNotNull(it)
                assertEquals(content, it.use { input -> input.readString() })
            }
        } catch (e: IOException) {
            logger.warn("failed connect raw.githubusercontent.com")
        }
    }

    @Test
    fun testCachedResourceFromApache() {
        settings.httpTimeOut = 5

        try {
            //test forbidden
            val resource = resourceResolver.resolve("http://www.apache.org/licenses/LICENSE-2.0")
            val content = resource.content
            logger.info("load apache LICENSE-2.0:\n$content")
            assertNotNull(content)
            assertEquals("", content)

            resource.bytes.let {
                assertNotNull(it)
                assertTrue(it.isEmpty())
            }
            resource.inputStream.let {
                assertNotNull(it)
                assertEquals("", it.use { input -> input.readString() })
            }
        } catch (e: IOException) {
            logger.warn("failed connect apache.org")
        }
    }
}