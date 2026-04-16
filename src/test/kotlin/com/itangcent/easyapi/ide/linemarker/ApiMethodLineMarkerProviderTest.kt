package com.itangcent.easyapi.ide.linemarker

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.util.ide.ProjectClassAvailabilityService

class ApiMethodLineMarkerProviderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var lineMarkerProvider: ApiMethodLineMarkerProvider

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        lineMarkerProvider = ApiMethodLineMarkerProvider()
    }

    private fun loadTestFiles() {
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testLineMarkerProviderDoesNotFailOnNonApiMethod() = runTest {
        val file = myFixture.configureByFile("api/UserCtrl.java")
        val classes = PsiTreeUtil.getChildrenOfType(file, PsiClass::class.java) ?: emptyArray()

        assertTrue("Should have classes in test file", classes.isNotEmpty())

        val methods = classes.flatMap { it.methods.toList() }
        assertTrue("Should have methods in test file", methods.isNotEmpty())

        methods.forEach { method ->
            val identifier = method.nameIdentifier
            if (identifier != null) {
                val marker = lineMarkerProvider.getLineMarkerInfo(identifier)
            }
        }
    }

    fun testProjectClassAvailabilityServiceIsUsed() = runTest {
        val availabilityService = ProjectClassAvailabilityService.getInstance(project)

        val hasSpringAnnotations = availabilityService.hasAnyClassInProject(
            setOf(
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping"
            )
        )
        assertTrue("Should detect Spring annotations in project", hasSpringAnnotations)

        val hasJaxrsAnnotations = availabilityService.hasAnyClassInProject(
            setOf(
                "javax.ws.rs.GET",
                "javax.ws.rs.Path"
            )
        )
        assertFalse("Should not detect JAX-RS annotations in project", hasJaxrsAnnotations)
    }

    fun testGrpcFrameworkCheck() = runTest {
        val availabilityService = ProjectClassAvailabilityService.getInstance(project)

        val hasGrpc = availabilityService.hasAnyClassInProject(
            setOf("net.devh.boot.grpc.server.service.GrpcService")
        ) || availabilityService.hasClassInProject("io.grpc.BindableService")

        assertFalse("Should not detect gRPC framework in project", hasGrpc)
    }

    fun testOnlyChecksAvailableAnnotations() = runTest {
        val availabilityService = ProjectClassAvailabilityService.getInstance(project)

        val allApiAnnotations = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "javax.ws.rs.GET",
            "javax.ws.rs.POST",
            "javax.ws.rs.PUT",
            "javax.ws.rs.DELETE",
            "javax.ws.rs.PATCH",
            "javax.ws.rs.Path"
        )

        val availableAnnotations = allApiAnnotations.filter {
            availabilityService.hasClassInProject(it)
        }

        assertTrue("Should have some available Spring annotations", availableAnnotations.isNotEmpty())
        assertTrue(
            "Available annotations should be subset of all annotations",
            availableAnnotations.size < allApiAnnotations.size
        )
    }
}
