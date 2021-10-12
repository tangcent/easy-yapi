package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.model.Request
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader

/**
 * Test case of [YapiSpringRequestClassExporter]
 * 1.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.TAG]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.STATUS]
 * 3.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.OPEN]
 */
internal class YapiSpringRequestClassExporterTest : YapiSpringClassExporterBaseTest() {
    override fun customConfig(): String {
        return super.customConfig() +
                "\napi.class.parse.before=groovy:logger.info(\"before parse class:\"+it)\n" +
                "api.class.parse.after=groovy:logger.info(\"after parse class:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n" +
                "api.param.parse.before=groovy:logger.info(\"before parse param:\"+it)\n" +
                "api.param.parse.before=groovy:logger.info(\"before parse param:\"+it)\n"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    fun testExport() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        classExporter.export(defaultCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())

            assertTrue(request.isOpen())
        }
        requests[1].let { request ->
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())

            assertFalse(request.isOpen())
            assertEquals("undone", request.getStatus())
            assertTrue(request.getTags()!!.contains("deprecated"))
        }

        val apiCntInUserCtrl = userCtrlPsiClass.methods.size
        requests[apiCntInUserCtrl + 0].let { request ->
            assertEquals("call with query", request.name)
            assertEquals("", request.desc)
            assertEquals("GET", request.method)
            assertEquals(defaultCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[apiCntInUserCtrl + 1].let { request ->
            assertEquals("call with form", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("POST", request.method)
            assertEquals(defaultCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())

            assertFalse(request.isOpen())
            assertEquals("done", request.getStatus())
        }
        requests[apiCntInUserCtrl + 2].let { request ->
            assertEquals("call with body", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("POST", request.method)
            assertEquals(defaultCtrlPsiClass.methods[2], (request.resource as PsiResource).resource())

            assertFalse(request.isOpen())
            assertEquals("done", request.getStatus())
        }

        assertEquals(ResultLoader.load(), LoggerCollector.getLog().toUnixString())
    }
}