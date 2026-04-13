package com.itangcent.easyapi.exporter.grpc

import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.exporter.model.grpcMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class GrpcClassExporterLifecycleTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: GrpcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = GrpcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("grpc/BindableService.java")
        loadFile("grpc/StreamObserver.java")
        loadFile("grpc/GrpcService.java")
        loadFile("grpc/EchoRequest.java")
        loadFile("grpc/EchoResponse.java")
        loadFile("grpc/EchoServiceGrpc.java")
        loadFile("grpc/EchoServiceImpl.java")
        loadFile("grpc/UserInfo.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        """
        api.class.parse.before=groovy:logger.info("lifecycle:api.class.parse.before:" + it.name())
        api.class.parse.after=groovy:logger.info("lifecycle:api.class.parse.after:" + it.name())
        api.method.parse.before=groovy:logger.info("lifecycle:api.method.parse.before:" + it.name())
        api.method.parse.after=groovy:logger.info("lifecycle:api.method.parse.after:" + it.name())
        export.after=groovy:logger.info("lifecycle:export.after:" + it.name())
        """.trimIndent()
    )


    fun testGrpcClassParseBeforeAndAfter() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export at least one endpoint", endpoints.isNotEmpty())
    }

    fun testGrpcExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export at least one endpoint", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertNotNull("Each endpoint should have source method for EXPORT_AFTER", endpoint.sourceMethod)
        }
    }

    fun testGrpcNonServiceNoEvents() = runTest {
        val psiClass = findClass("com.itangcent.grpc.model.UserInfo")
        assertNotNull("Should find UserInfo class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Non-gRPC class should return empty endpoints", endpoints.isEmpty())
    }

    fun testGrpcClassParseAfterFiresOnSuccess() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        assertTrue("Should have multiple endpoints", endpoints.size > 1)
    }

    fun testGrpcEndpointsHaveCorrectMetadata() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertNotNull("Each endpoint should have gRPC metadata", endpoint.grpcMetadata)
            assertNotNull("Each endpoint should have a path", endpoint.path)
            assertTrue("Path should start with /", endpoint.path.startsWith("/"))
        }
    }

    fun testGrpcUnaryMethodWithEvents() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val echoEndpoint = endpoints.find { it.name == "echo" }

        assertNotNull("Should find echo endpoint", echoEndpoint)
        assertEquals("/com.itangcent.grpc.EchoService/echo", echoEndpoint!!.path)
        assertEquals(GrpcStreamingType.UNARY, echoEndpoint.grpcMetadata?.streamingType)
    }

    fun testGrpcClientStreamingMethodWithEvents() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val streamEndpoint = endpoints.find { it.name == "echoStream" }

        assertNotNull("Should find echoStream endpoint", streamEndpoint)
        assertEquals("/com.itangcent.grpc.EchoService/echoStream", streamEndpoint!!.path)
        assertEquals(GrpcStreamingType.CLIENT_STREAMING, streamEndpoint.grpcMetadata?.streamingType)
    }
}
