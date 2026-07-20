package com.itangcent.easyapi.core.ide.linemarker

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Characterization test for [ApiMethodLineMarkerProvider] behavior.
 *
 * Captures the line-marker's `getLineMarkerInfo` decisions (null vs non-null)
 * for a representative fixture set BEFORE the Item 1 refactor (recognizer
 * relocation + EP-seam refactor). The test MUST pass against current
 * (pre-patch) code AND against post-patch code &mdash; byte-identical
 * behavior per Migration Req 5.1.
 *
 * Fixture set per `requirements-recognizer-relocation.md` Req 4.7:
 *  (a) Spring MVC controller method (`@RequestMapping`)
 *  (b) JAX-RS resource method (`@GET` + `@Path`)
 *  (c) gRPC unary method (`(Req, StreamObserver<Resp>) -> void` on a
 *      class extending `BindableService`)
 *  (d) gRPC streaming method (`(StreamObserver<Resp>) -> StreamObserver<Req>`)
 *  (e) non-API method (plain class, no annotations)
 *  (f) class with `class.is.grpc = true` rule-engine override but NO
 *      `BindableService` supertype and NO `@GrpcService` annotation &mdash;
 *      MUST NOT be marked (PR1's "MUST NOT consult rule engine" contract)
 *
 * Requirements: Recognizer Relocation 4.7; Decision: PR1 (rule-engine
 * exclusion contract)
 */
class ApiMethodLineMarkerProviderCharacterizationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var lineMarkerProvider: ApiMethodLineMarkerProvider

    override fun setUp() {
        super.setUp()
        // Enable all 5 frameworks so recognizers are not filtered out by isEnabled.
        // Feign (default-off) and Actuator (default-off) need explicit enablement;
        // JAX-RS (default-on), gRPC (default-on), SpringMVC (always-on) are
        // already on, so no entry is needed for them.
        SettingBinder.getInstance(project).update(GeneralSettings::class) {
            enabledFrameworks = arrayOf("Feign", "SpringActuator")
        }
        loadTestFiles()
        lineMarkerProvider = ApiMethodLineMarkerProvider()
    }

    private fun loadTestFiles() {
        // Spring MVC fixtures
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/CookieValue.java")
        loadFile("spring/SessionAttribute.java")
        loadFile("spring/AliasFor.java")
        loadFile("annotation/Public.java")
        loadFile("api/BaseController.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")

        // JAX-RS fixtures
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/CookieParam.java")
        loadFile("jaxrs/FormParam.java")
        loadFile("jaxrs/HeaderParam.java")
        loadFile("jaxrs/BeanParam.java")
        loadFile("jaxrs/DefaultValue.java")
        loadFile("jaxrs/Produces.java")
        loadFile("jaxrs/Consumes.java")
        loadFile("api/jaxrs/UserDTO.java")
        loadFile("api/jaxrs/MyGet.java")
        loadFile("api/jaxrs/MyPut.java")
        loadFile("api/jaxrs/UserResource.java")

        // gRPC fixtures
        loadFile("grpc/BindableService.java")
        loadFile("grpc/StreamObserver.java")
        loadFile("grpc/GrpcService.java")
        loadFile("grpc/EchoRequest.java")
        loadFile("grpc/EchoResponse.java")
        loadFile("grpc/EchoServiceGrpc.java")
        loadFile("grpc/EchoServiceImpl.java")
        loadFile("grpc/UserInfo.java")
        loadFile("grpc/FakeGrpcService.java")

        // Load a constant file needed by the JAX-RS fixture
        loadFile("constant/UserType.java")
    }

    /**
     * Configures a `class.is.grpc = true` rule-engine override (case (f)).
     *
     * The rule is global (returns true for any class). The line marker MUST
     * NOT consult the rule engine &mdash; so even with this rule active,
     * the fake gRPC class's methods must not be marked. This configuration
     * is load-bearing for case (f)'s contract.
     */
    override fun createConfigReader() = TestConfigReader.fromRules(
        project,
        "class.is.grpc" to "true"
    )

    // ------------------------------------------------------------------
    // Case (a): Spring MVC controller method &mdash; MUST be marked
    // ------------------------------------------------------------------

    fun testCaseASpringMvcMethodIsMarked() = runTest {
        val ctrl = findClass("com.itangcent.api.UserCtrl")!!
        // `greeting()` has @RequestMapping &mdash; a Spring MVC API annotation
        val method = findMethod(ctrl, "greeting")!!
        val marker = lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        assertNotNull(
            "Spring MVC @RequestMapping method MUST be marked (case a)",
            marker
        )
    }

    // ------------------------------------------------------------------
    // Case (b): JAX-RS resource method &mdash; MUST be marked
    // ------------------------------------------------------------------

    fun testCaseBJaxRsMethodIsMarked() = runTest {
        val resource = findClass("com.itangcent.jaxrs.UserResource")!!
        // `greeting()` has @GET + @Path &mdash; JAX-RS API annotations
        val method = findMethod(resource, "greeting")!!
        val marker = lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        assertNotNull(
            "JAX-RS @GET method MUST be marked (case b)",
            marker
        )
    }

    // ------------------------------------------------------------------
    // Case (c): gRPC unary method on BindableService extender &mdash; MUST be marked
    // ------------------------------------------------------------------

    fun testCaseCGrpcUnaryMethodIsMarked() = runTest {
        val impl = findClass("com.itangcent.grpc.service.EchoServiceImpl")!!
        // `echo(EchoRequest, StreamObserver<EchoResponse>)` &mdash; unary signature
        val method = findMethod(impl, "echo")!!
        val marker = lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        assertNotNull(
            "gRPC unary method on BindableService extender MUST be marked (case c)",
            marker
        )
    }

    // ------------------------------------------------------------------
    // Case (d): gRPC streaming method on BindableService extender &mdash; MUST be marked
    // ------------------------------------------------------------------

    fun testCaseDGrpcStreamingMethodIsMarked() = runTest {
        val impl = findClass("com.itangcent.grpc.service.EchoServiceImpl")!!
        // `echoStream(StreamObserver<EchoResponse>) -> StreamObserver<EchoRequest>` &mdash; streaming
        val method = findMethod(impl, "echoStream")!!
        val marker = lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        assertNotNull(
            "gRPC streaming method on BindableService extender MUST be marked (case d)",
            marker
        )
    }

    // ------------------------------------------------------------------
    // Case (e): non-API method (plain class) &mdash; MUST NOT be marked
    // ------------------------------------------------------------------

    fun testCaseENonApiMethodIsNotMarked() = runTest {
        val userInfo = findClass("com.itangcent.grpc.model.UserInfo")!!
        // `getId()` is a plain getter &mdash; no API annotations, no gRPC signature,
        // and UserInfo does not extend BindableService
        val method = findMethod(userInfo, "getId")!!
        val marker = lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        assertNull(
            "Non-API method on plain class MUST NOT be marked (case e)",
            marker
        )
    }

    // ------------------------------------------------------------------
    // Case (f): rule-engine gRPC override but no BindableService / @GrpcService
    //           &mdash; MUST NOT be marked (PR1's "MUST NOT consult rule engine" contract)
    // ------------------------------------------------------------------

    fun testCaseFRuleEngineOverrideNotMarked() = runTest {
        val fakeService = findClass("com.itangcent.grpc.fake.FakeGrpcService")!!
        // `echo(EchoRequest, StreamObserver<EchoResponse>)` has a gRPC unary signature,
        // but FakeGrpcService does NOT extend BindableService and has NO @GrpcService.
        // Even with `class.is.grpc = true` rule override (see createConfigReader),
        // the line marker MUST NOT mark this method &mdash; because the line marker's
        // gRPC detection is structural (BindableService/@GrpcService only), not
        // rule-driven. This is PR1's load-bearing contract.
        val echoMethod = findMethod(fakeService, "echo")!!
        val echoMarker = lineMarkerProvider.getLineMarkerInfo(echoMethod.nameIdentifier!!)
        assertNull(
            "Rule-engine gRPC override MUST NOT cause line-marker (case f, unary)",
            echoMarker
        )

        // Same check for the streaming-style method
        val echoStreamMethod = findMethod(fakeService, "echoStream")!!
        val echoStreamMarker = lineMarkerProvider.getLineMarkerInfo(echoStreamMethod.nameIdentifier!!)
        assertNull(
            "Rule-engine gRPC override MUST NOT cause line-marker (case f, streaming)",
            echoStreamMarker
        )

        // And the plain method (no gRPC signature either)
        val plainMethod = findMethod(fakeService, "plainMethod")!!
        val plainMarker = lineMarkerProvider.getLineMarkerInfo(plainMethod.nameIdentifier!!)
        assertNull(
            "Plain method on fake gRPC class MUST NOT be marked (case f, plain)",
            plainMarker
        )
    }
}
