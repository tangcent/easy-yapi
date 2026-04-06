package com.itangcent.easyapi.grpc

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before

/**
 * Tests for [ProtoFileResolver].
 */
class ProtoFileResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: ProtoFileResolver

    private val protoContent = """
        syntax = "proto3";
        package test.grpc;
        option java_package = "com.itangcent.easyapi.grpc.test";
        option java_multiple_files = true;
        service EchoService {
          rpc Echo(EchoRequest) returns (EchoResponse);
          rpc EchoEmpty(EmptyRequest) returns (EchoResponse);
          rpc Reverse(ReverseRequest) returns (ReverseResponse);
        }
        message EchoRequest { string message = 1; int32 count = 2; }
        message EchoResponse { string echoed = 1; int32 received_count = 2; }
        message EmptyRequest {}
        message ReverseRequest { string text = 1; }
        message ReverseResponse { string reversed = 1; int32 length = 2; }
    """.trimIndent()

    @Before
    override fun setUp() {
        super.setUp()
        resolver = ProtoFileResolver(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    // -------------------------------------------------------------------------
    // parseProtoText tests (via ProtoUtils)
    // -------------------------------------------------------------------------

    fun testParseProtoTextExtractsPackage() {
        val result = ProtoUtils.parseProtoText("syntax = \"proto3\";\npackage test.grpc;\n")
        assertEquals("Package name should be extracted", "test.grpc", result.packageName)
    }

    fun testParseProtoTextExtractsService() {
        val text = """
            syntax = "proto3";
            package test.grpc;
            service EchoService {
              rpc Echo(EchoRequest) returns (EchoResponse);
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals("Should find one service", 1, result.services.size)
        assertEquals("Service name should be EchoService", "EchoService", result.services[0].name)
        assertEquals("Should find one method", 1, result.services[0].methods.size)
        assertEquals("Method name should be Echo", "Echo", result.services[0].methods[0].name)
    }

    fun testParseProtoTextExtractsMessage() {
        val text = """
            syntax = "proto3";
            message EchoRequest {
              string message = 1;
              int32 count = 2;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals("Should find one message", 1, result.messages.size)
        assertEquals("Message name should be EchoRequest", "EchoRequest", result.messages[0].name)
        assertEquals("Should find two fields", 2, result.messages[0].fields.size)
    }

    fun testParseProtoTextHandlesEmptyInput() {
        val result = ProtoUtils.parseProtoText("")
        assertEquals("Package should be empty", "", result.packageName)
        assertTrue("Services should be empty", result.services.isEmpty())
        assertTrue("Messages should be empty", result.messages.isEmpty())
        assertTrue("Imports should be empty", result.imports.isEmpty())
    }

    // -------------------------------------------------------------------------
    // findProtoFiles tests
    // -------------------------------------------------------------------------

    fun testFindProtoFilesReturnsEmptyWhenNoProtoFiles() {
        val files = resolver.findProtoFiles()
        assertTrue("Should return empty list when no proto files in project", files.isEmpty())
    }

    fun testFindProtoFilesFindsAddedProtoFile() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/test.proto", protoContent)
        }
        val files = resolver.findProtoFiles()
        assertTrue("Should find at least one proto file", files.isNotEmpty())
        assertTrue("Found file should be named test.proto", files.any { it.name == "test.proto" })
    }

    // -------------------------------------------------------------------------
    // resolve tests
    // -------------------------------------------------------------------------

    fun testResolveReturnsNullWhenNoProtoFiles() = runTest {
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "Echo", null)
        assertNull("Should return null when no proto files in project", result)
    }

    fun testResolveFindsServiceInProtoFile() = runTest {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/test.proto", protoContent)
        }
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "Echo", null)
        assertNotNull("Should find EchoService/Echo in proto file", result)
        assertEquals(
            "Source should be PROTO_FILE",
            DescriptorSource.PROTO_FILE,
            result!!.source
        )
    }

    fun testResolveReturnsNullForUnknownService() = runTest {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/test.proto", protoContent)
        }
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "unknown.Service", "Method", null)
        assertNull("Should return null for unknown service", result)
    }

    fun testResolveMalformedProtoReturnsNull() = runTest {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/bad.proto", "this is not valid proto")
        }
        val classLoader = Thread.currentThread().contextClassLoader
        // Should not throw; just return null
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "Echo", null)
        assertNull("Should return null for malformed proto", result)
    }

    fun testInvalidateCacheClearsCache() = runTest {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/test.proto", protoContent)
        }
        val classLoader = Thread.currentThread().contextClassLoader

        // Populate cache
        val first = resolver.resolve(classLoader, "test.grpc.EchoService", "Echo", null)
        assertNotNull("First resolve should succeed", first)

        // Invalidate and resolve again — should still work
        resolver.invalidateCache()
        val second = resolver.resolve(classLoader, "test.grpc.EchoService", "Echo", null)
        assertNotNull("Resolve after cache invalidation should still succeed", second)
        assertEquals(DescriptorSource.PROTO_FILE, second!!.source)
    }
}
