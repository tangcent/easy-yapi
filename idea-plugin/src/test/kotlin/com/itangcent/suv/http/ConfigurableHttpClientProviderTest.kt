package com.itangcent.suv.http

import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.SettingBinderAdaptor

/**
 * Test case of [ConfigurableHttpClientProvider]
 */
internal class ConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = ConfigurableHttpClientProvider::class

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.trustHosts = arrayOf("https://www.apache.org")
            }))
        }
    }

    override fun customConfig(): String {
        return "http.call.before=groovy:logger.info(\"call:\"+request.url())\nhttp.call.after=groovy:logger.info(\"response:\"+response.string())\nhttp.timeOut=3"
    }
}