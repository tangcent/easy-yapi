package com.itangcent.easyapi.property

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.feign.FeignClientRecognizer
import com.itangcent.easyapi.exporter.jaxrs.JaxRsResourceRecognizer
import com.itangcent.easyapi.exporter.springmvc.RequestMappingResolver
import com.itangcent.easyapi.exporter.springmvc.ReturnTypeUnwrapper
import com.itangcent.easyapi.exporter.springmvc.SpringControllerRecognizer
import com.itangcent.easyapi.exporter.springmvc.SpringParameterBindingResolver
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.type.GenericContext
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.TypeResolver
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.StringRuleMode
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking

class PsiAndExporterPropertyTests : EasyApiLightCodeInsightFixtureTestCase() {

    fun testFeignControllerRecognition() = runBlocking {
        addFeignStubs()
        myFixture.addFileToProject(
            "demo/FeignApi.java",
            """
            package demo;
            import org.springframework.cloud.openfeign.FeignClient;
            @FeignClient(name="x", path="/api")
            public interface FeignApi {
              String get();
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.FeignApi")!!
        val engine = RuleEngine.getInstance(project)
        val recognizer = FeignClientRecognizer(engine, true)
        assertTrue(recognizer.isFeignClient(psiClass))
    }

    fun testJaxrsControllerRecognition() = runBlocking {
        addJaxrsStubs()
        myFixture.addFileToProject(
            "demo/Jax.java",
            """
            package demo;
            import javax.ws.rs.Path;
            @Path("/a")
            public class Jax {
              public String x(){return "";}
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Jax")!!
        val engine = RuleEngine.getInstance(project)
        val recognizer = JaxRsResourceRecognizer(engine, true)
        assertTrue(recognizer.isResource(psiClass))
    }

    fun testSpringMvcHttpMethodAndPathExtraction() = runBlocking {
        addSpringMvcStubs()
        myFixture.addFileToProject(
            "demo/Ctrl.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping(path="/api")
            public class Ctrl {
              @GetMapping(path="/x")
              public String x(@RequestParam("q") String q){ return q; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "x" }
        val engine = RuleEngine.getInstance(project)
        val resolver = RequestMappingResolver(UnifiedAnnotationHelper(), engine)
        val mappings = resolver.resolve(resolvedMethod)
        assertEquals(1, mappings.size)
        assertEquals("/api/x", mappings[0].path)
        assertEquals("GET", mappings[0].method.name)
    }

    fun testSpringMvcMultiPathResolution() = runBlocking {
        addSpringMvcStubs()
        myFixture.addFileToProject(
            "demo/Ctrl2.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping(path={"/a","/b"})
            public class Ctrl2 {
              @GetMapping(path={"/c","/d"})
              public String x(){ return ""; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl2")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "x" }
        val engine = RuleEngine.getInstance(project)
        val resolver = RequestMappingResolver(UnifiedAnnotationHelper(), engine)
        val mappings = resolver.resolve(resolvedMethod)
        assertEquals(4, mappings.size)
        val paths = mappings.map { it.path }.toSet()
        assertTrue(paths.contains("/a/c"))
        assertTrue(paths.contains("/a/d"))
        assertTrue(paths.contains("/b/c"))
        assertTrue(paths.contains("/b/d"))
    }

    fun testSpringMvcParameterBindingResolution() = runBlocking {
        addSpringMvcStubs()
        addServletStubs()
        myFixture.addFileToProject(
            "demo/Ctrl3.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.*;
            import javax.servlet.http.HttpServletRequest;
            @RestController
            public class Ctrl3 {
              @PostMapping(path="/x")
              public String x(@RequestBody String body,
                              @RequestParam("q") String q,
                              @PathVariable("p") String p,
                              @RequestHeader("h") String h,
                              @CookieValue("c") String c,
                              HttpServletRequest req){ return ""; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl3")!!
        val method = psiClass.methods.first { it.name == "x" }
        val engine = RuleEngine.getInstance(project)
        val resolver = SpringParameterBindingResolver(UnifiedAnnotationHelper(), engine)
        val bindings = method.parameterList.parameters.mapNotNull { p ->
            val b = resolver.resolve(p)
            if (b != null) p.name to b else null
        }.toMap()
        assertEquals(com.itangcent.easyapi.exporter.model.ParameterBinding.Body, bindings["body"])
        assertEquals(com.itangcent.easyapi.exporter.model.ParameterBinding.Query, bindings["q"])
        assertEquals(com.itangcent.easyapi.exporter.model.ParameterBinding.Path, bindings["p"])
        assertEquals(com.itangcent.easyapi.exporter.model.ParameterBinding.Header, bindings["h"])
        assertEquals(com.itangcent.easyapi.exporter.model.ParameterBinding.Cookie, bindings["c"])
        assertEquals(
            com.itangcent.easyapi.exporter.model.ParameterBinding.Ignored,
            resolver.resolve(method.parameterList.parameters.last())
        )
    }

    fun testSpringMvcControllerRecognition() = runBlocking {
        addSpringMvcStubs()
        myFixture.addFileToProject(
            "demo/Ctrl4.java",
            """
            package demo;
            import org.springframework.stereotype.Controller;
            @Controller
            public class Ctrl4 {}
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl4")!!
        val engine = RuleEngine.getInstance(project)
        val recognizer = SpringControllerRecognizer(engine)
        assertTrue(recognizer.isController(psiClass))
    }

    fun testReactiveTypeUnwrapping() {
        addReactiveStubs()
        myFixture.addFileToProject(
            "demo/ReactorCtrl.java",
            """
            package demo;
            import reactor.core.publisher.Mono;
            import reactor.core.publisher.Flux;
            public class ReactorCtrl {
              public Mono<String> mono(){ return null; }
              public Flux<Integer> flux(){ return null; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.ReactorCtrl")!!
        val mono = psiClass.methods.first { it.name == "mono" }
        val flux = psiClass.methods.first { it.name == "flux" }
        val resolvedMono = TypeResolver.resolve(mono.returnType)
        assertTrue(resolvedMono is ResolvedType.ClassType)
        assertEquals("reactor.core.publisher.Mono", (resolvedMono as ResolvedType.ClassType).psiClass.qualifiedName)
        val resolvedArg = resolvedMono.typeArgs.firstOrNull()
        val resolvedArgName = when (resolvedArg) {
            is ResolvedType.ClassType -> resolvedArg.psiClass.qualifiedName
            is ResolvedType.UnresolvedType -> resolvedArg.canonicalText
            else -> null
        }
        assertTrue(resolvedArgName == "java.lang.String" || resolvedArgName == "String")
        val unwrappedMono = ReturnTypeUnwrapper.unwrap(mono.returnType)
        val monoName = when (unwrappedMono) {
            is ResolvedType.ClassType -> unwrappedMono.psiClass.qualifiedName
            is ResolvedType.UnresolvedType -> unwrappedMono.canonicalText
            else -> null
        }
        assertTrue(monoName == "java.lang.String" || monoName == "String")
        val unwrappedFlux = ReturnTypeUnwrapper.unwrap(flux.returnType)
        assertTrue(unwrappedFlux is ResolvedType.ArrayType)
    }

    fun testAnnotationDrivenFieldNameOverride() = runBlocking {
        addJacksonStubs()
        myFixture.addFileToProject(
            "demo/Dto.java",
            """
            package demo;
            import com.fasterxml.jackson.annotation.JsonProperty;
            public class Dto {
              @JsonProperty("x_name")
              public String name;
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Dto")!!
        val field = psiClass.allFields.first { it.name == "name" }
        project.registerServiceInstance(
            serviceInterface = com.itangcent.easyapi.config.ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "field.name" to "@com.fasterxml.jackson.annotation.JsonProperty#value")
        )
        val engine = RuleEngine.getInstance(project)
        val v = engine.evaluate(RuleKey.string("field.name", StringRuleMode.SINGLE), field)
        assertEquals("x_name", v)
    }

    fun testValidationAnnotationRequiredMarking() = runBlocking {
        addValidationStubs()
        myFixture.addFileToProject(
            "demo/V.java",
            """
            package demo;
            import javax.validation.constraints.NotNull;
            public class V {
              @NotNull
              public String a;
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.V")!!
        val field = psiClass.allFields.first { it.name == "a" }
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "field.required" to "@javax.validation.constraints.NotNull")
        )
        val engine = RuleEngine.getInstance(project)
        val required = engine.evaluate(RuleKey.boolean("field.required"), field)
        assertTrue(required)
    }

    fun testGenericTypeResolutionThroughHierarchy() {
        myFixture.addFileToProject(
            "demo/Gen.java",
            """
            package demo;
            class Base<T> { public T get(){ return null; } }
            class Sub extends Base<String> {}
            """.trimIndent()
        )
        val sub = findClass("demo.Sub")!!
        val map = TypeResolver.resolveGenericParams(sub, emptyList())
        val t = map["T"]
        val tName = when (t) {
            is ResolvedType.ClassType -> t.psiClass.qualifiedName
            is ResolvedType.UnresolvedType -> t.canonicalText
            else -> null
        }
        assertTrue(tName == "java.lang.String" || tName == "String")
        val base = findClass("demo.Base")!!
        val baseGet = base.methods.first { it.name == "get" }
        val resolvedReturn = TypeResolver.resolve(baseGet.returnType, GenericContext(map))
        val rName = when (resolvedReturn) {
            is ResolvedType.ClassType -> resolvedReturn.psiClass.qualifiedName
            is ResolvedType.UnresolvedType -> resolvedReturn.canonicalText
            else -> null
        }
        assertTrue(rName == "java.lang.String" || rName == "String")
    }

    fun testAnnotationNormalizationArrayValues() = runBlocking {
        myFixture.addFileToProject(
            "demo/Ann.java",
            """
            package demo;
            public @interface Ann { String[] value(); }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "demo/Use.java",
            """
            package demo;
            @Ann({"a","b"})
            public class Use {}
            """.trimIndent()
        )
        val use = findClass("demo.Use")!!
        val helper = UnifiedAnnotationHelper()
        val v = helper.findAttr(use, "demo.Ann", "value")
        assertTrue(v is List<*>)
        assertEquals(listOf("a", "b"), (v as List<*>).filterIsInstance<String>())
    }

    fun testRuleKeyAliasEquivalence() = runBlocking {
        addSpringMvcStubs()
        myFixture.addFileToProject(
            "demo/Ctrl5.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class Ctrl5 {
              @GetMapping(path="/x")
              public String x(@RequestParam("q") String q){ return q; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl5")!!
        val method = psiClass.methods.first { it.name == "x" }
        val param = method.parameterList.parameters.first()
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "doc.param" to "param-doc")
        )
        val engine = RuleEngine.getInstance(project)
        val v = engine.evaluate(RuleKeys.PARAM_DOC, param)
        assertEquals("param-doc", v)
    }

    fun testRuleModeAggregationCorrectness() = runBlocking {
        myFixture.addFileToProject(
            "demo/Ctrl6.java",
            """
            package demo;
            public class Ctrl6 { public String x(){ return ""; } }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl6")!!
        val method = psiClass.methods.first { it.name == "x" }
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "method.doc" to "a",
                "method.doc" to "b"
            )
        )
        val engine = RuleEngine.getInstance(project)
        val merged = engine.evaluate(RuleKey.string("method.doc", StringRuleMode.MERGE), method)
        assertEquals("a\nb", merged)
    }

    fun testRuleScriptErrorHandling() = runBlocking {
        myFixture.addFileToProject(
            "demo/Ctrl7.java",
            """
            package demo;
            public class Ctrl7 { public String x(){ return ""; } }
            """.trimIndent()
        )
        val psiClass = findClass("demo.Ctrl7")!!
        val method = psiClass.methods.first { it.name == "x" }
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.name" to "groovy:1/0")
        )
        val engine = RuleEngine.getInstance(project)
        val v = engine.evaluate(RuleKey.string("api.name", StringRuleMode.SINGLE), method)
        assertNull(v)
    }

    private fun addFeignStubs() {
        myFixture.addFileToProject(
            "org/springframework/cloud/openfeign/FeignClient.java",
            """
            package org.springframework.cloud.openfeign;
            public @interface FeignClient {
              String name() default "";
              String value() default "";
              String path() default "";
              String url() default "";
            }
            """.trimIndent()
        )
    }

    private fun addJaxrsStubs() {
        myFixture.addFileToProject(
            "javax/ws/rs/Path.java",
            """
            package javax.ws.rs;
            public @interface Path { String value(); }
            """.trimIndent()
        )
    }

    private fun addSpringMvcStubs() {
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RestController.java",
            "package org.springframework.web.bind.annotation; public @interface RestController {}"
        )
        myFixture.addFileToProject(
            "org/springframework/stereotype/Controller.java",
            "package org.springframework.stereotype; public @interface Controller {}"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestMethod.java",
            """
            package org.springframework.web.bind.annotation;
            public enum RequestMethod { GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestMapping.java",
            """
            package org.springframework.web.bind.annotation;
            public @interface RequestMapping {
              String[] path() default {};
              String[] value() default {};
              RequestMethod[] method() default {};
              String[] consumes() default {};
              String[] produces() default {};
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/GetMapping.java",
            "package org.springframework.web.bind.annotation; public @interface GetMapping { String[] path() default {}; String[] value() default {}; String[] consumes() default {}; String[] produces() default {}; }"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/PostMapping.java",
            "package org.springframework.web.bind.annotation; public @interface PostMapping { String[] path() default {}; String[] value() default {}; String[] consumes() default {}; String[] produces() default {}; }"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestBody.java",
            "package org.springframework.web.bind.annotation; public @interface RequestBody {}"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestParam.java",
            "package org.springframework.web.bind.annotation; public @interface RequestParam { String value() default \"\"; }"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/PathVariable.java",
            "package org.springframework.web.bind.annotation; public @interface PathVariable { String value() default \"\"; }"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/RequestHeader.java",
            "package org.springframework.web.bind.annotation; public @interface RequestHeader { String value() default \"\"; }"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/CookieValue.java",
            "package org.springframework.web.bind.annotation; public @interface CookieValue { String value() default \"\"; }"
        )
        myFixture.addFileToProject(
            "org/springframework/web/bind/annotation/ModelAttribute.java",
            "package org.springframework.web.bind.annotation; public @interface ModelAttribute {}"
        )
    }

    private fun addServletStubs() {
        myFixture.addFileToProject(
            "javax/servlet/http/HttpServletRequest.java",
            "package javax.servlet.http; public interface HttpServletRequest {}"
        )
    }

    private fun addReactiveStubs() {
        myFixture.addFileToProject(
            "reactor/core/publisher/Mono.java",
            "package reactor.core.publisher; public class Mono<T> {}"
        )
        myFixture.addFileToProject(
            "reactor/core/publisher/Flux.java",
            "package reactor.core.publisher; public class Flux<T> {}"
        )
        myFixture.addFileToProject(
            "org/springframework/http/ResponseEntity.java",
            "package org.springframework.http; public class ResponseEntity<T> {}"
        )
    }

    private fun addJacksonStubs() {
        myFixture.addFileToProject(
            "com/fasterxml/jackson/annotation/JsonProperty.java",
            "package com.fasterxml.jackson.annotation; public @interface JsonProperty { String value() default \"\"; }"
        )
    }

    private fun addValidationStubs() {
        myFixture.addFileToProject(
            "javax/validation/constraints/NotNull.java",
            "package javax.validation.constraints; public @interface NotNull {}"
        )
    }

    private fun PsiClass.type(): com.intellij.psi.PsiType? {
        return com.intellij.psi.util.PsiTypesUtil.getClassType(this)
    }
}
