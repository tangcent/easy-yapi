package com.itangcent.easyapi.exporter.grpc

import com.itangcent.easyapi.exporter.model.grpcMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class GrpcRuleIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("model/Result.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.name=groovy:it.name().toUpperCase()
        folder.name=groovy:"grpc-custom-folder"
        method.doc=groovy:"Rule doc: " + it.name()
        """.trimIndent()
    )

    fun testRuleApiNameOverridesDefault() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val echo = endpoints.find { it.sourceMethod?.name == "echo" }
        assertNotNull(echo)
        assertEquals("ECHO", echo!!.name)
    }

    fun testRuleFolderNameOverridesDefault() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertEquals("grpc-custom-folder", endpoint.folder)
        }
    }

    fun testRuleMethodDocAppended() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())

        val echo = endpoints.find { it.sourceMethod?.name == "echo" }
        assertNotNull(echo)
        assertNotNull(echo!!.description)
        assertTrue(
            "Description should contain rule-based doc",
            echo.description!!.contains("Rule doc:")
        )
    }

    // Note: method.return and method.return.main rules cannot be tested with
    // plain Java test fixtures because GrpcTypeParser.parseMessageType() returns
    // null for non-protobuf-generated classes. These rules are tested indirectly
    // through JaxRsRuleIntegrationTest and FeignRuleIntegrationTest which use
    // standard Java type resolution.
}
