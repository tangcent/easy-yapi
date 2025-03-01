package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [ActuatorEndpointExporter]
 */
internal class ActuatorEndpointExporterTest
    : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var standardEndpointPsiClass: PsiClass
    private lateinit var webAnnEndpointPsiClass: PsiClass
    private lateinit var controllerAnnEndpointPsiClass: PsiClass
    private lateinit var restControllerAnnEndpointPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.Boolean::class)
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
        loadFile("spring/Endpoint.java")
        loadFile("spring/WebEndpoint.java")
        loadFile("spring/ControllerEndpoint.java")
        loadFile("spring/RestControllerEndpoint.java")
        loadFile("spring/ReadOperation.java")
        loadFile("spring/WriteOperation.java")
        loadFile("spring/DeleteOperation.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/Selector.java")
        standardEndpointPsiClass = loadClass("api/actuator/StandardEndpoint.java")!!
        webAnnEndpointPsiClass = loadClass("api/actuator/WebAnnEndpoint.java")!!
        controllerAnnEndpointPsiClass = loadClass("api/actuator/ControllerAnnEndpoint.java")!!
        restControllerAnnEndpointPsiClass = loadClass("api/actuator/RestControllerAnnEndpoint.java")!!
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ClassExporter::class) { it.with(ActuatorEndpointExporter::class).singleton() }
    }

    fun testExportStandard() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(standardEndpointPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("endpointByGet", request.name)
            assertEquals("", request.desc)
            assertEquals("GET", request.method)
            assertEquals("/actuator/standard/{username}/{age}", request.path?.url())
            assertEquals(
                "[{\"name\":\"username\",\"desc\":\"the username to use\"},{\"name\":\"age\",\"desc\":\"age of the user\"}]",
                request.paths.toJson()
            )
            assertNull(request.querys)
            assertNull(request.body)
            assertNull(request.bodyAttr)
            assertNull(request.bodyType)
            assertEquals(standardEndpointPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("endpointByPost", request.name)
            assertEquals("", request.desc)
            assertEquals("POST", request.method)
            assertEquals("/actuator/standard/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(request.querys)
            assertEquals(
                "{\"@comment\":{\"username\":\"the username to use\",\"age\":\"age of the user\"},\"age\":0,\"username\":\"\"}",
                request.body.toJson()
            )
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(standardEndpointPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
        requests[2].let { request ->
            assertEquals("endpointByDelete", request.name)
            assertEquals("", request.desc)
            assertEquals("DELETE", request.method)
            assertEquals("/actuator/standard/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(null, request.querys.toJson())
            assertEquals("{\"completely\":false,\"@comment\":{\"completely\":\"real delete\"}}", request.body.toJson())
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(standardEndpointPsiClass.methods[2], (request.resource as PsiResource).resource())
        }
    }

    fun testExportWeb() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(webAnnEndpointPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("endpointByGet", request.name)
            assertEquals("", request.desc)
            assertEquals("GET", request.method)
            assertEquals("/actuator/web/{username}/{age}", request.path?.url())
            assertEquals(
                "[{\"name\":\"username\",\"desc\":\"the username to use\"},{\"name\":\"age\",\"desc\":\"age of the user\"}]",
                request.paths.toJson()
            )
            assertNull(request.querys)
            assertNull(request.body)
            assertNull(request.bodyAttr)
            assertNull(request.bodyType)
            assertEquals(webAnnEndpointPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("endpointByPost", request.name)
            assertEquals("", request.desc)
            assertEquals("POST", request.method)
            assertEquals("/actuator/web/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(request.querys)
            assertEquals(
                "{\"@comment\":{\"username\":\"the username to use\",\"age\":\"age of the user\"},\"age\":0,\"username\":\"\"}",
                request.body.toJson()
            )
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(webAnnEndpointPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
        requests[2].let { request ->
            assertEquals("endpointByDelete", request.name)
            assertEquals("", request.desc)
            assertEquals("DELETE", request.method)
            assertEquals("/actuator/web/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(null, request.querys.toJson())
            assertEquals("{\"completely\":false,\"@comment\":{\"completely\":\"real delete\"}}", request.body.toJson())
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(webAnnEndpointPsiClass.methods[2], (request.resource as PsiResource).resource())
        }
    }

    fun testExportController() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(controllerAnnEndpointPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("endpointByGet", request.name)
            assertEquals("", request.desc)
            assertEquals("GET", request.method)
            assertEquals("/actuator/controller/{username}/{age}", request.path?.url())
            assertEquals(
                "[{\"name\":\"username\",\"desc\":\"the username to use\"},{\"name\":\"age\",\"desc\":\"age of the user\"}]",
                request.paths.toJson()
            )
            assertNull(request.querys)
            assertNull(request.body)
            assertNull(request.bodyAttr)
            assertNull(request.bodyType)
            assertEquals(controllerAnnEndpointPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("endpointByPost", request.name)
            assertEquals("", request.desc)
            assertEquals("POST", request.method)
            assertEquals("/actuator/controller/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(request.querys)
            assertEquals(
                "{\"@comment\":{\"username\":\"the username to use\",\"age\":\"age of the user\"},\"age\":0,\"username\":\"\"}",
                request.body.toJson()
            )
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(controllerAnnEndpointPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
        requests[2].let { request ->
            assertEquals("endpointByDelete", request.name)
            assertEquals("", request.desc)
            assertEquals("DELETE", request.method)
            assertEquals("/actuator/controller/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(null, request.querys.toJson())
            assertEquals("{\"completely\":false,\"@comment\":{\"completely\":\"real delete\"}}", request.body.toJson())
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(controllerAnnEndpointPsiClass.methods[2], (request.resource as PsiResource).resource())
        }
    }

    fun testExportRestController() {
        assertTrue(classExporter.support(Request::class))
        assertFalse(classExporter.support(MethodDoc::class))

        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(restControllerAnnEndpointPsiClass, requestOnly {
                requests.add(it)
            })
        }
        requests[0].let { request ->
            assertEquals("endpointByGet", request.name)
            assertEquals("", request.desc)
            assertEquals("GET", request.method)
            assertEquals("/actuator/rest/{username}/{age}", request.path?.url())
            assertEquals(
                "[{\"name\":\"username\",\"desc\":\"the username to use\"},{\"name\":\"age\",\"desc\":\"age of the user\"}]",
                request.paths.toJson()
            )
            assertNull(request.querys)
            assertNull(request.body)
            assertNull(request.bodyAttr)
            assertNull(request.bodyType)
            assertEquals(restControllerAnnEndpointPsiClass.methods[0], (request.resource as PsiResource).resource())
        }
        requests[1].let { request ->
            assertEquals("endpointByPost", request.name)
            assertEquals("", request.desc)
            assertEquals("POST", request.method)
            assertEquals("/actuator/rest/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(request.querys)
            assertEquals(
                "{\"@comment\":{\"username\":\"the username to use\",\"age\":\"age of the user\"},\"age\":0,\"username\":\"\"}",
                request.body.toJson()
            )
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(restControllerAnnEndpointPsiClass.methods[1], (request.resource as PsiResource).resource())
        }
        requests[2].let { request ->
            assertEquals("endpointByDelete", request.name)
            assertEquals("", request.desc)
            assertEquals("DELETE", request.method)
            assertEquals("/actuator/rest/{id}", request.path?.url())
            assertEquals("[{\"name\":\"id\",\"desc\":\"user id\"}]", request.paths.toJson())
            assertNull(null, request.querys.toJson())
            assertEquals("{\"completely\":false,\"@comment\":{\"completely\":\"real delete\"}}", request.body.toJson())
            assertEquals("", request.bodyAttr)
            assertEquals("json", request.bodyType)
            assertEquals(restControllerAnnEndpointPsiClass.methods[2], (request.resource as PsiResource).resource())
        }
    }

}