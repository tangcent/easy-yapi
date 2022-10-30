package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.test.StringResource
import com.itangcent.test.assertContentEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

/**
 * Test case of [RemoteConfigSettingsHelper]
 */
internal class RemoteConfigSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var remoteConfigSettingsHelper: RemoteConfigSettingsHelper

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        val resourceResolver = mock<ResourceResolver> {
            this.on {
                it.resolve(eq("https://github.com/tangcent/easy-yapi/raw/master/third/a.config"))
            }.thenReturn(
                StringResource(
                    "https://github.com/tangcent/easy-yapi/raw/master/third/a.config",
                    "a=1"
                )
            )
            this.on {
                it.resolve(eq("https://github.com/tangcent/easy-yapi/raw/master/third/b.config"))
            }.thenReturn(
                StringResource(
                    "https://github.com/tangcent/easy-yapi/raw/master/third/b.config",
                    "b=2"
                )
            )
            this.on {
                it.resolve(eq("https://github.com/tangcent/easy-yapi/raw/master/third/c.config"))
            }.thenReturn(
                StringResource(
                    "https://github.com/tangcent/easy-yapi/raw/master/third/c.config",
                    "c=3"
                )
            )
        }
        builder.bindInstance(ResourceResolver::class, resourceResolver)
    }

    @Test
    fun remoteConfigContent() {
        assertEquals("", remoteConfigSettingsHelper.remoteConfigContent())
        settings.remoteConfig = arrayOf(
            "https://github.com/tangcent/easy-yapi/raw/master/third/a.config",
            "!https://github.com/tangcent/easy-yapi/raw/master/third/b.config",
            "https://github.com/tangcent/easy-yapi/raw/master/third/c.config"
        )
        assertEquals(
            "a=1\n" +
                    "c=3", remoteConfigSettingsHelper.remoteConfigContent()
        )
    }

    @Test
    fun loadConfig() {
        assertEquals(
            "a=1",
            remoteConfigSettingsHelper.loadConfig("https://github.com/tangcent/easy-yapi/raw/master/third/a.config")
        )
    }

    @Test
    fun testRemoteConfig() {
        val config = arrayOf(
            "https://github.com/tangcent/easy-yapi/raw/master/third/a.config",
            "!https://github.com/tangcent/easy-yapi/raw/master/third/b.config",
            "https://github.com/tangcent/easy-yapi/raw/master/third/c.config"
        ).parse()
        assertEquals(
            listOf(
                true to "https://github.com/tangcent/easy-yapi/raw/master/third/a.config",
                false to "https://github.com/tangcent/easy-yapi/raw/master/third/b.config",
                true to "https://github.com/tangcent/easy-yapi/raw/master/third/c.config"
            ),
            config
        )

        config.setSelected(0, false)
        config.setSelected(1, true)


        assertEquals(
            listOf(
                false to "https://github.com/tangcent/easy-yapi/raw/master/third/a.config",
                true to "https://github.com/tangcent/easy-yapi/raw/master/third/b.config",
                true to "https://github.com/tangcent/easy-yapi/raw/master/third/c.config"
            ),
            config
        )

        assertContentEquals(
            arrayOf(
                "!https://github.com/tangcent/easy-yapi/raw/master/third/a.config",
                "https://github.com/tangcent/easy-yapi/raw/master/third/b.config",
                "https://github.com/tangcent/easy-yapi/raw/master/third/c.config"
            ),
            config.toConfig()
        )
    }
}