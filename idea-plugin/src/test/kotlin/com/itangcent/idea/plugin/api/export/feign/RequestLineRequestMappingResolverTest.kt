package com.itangcent.idea.plugin.api.export.feign

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [RequestLineRequestMappingResolver]
 */
internal class RequestLineRequestMappingResolverTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var requestLineRequestMappingResolver: RequestLineRequestMappingResolver

    private lateinit var primitiveUserClientPsiClass: PsiClass

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.String::class)
        loadSource(java.lang.Integer::class)
        loadSource(java.lang.Long::class)
        loadSource(Collection::class)
        loadSource(Map::class)
        loadSource(List::class)
        loadSource(LinkedList::class)
        loadSource(LocalDate::class)
        loadSource(LocalDateTime::class)
        loadSource(HashMap::class)
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("feign/Body.java")
        loadFile("feign/Headers.java")
        loadFile("feign/Param.java")
        loadFile("feign/RequestLine.java")
        primitiveUserClientPsiClass = loadClass("api/feign/PrimitiveUserClient.java")!!
        settings.feignEnable = true
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }

    fun testResolveRequestMapping() {
        assertEquals(
            "{\"method\":\"POST\",\"value\":\"/add\"}",
            requestLineRequestMappingResolver.resolveRequestMapping(primitiveUserClientPsiClass.methods[0]).toJson()
        )
        assertEquals(
            "{\"method\":\"POST\",\"value\":\"/list/{type}\"}",
            requestLineRequestMappingResolver.resolveRequestMapping(primitiveUserClientPsiClass.methods[1]).toJson()
        )
    }
}