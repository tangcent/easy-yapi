package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import org.junit.jupiter.api.condition.OS

/**
 * Test case of export spring apis with [PostmanRequestBuilderListener]
 * 1.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.POST_PRE_REQUEST]
 * 2.support rule:[com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.POST_TEST]
 *
 */
internal class PostmanSpringRequestClassExporterTest : PostmanSpringClassExporterBaseTest() {

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

    override fun customConfig(): String {
        return super.customConfig() +
                "\napi.class.parse.before=groovy:logger.info(\"before parse class:\"+it)\n" +
                "api.class.parse.after=groovy:logger.info(\"after parse class:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n"
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
        (classExporter as Worker).waitCompleted()
        requests[0].let { request ->
            assertEquals("say hello", request.name)
            assertEquals("not update anything", request.desc)
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[0], (request.resource as PsiResource).resource())

            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                request.getExt(ClassExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", request.getExt(ClassExportRuleKeys.POST_TEST.name())
            )
            assertNull(request.body)
            assertEquals("", request.response!!.first().body.toJson())
        }
        requests[1].let { request ->
            assertEquals("get user info", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("GET", request.method)
            assertEquals(userCtrlPsiClass.methods[1], (request.resource as PsiResource).resource())

            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                request.getExt(ClassExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", request.getExt(ClassExportRuleKeys.POST_TEST.name())
            )
            assertNull(request.body.toJson())
            assertEquals(
                "{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"success\",\"data\":{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"Tony Stark\",\"age\":45,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}}",
                request.response!!.first().body.toJson()
            )
        }
        requests[2].let { request ->
            assertEquals("create new user", request.name)
            assertTrue(request.desc.isNullOrEmpty())
            assertEquals("POST", request.method)
            assertEquals(userCtrlPsiClass.methods[2], (request.resource as PsiResource).resource())

            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                request.getExt(ClassExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", request.getExt(ClassExportRuleKeys.POST_TEST.name())
            )
            assertEquals(
                "{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}",
                request.body.toJson()
            )
            assertEquals(
                "{\"code\":0,\"@comment\":{\"code\":\"response code\",\"msg\":\"message\",\"data\":\"response data\"},\"msg\":\"\",\"data\":{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"type@options\":[{\"value\":1,\"desc\":\"administration\"},{\"value\":2,\"desc\":\"a person, an animal or a plant\"},{\"value\":3,\"desc\":\"Anonymous visitor\"}],\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}}",
                request.response!!.first().body.toJson()
            )
        }

        assertEquals(ResultLoader.load(), LoggerCollector.getLog().toUnixString())
    }
}