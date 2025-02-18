package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonObject
import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.helper.YapiTokenChecker
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [YapiTokenCheckerSupport]
 */
internal class YapiTokenCheckerSupportTest : BaseContextTest() {

    @Inject
    private lateinit var yapiTokenChecker: YapiTokenChecker

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(YapiTokenChecker::class) { it.with(YapiTokenCheckerSupport::class) }
        val yapiApiHelper = mock<YapiApiHelper>()
        yapiApiHelper.stub {
            this.on(yapiApiHelper.getProjectInfo("123"))
                    .thenReturn(JsonObject())
            this.on(yapiApiHelper.getProjectInfo("abc"))
                    .thenReturn(null)
        }
        builder.bind(YapiApiHelper::class) {
            it.toInstance(yapiApiHelper)
        }
    }

    @Test
    fun testCheckToken() {
        assertTrue(yapiTokenChecker.checkToken("123"))
        assertFalse(yapiTokenChecker.checkToken("abc"))
    }
}