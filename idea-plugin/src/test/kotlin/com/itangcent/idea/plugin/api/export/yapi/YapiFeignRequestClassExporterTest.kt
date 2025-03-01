package com.itangcent.idea.plugin.api.export.yapi

import com.intellij.psi.PsiClass
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader

/**
 * Test case of [YapiFeignRequestClassExporter]
 * 1.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.TAG]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.STATUS]
 * 3.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.OPEN]
 */
internal class YapiFeignRequestClassExporterTest : YapiSpringClassExporterBaseTest() {

    private lateinit var userClientPsiClass: PsiClass
    override fun beforeBind() {
        super.beforeBind()
        loadFile("spring/FeignClient.java")
        userClientPsiClass = loadClass("api/feign/UserClient.java")!!
    }

    override fun customConfig(): String {
        return super.customConfig() +
                "\napi.class.parse.before=groovy:logger.info(\"before parse class:\"+it)\n" +
                "api.class.parse.after=groovy:logger.info(\"after parse class:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n" +
                "api.method.parse.after=groovy:logger.info(\"after parse method:\"+it)\n" +
                "api.param.parse.before=groovy:logger.info(\"before parse param:\"+it)\n" +
                "api.param.parse.after=groovy:logger.info(\"after parse param:\"+it)\n"
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
        builder.bind(ClassExporter::class) { it.with(YapiFeignRequestClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.feignEnable = true
            }))
        }
    }

    fun testExport() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))
        LoggerCollector.getLog()

        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(userClientPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("/user/index", request.path!!.url())
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userClientPsiClass.methods[0], (request.resource as PsiResource).resource())

            assertTrue(request.isOpen())
        }
        requests[1].let { request ->
            assertEquals("/user/get/{id}", request.path!!.url())
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("GET", request.method)
            assertEquals(userClientPsiClass.methods[1], (request.resource as PsiResource).resource())

            assertFalse(request.isOpen())
            assertEquals("undone", request.getStatus())
            assertTrue(request.getTags().contains("deprecated"))
        }
        assertEquals(ResultLoader.load(), LoggerCollector.getLog().toUnixString())
    }
}