package com.itangcent.easyapi.grpc

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before

/**
 * Tests for [StubClassResolver].
 *
 * Uses PSI fixture sources that mimic a real protoc-generated + user-written service structure:
 *   - EchoServiceGrpc.EchoServiceImplBase  (generated)
 *   - EchoServiceImpl extends EchoServiceImplBase  (user-written)
 *   - EchoRequest / EchoResponse  (generated message classes)
 */
class StubClassResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: StubClassResolver

    private val echoRequestSource = """
        package test.grpc;
        public final class EchoRequest extends com.google.protobuf.GeneratedMessageV3 {
            public static final int MESSAGE_FIELD_NUMBER = 1;
            public static final int COUNT_FIELD_NUMBER = 2;
            public String getMessage() { return ""; }
            public int getCount() { return 0; }
        }
    """.trimIndent()

    private val echoResponseSource = """
        package test.grpc;
        public final class EchoResponse extends com.google.protobuf.GeneratedMessageV3 {
            public static final int ECHOED_FIELD_NUMBER = 1;
            public String getEchoed() { return ""; }
        }
    """.trimIndent()

    // Generated outer class with ImplBase inner class
    private val echoServiceGrpcSource = """
        package test.grpc;
        public final class EchoServiceGrpc {
            public static final String SERVICE_NAME = "test.grpc.EchoService";
            public static abstract class EchoServiceImplBase implements io.grpc.BindableService {
                public void echo(EchoRequest request, io.grpc.stub.StreamObserver<EchoResponse> responseObserver) {}
            }
        }
    """.trimIndent()

    // User-written service implementation
    private val echoServiceImplSource = """
        package test.grpc;
        public class EchoServiceImpl extends EchoServiceGrpc.EchoServiceImplBase {
            @Override
            public void echo(EchoRequest request, io.grpc.stub.StreamObserver<EchoResponse> responseObserver) {}
        }
    """.trimIndent()

    @Before
    override fun setUp() {
        super.setUp()
        resolver = StubClassResolver(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    private fun addStubSources() {
        ApplicationManager.getApplication().runWriteAction {
            // Add minimal stubs for library types so PSI can resolve them
            myFixture.addFileToProject("io/grpc/stub/StreamObserver.java",
                "package io.grpc.stub; public interface StreamObserver<V> {}")
            myFixture.addFileToProject("io/grpc/BindableService.java",
                "package io.grpc; public interface BindableService {}")
            myFixture.addFileToProject("com/google/protobuf/GeneratedMessageV3.java",
                "package com.google.protobuf; public abstract class GeneratedMessageV3 {}")

            myFixture.addFileToProject("test/grpc/EchoRequest.java", echoRequestSource)
            myFixture.addFileToProject("test/grpc/EchoResponse.java", echoResponseSource)
            myFixture.addFileToProject("test/grpc/EchoServiceGrpc.java", echoServiceGrpcSource)
            myFixture.addFileToProject("test/grpc/EchoServiceImpl.java", echoServiceImplSource)
        }
    }

    fun testFindImplBaseReturnsNullWhenNoStubs() {
        val implBase = resolver.findImplBase("EchoService", "test.grpc")
        assertNull("Should return null when no Grpc classes in project", implBase)
    }

    fun testFindImplBaseFindsEchoServiceImplBase() {
        addStubSources()
        val implBase = resolver.findImplBase("EchoService", "test.grpc")
        assertNotNull("Should find EchoServiceImplBase", implBase)
        assertEquals("EchoServiceImplBase", implBase!!.name)
    }

    fun testFindImplClassFindsEchoServiceImpl() {
        addStubSources()
        val implBase = resolver.findImplBase("EchoService", "test.grpc")!!
        val implClass = resolver.findImplClass(implBase)
        assertNotNull("Should find EchoServiceImpl", implClass)
        assertEquals("EchoServiceImpl", implClass!!.name)
    }

    fun testExtractMessageTypesFromEchoMethod() = runTest {
        addStubSources()
        val implBase = resolver.findImplBase("EchoService", "test.grpc")!!
        val implClass = resolver.findImplClass(implBase)!!
        val methods = implClass.findMethodsByName("echo", true)
        assertTrue("Should find echo method, found ${methods.size}", methods.isNotEmpty())
        val method = methods.first()
        val params = method.parameterList.parameters
        assertEquals("Should have 2 params", 2, params.size)
        
        val param0Type = params[0].type
        val param1Type = params[1].type
        
        // Debug via assertion messages
        assertNotNull("param0 type should not be null, canonicalText=${param0Type.canonicalText}", 
            com.intellij.psi.util.PsiTypesUtil.getPsiClass(param0Type))
        
        assertTrue("param1 should be PsiClassType but is ${param1Type.javaClass.name}, canonicalText=${param1Type.canonicalText}",
            param1Type is com.intellij.psi.PsiClassType)
        
        val classType = param1Type as com.intellij.psi.PsiClassType
        assertTrue("param1 type args should not be empty, resolve=${classType.resolve()?.qualifiedName}, rawType=${classType.rawType().canonicalText}, hasParams=${classType.hasParameters()}",
            classType.parameters.isNotEmpty())
        
        val types = resolver.extractMessageTypes(method)
        assertNotNull("Should extract message types from echo method", types)
        assertEquals("Input should be EchoRequest", "EchoRequest", types!!.first.name)
        assertEquals("Output should be EchoResponse", "EchoResponse", types.second.name)
    }

    fun testBuildMessageDefExtractsFields() = runTest {
        addStubSources()
        val facade = com.intellij.psi.JavaPsiFacade.getInstance(project)
        val echoRequestClass = facade.findClass(
            "test.grpc.EchoRequest",
            com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        )!!
        val msgDef = resolver.buildMessageDef(echoRequestClass)
        assertNotNull("MessageDef should be built", msgDef)
        assertEquals("EchoRequest", msgDef!!.name)
        assertTrue("Should have message field at number 1", msgDef.fields.any { it.name == "message" && it.number == 1 })
        assertTrue("Should have count field at number 2", msgDef.fields.any { it.name == "count" && it.number == 2 })
    }

    fun testResolveReturnsNullWhenNoStubs() = runTest {
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "echo", null)
        assertNull("Should return null when no stub classes in project", result)
    }

    fun testResolveFindsEchoMethod() = runTest {
        addStubSources()
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "echo", null)
        assertNotNull("Should resolve EchoService/echo from PSI stubs", result)
        assertEquals("Source should be STUB_CLASS", DescriptorSource.STUB_CLASS, result!!.source)
    }

    fun testResolveReturnsNullForUnknownService() = runTest {
        addStubSources()
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "unknown.Service", "method", null)
        assertNull("Should return null for unknown service", result)
    }

    fun testResolveReturnsNullForUnknownMethod() = runTest {
        addStubSources()
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "unknownMethod", null)
        assertNull("Should return null for unknown method", result)
    }
}
