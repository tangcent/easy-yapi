package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.psi.PsiResource

/**
 * Test case of [YapiSpringRequestClassExporter]
 * 1.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.TAG]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.STATUS]
 * 3.support rule:[com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys.OPEN]
 */
internal class YapiSpringRequestClassExporterTest : YapiSpringClassExporterBaseTest() {

    fun testExport() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("say hello", request.name)
            assertEquals("say hello\n" +
                    "not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())

            assertTrue(request.isOpen())
        }
        requests[1].let { request ->
            assertEquals("get user info", request.name)
            assertEquals("get user info", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())

            assertFalse(request.isOpen())
            assertEquals("undone", request.getStatus())
            assertTrue(request.getTags()!!.contains("deprecated"))
        }
    }
}