package com.itangcent.idea.plugin.api.export.rule

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.*
import com.itangcent.idea.plugin.api.export.core.ClassExportContext
import com.itangcent.idea.plugin.api.export.core.DefaultRequestBuilderListener
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.core.RequestBuilderListener
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [RequestRuleWrap]
 */
internal class RequestRuleWrapTest : PluginContextLightCodeInsightFixtureTestCase() {

    private lateinit var userCtrlPsiClass: PsiClass
    private lateinit var userInfoPsiClass: PsiClass
    private lateinit var modelPsiClass: PsiClass
    private lateinit var requestRuleWrap: RequestRuleWrap
    private lateinit var request: Request

    @Inject
    private lateinit var duckTypeHelper: DuckTypeHelper

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
        loadSource(java.lang.Deprecated::class)
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        userInfoPsiClass = loadClass("model/UserInfo.java")!!
        modelPsiClass = loadClass("model/Model.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(RequestBuilderListener::class) { it.with(DefaultRequestBuilderListener::class).singleton() }
    }

    override fun customConfig(): String {
        return "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n"
    }

    override fun afterBind() {
        super.afterBind()
        val explicitClass = duckTypeHelper.explicit(userCtrlPsiClass)
        request = Request()
        requestRuleWrap = RequestRuleWrap(
            MethodExportContext(
                ClassExportContext(userCtrlPsiClass),
                explicitClass.methods().first()
            ),
            request
        )
    }

    fun testName() {
        request.name = "name"
        assertEquals("name", requestRuleWrap.name())
        request.name = "other"
        assertEquals("other", requestRuleWrap.name())
    }

    fun testSetName() {
        requestRuleWrap.setName("name")
        assertEquals("name", request.name)
        requestRuleWrap.setName("other")
        assertEquals("other", request.name)
    }

    fun testDesc() {
        request.desc = "desc"
        assertEquals("desc", requestRuleWrap.desc())
        request.desc = "other"
        assertEquals("other", requestRuleWrap.desc())
    }

    fun testSetDesc() {
        requestRuleWrap.setDesc("desc")
        assertEquals("desc", request.desc)
        requestRuleWrap.setDesc("other")
        assertEquals("other", request.desc)
    }

    fun testAppendDesc() {
        requestRuleWrap.appendDesc("desc")
        assertEquals("desc", request.desc)
        requestRuleWrap.appendDesc("-suffix")
        assertEquals("desc-suffix", request.desc)
    }

    fun testMethod() {
        request.method = "POST"
        assertEquals("POST", requestRuleWrap.method())
        request.method = "GET"
        assertEquals("GET", requestRuleWrap.method())
    }

    fun testSetMethod() {
        requestRuleWrap.setMethod("POST")
        assertEquals("POST", request.method)
        requestRuleWrap.setMethod("GET")
        assertEquals("GET", request.method)
    }

    fun testSetMethodIfMissed() {
        requestRuleWrap.setMethodIfMissed("POST")
        assertEquals("POST", request.method)
        requestRuleWrap.setMethodIfMissed("GET")
        assertEquals("POST", request.method)
    }

    fun testPath() {
        request.path = URL.nil()
        assertNull(requestRuleWrap.path())
        request.path = URL.of("/path")
        assertEquals("/path", requestRuleWrap.path())
        request.path = URL.of("/path", "/m/path")
        assertEquals("/path", requestRuleWrap.path())
    }

    fun testPaths() {
        request.path = URL.nil()
        assertArrayEquals(arrayOf<String>(), requestRuleWrap.paths())
        request.path = URL.of("/path")
        assertArrayEquals(arrayOf("/path"), requestRuleWrap.paths())
        request.path = URL.of("/path", "/m/path")
        assertArrayEquals(arrayOf("/path", "/m/path"), requestRuleWrap.paths())
    }

    fun testSetPath() {
        requestRuleWrap.setPath("/path")
        assertEquals(URL.of("/path"), request.path)
        requestRuleWrap.setPath("/path", "/m/path")
        assertEquals(URL.of("/path", "/m/path"), request.path)
    }

    fun testSetPaths() {
        requestRuleWrap.setPaths(listOf("/path", "/m/path"))
        assertEquals(URL.of("/path", "/m/path"), request.path)
    }

    fun testBodyType() {
        request.bodyType = "json"
        assertEquals("json", requestRuleWrap.bodyType())
        request.bodyType = "text"
        assertEquals("text", requestRuleWrap.bodyType())
    }

    fun testBodyAttr() {
        request.bodyAttr = "json"
        assertEquals("json", requestRuleWrap.bodyAttr())
        request.bodyAttr = "text"
        assertEquals("text", requestRuleWrap.bodyAttr())
    }

    fun testSetBody() {
        assertNull(request.body)
        requestRuleWrap.setBody(null)
        assertNull(request.body)
        requestRuleWrap.setBody("test")
        assertEquals("test", request.body)
        requestRuleWrap.setBody(null)
        assertEquals("test", request.body)
    }

    fun testSetBodyClass() {
        assertNull(request.body)
        requestRuleWrap.setBodyClass("com.itangcent.model.Model")
        assertNull(request.body)
        requestRuleWrap.setBodyClass(null)
        assertNull(request.body)
        request.resource = userCtrlPsiClass
        requestRuleWrap.setBodyClass("com.itangcent.model.UserInfo")
        assertEquals(
            "{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}",
            request.body.toJson()
        )
    }

    fun testAddModelAsParam() {
        assertNull(request.formParams)
        request.resource = userCtrlPsiClass
        requestRuleWrap.addModelAsParam(mapOf<String?, Any?>("x" to "1", "y" to 2))
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"\",\"required\":false,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
    }

    fun testAddModelClass() {
        assertNull(request.formParams)
        requestRuleWrap.addModelClass(null)
        assertNull(request.formParams)
        requestRuleWrap.addModelClass("com.itangcent.model.Model")
        assertNull(request.formParams)
        request.resource = userCtrlPsiClass
        requestRuleWrap.addModelClass("com.itangcent.model.UserInfo")
        assertEquals(
            "[{\"name\":\"id\",\"value\":\"0\",\"desc\":\"user id\",\"required\":false,\"type\":\"text\"},{\"name\":\"@comment\",\"value\":\"{id: user id, type: user type, name: user name, age: user age, sex: null, birthDay: user birthDay, regtime: user regtime}\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"type\",\"value\":\"0\",\"desc\":\"user type\",\"required\":false,\"type\":\"text\"},{\"name\":\"name\",\"value\":\"\",\"desc\":\"user name\",\"required\":false,\"type\":\"text\"},{\"name\":\"age\",\"value\":\"0\",\"desc\":\"user age\",\"required\":false,\"type\":\"text\"},{\"name\":\"sex\",\"value\":\"0\",\"desc\":\"\",\"required\":false,\"type\":\"text\"},{\"name\":\"birthDay\",\"value\":\"\",\"desc\":\"user birthDay\",\"required\":false,\"type\":\"text\"},{\"name\":\"regtime\",\"value\":\"\",\"desc\":\"user regtime\",\"required\":false,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
    }

    fun testSetJsonBody() {
        assertNull(request.body)
        requestRuleWrap.setJsonBody("hello", "attr")
        assertEquals("hello", request.body)
        assertEquals("attr", request.bodyAttr)
        requestRuleWrap.setJsonBody(mapOf<String?, Any?>("x" to "1", "y" to 2), null)
        assertEquals(mapOf<String?, Any?>("x" to "1", "y" to 2), request.body)
        assertEquals(null, request.bodyAttr)
    }

    fun testAddParam() {
        requestRuleWrap.addParam("x", "1", "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}]",
            request.querys.toJson()
        )
        requestRuleWrap.addParam("y", "2", false, "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":false}]",
            request.querys.toJson()
        )
        requestRuleWrap.addParam(Param().also {
            it.name = "z"
            it.value = 3
            it.desc = "The value of the z axis"
            it.required = true
        })
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":false},{\"name\":\"z\",\"value\":3,\"desc\":\"The value of the z axis\",\"required\":true}]",
            request.querys.toJson()
        )
    }

    fun testSetParam() {
        requestRuleWrap.setParam("x", "1", true, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":true}]",
            request.querys.toJson()
        )
        requestRuleWrap.setParam("x", "2", false, "Um..The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"2\",\"desc\":\"Um..The value of the x axis\",\"required\":false}]",
            request.querys.toJson()
        )
    }

    fun testAddFormParam() {
        requestRuleWrap.addFormParam("x", "1", "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
        requestRuleWrap.addFormParam("y", "2", false, "Um..The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false,\"type\":\"text\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"Um..The value of the x axis\",\"required\":false,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
        requestRuleWrap.addFormParam(FormParam().also {
            it.name = "z"
            it.value = "3"
            it.desc = "The value of the z axis"
            it.required = true
        })
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false,\"type\":\"text\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"Um..The value of the x axis\",\"required\":false,\"type\":\"text\"},{\"name\":\"z\",\"value\":\"3\",\"desc\":\"The value of the z axis\",\"required\":true}]",
            request.formParams.toJson()
        )
    }

    fun testSetFormParam() {
        requestRuleWrap.setFormParam("x", "1", true, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":true,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
        requestRuleWrap.setFormParam("x", "2", false, "Um..The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"2\",\"desc\":\"Um..The value of the x axis\",\"required\":false,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
        requestRuleWrap.setFormParam("z", "3", null, "Um..The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"2\",\"desc\":\"Um..The value of the x axis\",\"required\":false,\"type\":\"text\"},{\"name\":\"z\",\"value\":\"3\",\"desc\":\"Um..The value of the x axis\",\"required\":true,\"type\":\"text\"}]",
            request.formParams.toJson()
        )
    }

    fun testAddFormFileParam() {
        requestRuleWrap.addFormFileParam("x", true, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"desc\":\"The value of the x axis\",\"required\":true,\"type\":\"file\"}]",
            request.formParams.toJson()
        )
        requestRuleWrap.addFormFileParam("y", false, "Um..The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"desc\":\"The value of the x axis\",\"required\":true,\"type\":\"file\"},{\"name\":\"y\",\"desc\":\"Um..The value of the x axis\",\"required\":false,\"type\":\"file\"}]",
            request.formParams.toJson()
        )
    }

    fun testSetFormFileParam() {
        requestRuleWrap.setFormFileParam("x", true, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"desc\":\"The value of the x axis\",\"required\":true,\"type\":\"file\"}]",
            request.formParams.toJson()
        )
        requestRuleWrap.setFormFileParam("x", false, "Um..The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"desc\":\"Um..The value of the x axis\",\"required\":false,\"type\":\"file\"}]",
            request.formParams.toJson()
        )
    }

    fun testHeaders() {
        request.headers = arrayListOf<Header>(
            Header().also {
                it.name = "x"
                it.value = "1"
                it.required = false
                it.desc = "The value of the x axis"
            }, Header().also {
                it.name = "y"
                it.value = "2"
                it.required = true
                it.desc = "The value of the y axis"
            }, Header().also {
                it.name = "y"
                it.value = "3"
                it.required = true
                it.desc = "Another value of the y axis"
            })
        assertEquals(
            "[{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}},{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}},{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}}]",
            requestRuleWrap.headers().toJson()
        )
        assertEquals(
            "[{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}}]",
            requestRuleWrap.headers("x").toJson()
        )
        assertEquals(
            "[{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}},{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}}]",
            requestRuleWrap.headers("y").toJson()
        )
        assertEquals(
            "[]",
            requestRuleWrap.headers("z").toJson()
        )
    }

    fun testHeader() {
        request.headers = arrayListOf<Header>(
            Header().also {
                it.name = "x"
                it.value = "1"
                it.required = false
                it.desc = "The value of the x axis"
            }, Header().also {
                it.name = "y"
                it.value = "2"
                it.required = true
                it.desc = "The value of the y axis"
            }, Header().also {
                it.name = "y"
                it.value = "3"
                it.required = true
                it.desc = "Another value of the y axis"
            })
        assertEquals(
            "{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}}",
            requestRuleWrap.header("x").toJson()
        )
        assertEquals(
            "{\"request\":{\"headers\":[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]},\"header\":{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}}",
            requestRuleWrap.header("y").toJson()
        )
    }

    fun testRemoveHeader() {
        request.headers = arrayListOf<Header>(
            Header().also {
                it.name = "x"
                it.value = "1"
                it.required = false
                it.desc = "The value of the x axis"
            }, Header().also {
                it.name = "y"
                it.value = "2"
                it.required = true
                it.desc = "The value of the y axis"
            }, Header().also {
                it.name = "y"
                it.value = "3"
                it.required = true
                it.desc = "Another value of the y axis"
            })
        requestRuleWrap.removeHeader("x")
        assertEquals(
            "[{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]",
            request.headers.toJson()
        )
        requestRuleWrap.removeHeader("y")
        assertEquals(
            "[]",
            request.headers.toJson()
        )
    }

    fun testAddHeader() {
        requestRuleWrap.addHeader("x", "1", false, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}]",
            request.headers.toJson()
        )
        requestRuleWrap.addHeader("y", "2", true, "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}]",
            request.headers.toJson()
        )
        requestRuleWrap.addHeader("y", "3", true, "Another value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]",
            request.headers.toJson()
        )
        requestRuleWrap.addHeader("z", "4")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true},{\"name\":\"z\",\"value\":\"4\",\"required\":true}]",
            request.headers.toJson()
        )
    }

    fun testAddHeaderIfMissed() {
        requestRuleWrap.addHeaderIfMissed("x", "1")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"required\":true}]",
            request.headers.toJson()
        )
        requestRuleWrap.addHeaderIfMissed("y", "2")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"required\":true},{\"name\":\"y\",\"value\":\"2\",\"required\":true}]",
            request.headers.toJson()
        )
        requestRuleWrap.addHeaderIfMissed("y", "3")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"required\":true},{\"name\":\"y\",\"value\":\"2\",\"required\":true}]",
            request.headers.toJson()
        )
    }

    fun testSetHeader() {
        requestRuleWrap.setHeader("x", "1", false, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}]",
            request.headers.toJson()
        )
        requestRuleWrap.setHeader("y", "2", true, "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}]",
            request.headers.toJson()
        )
        requestRuleWrap.setHeader("y", "3", true, "Another value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"The value of the y axis Another value of the y axis\",\"required\":true}]",
            request.headers.toJson()
        )
    }

    fun testAddPathParam() {
        requestRuleWrap.addPathParam("x", "1", "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"}]",
            request.paths.toJson()
        )
        requestRuleWrap.addPathParam("y", "2", "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\"}]",
            request.paths.toJson()
        )
        requestRuleWrap.addPathParam("y", "3", "Another value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\"},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\"}]",
            request.paths.toJson()
        )
        requestRuleWrap.addPathParam(PathParam().also {
            it.name = "z"
            it.value = "3"
            it.desc = "The value of the z axis"
        })
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\"},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\"},{\"name\":\"z\",\"value\":\"3\",\"desc\":\"The value of the z axis\"}]",
            request.paths.toJson()
        )
        requestRuleWrap.addPathParam("q", "8")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\"},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\"},{\"name\":\"z\",\"value\":\"3\",\"desc\":\"The value of the z axis\"},{\"name\":\"q\",\"desc\":\"8\"}]",
            request.paths.toJson()
        )
    }

    fun testSetPathParam() {
        requestRuleWrap.setPathParam("x", "1", "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"}]",
            request.paths.toJson()
        )
        requestRuleWrap.setPathParam("y", "2", "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\"}]",
            request.paths.toJson()
        )
        requestRuleWrap.setPathParam("y", "3", "Another value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\"},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"The value of the y axis Another value of the y axis\"}]",
            request.paths.toJson()
        )
    }

    fun testSetResponseBody() {
        assertNull(request.response)
        requestRuleWrap.setResponseBody(mapOf<String?, Any?>("x" to "1", "y" to 2))
        assertEquals(mapOf<String?, Any?>("x" to "1", "y" to 2), request.response!!.first().body)
        assertEquals("raw", request.response!!.first().bodyType)
        requestRuleWrap.setResponseBody("text", "hello")
        assertEquals("hello", request.response!!.first().body)
        assertEquals("text", request.response!!.first().bodyType)
    }

    fun testSetResponseBodyClass() {
        assertNull(request.response)
        requestRuleWrap.setResponseBodyClass(null)
        assertNull(request.response)
        requestRuleWrap.setResponseBodyClass("com.itangcent.model.Model")
        assertNull(request.response)
        request.resource = userCtrlPsiClass
        requestRuleWrap.setResponseBodyClass("com.itangcent.model.UserInfo")
        assertEquals(
            "{\"id\":0,\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"name\":\"user name\",\"age\":\"user age\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\"},\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}",
            request.response!!.first().body.toJson()
        )
        requestRuleWrap.setResponseBodyClass("text", "java.lang.String")
        assertEquals(
            "",
            request.response!!.first().body.toJson()
        )
        assertEquals("text", request.response!!.first().bodyType)
        requestRuleWrap.setResponseBodyClass("raw", null)
        assertEquals(
            "",
            request.response!!.first().body.toJson()
        )
        assertEquals("text", request.response!!.first().bodyType)
    }

    fun testSetResponseCode() {
        assertNull(request.response)
        assertNoThrowable { requestRuleWrap.setResponseCode(null) }
        assertNull(request.response?.first()?.code)
        assertNoThrowable { requestRuleWrap.setResponseCode("abc") }
        assertNull(request.response?.first()?.code)
        requestRuleWrap.setResponseCode(200)
        assertEquals(200, request.response!!.first().code)
        assertNoThrowable { requestRuleWrap.setResponseCode("404") }
        assertEquals(404, request.response!!.first().code)
    }

    fun testAppendResponseBodyDesc() {
        assertNull(request.response)
        requestRuleWrap.appendResponseBodyDesc("hello")
        assertEquals("hello", request.response!!.first().bodyDesc)
        requestRuleWrap.appendResponseBodyDesc("world")
        assertEquals("hello\nworld", request.response!!.first().bodyDesc)
    }

    fun testAddResponseHeader() {
        requestRuleWrap.addResponseHeader("x", "1", false, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}]",
            request.response!!.first().headers.toJson()
        )
        requestRuleWrap.addResponseHeader("y", "2", true, "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}]",
            request.response!!.first().headers.toJson()
        )
        requestRuleWrap.addResponseHeader("y", "3", true, "Another value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]",
            request.response!!.first().headers.toJson()
        )
        requestRuleWrap.addResponseHeader("x", "4")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true},{\"name\":\"x\",\"value\":\"4\"}]",
            request.response!!.first().headers.toJson()
        )
        requestRuleWrap.addResponseHeader(Header().also {
            it.name = "z"
            it.value = "5"
            it.required = false
            it.desc = "The value of the z axis"
        })
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true},{\"name\":\"x\",\"value\":\"4\"},{\"name\":\"z\",\"value\":\"5\",\"desc\":\"The value of the z axis\",\"required\":false}]",
            request.response!!.first().headers.toJson()
        )
    }

    fun testSetResponseHeader() {
        requestRuleWrap.setResponseHeader("x", "1", false, "The value of the x axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false}]",
            request.response!!.first().headers.toJson()
        )
        requestRuleWrap.setResponseHeader("y", "2", true, "The value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"2\",\"desc\":\"The value of the y axis\",\"required\":true}]",
            request.response!!.first().headers.toJson()
        )
        requestRuleWrap.setResponseHeader("y", "3", true, "Another value of the y axis")
        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":false},{\"name\":\"y\",\"value\":\"3\",\"desc\":\"Another value of the y axis\",\"required\":true}]",
            request.response!!.first().headers.toJson()
        )
    }

    fun testHeaderRuleWrap() {

        val header = Header()
        val headWrap = HeaderRuleWrap(request, header)
        request.headers = arrayListOf(header)

        headWrap.setName("x")
        assertEquals("x", header.name)
        assertEquals("x", headWrap.name())

        headWrap.setValue("1")
        assertEquals("1", header.value)
        assertEquals("1", headWrap.value())

        headWrap.setDesc("The value of the x axis")
        assertEquals("The value of the x axis", header.desc)
        assertEquals("The value of the x axis", headWrap.desc())

        headWrap.setRequired(true)
        assertEquals(true, header.required)
        assertEquals(true, headWrap.required())

        assertEquals(
            "[{\"name\":\"x\",\"value\":\"1\",\"desc\":\"The value of the x axis\",\"required\":true}]",
            request.headers.toJson()
        )
        headWrap.remove()
        assertEquals("[]", request.headers.toJson())

        header.setExt("time", 9999)
        val copyHeader = headWrap.copy()
        assertEquals(headWrap.hashCode(), copyHeader.hashCode())
        assertEquals(headWrap, copyHeader)
    }
}