package com.itangcent.easyapi.framework.springmvc

import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

/**
 * Covers [ReturnTypeUnwrapper.unwrap] — the unwrapping used on the Spring MVC export path.
 *
 * `Flux` is the case worth pinning: it must unwrap to an *array* of its element type, which is
 * the semantics the removed `unwrapPsiType` variant disagreed with.
 */
class ReturnTypeUnwrapperTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testUnwrapNullType() {
        val result = ReturnTypeUnwrapper.unwrap(null)
        assertTrue("Null input should yield an unresolved type", result is ResolvedType.UnresolvedType)
        assertEquals("null", result.qualifiedName())
    }

    fun testUnwrapPlainClassKeepsItUnchanged() = runBlocking {
        loadFile(
            "springmvc/PlainDto.java", """
            package com.test.springmvc;
            public class PlainDto {
                private String name;
            }
            """.trimIndent()
        )
        val psiClass = findClass("com.test.springmvc.PlainDto")!!
        val classType = PsiTypesUtil.getClassType(psiClass)

        val result = ReturnTypeUnwrapper.unwrap(classType)

        assertTrue("A non-wrapper type should stay a ClassType", result is ResolvedType.ClassType)
        assertEquals(
            "com.test.springmvc.PlainDto",
            (result as ResolvedType.ClassType).psiClass.qualifiedName
        )
    }

    fun testUnwrapResponseEntityYieldsInnerType() = runBlocking {
        addWrapperStubs()
        loadFile(
            "springmvc/WrappedCtrl.java", """
            package com.test.springmvc;
            import org.springframework.http.ResponseEntity;
            public class WrappedCtrl {
                public ResponseEntity<String> wrapped() { return null; }
            }
            """.trimIndent()
        )
        val method = findClass("com.test.springmvc.WrappedCtrl")!!.methods.first { it.name == "wrapped" }

        val result = ReturnTypeUnwrapper.unwrap(method.returnType)

        // The light fixture resolves `String` to either a class or a bare simple name.
        assertTrue(
            "ResponseEntity<T> should unwrap to T, was $result",
            simpleOrQualifiedNameOf(result) in setOf("java.lang.String", "String")
        )
    }

    fun testUnwrapFluxYieldsArrayOfElementType() = runBlocking {
        addWrapperStubs()
        loadFile(
            "springmvc/ReactorCtrl.java", """
            package com.test.springmvc;
            import reactor.core.publisher.Flux;
            public class ReactorCtrl {
                public Flux<Integer> stream() { return null; }
            }
            """.trimIndent()
        )
        val method = findClass("com.test.springmvc.ReactorCtrl")!!.methods.first { it.name == "stream" }

        val result = ReturnTypeUnwrapper.unwrap(method.returnType)

        assertTrue("Flux<T> should unwrap to an array", result is ResolvedType.ArrayType)
        val component = (result as ResolvedType.ArrayType).componentType
        assertTrue(
            "Flux element type should be Integer, was $component",
            component.qualifiedName() in setOf("int", "Integer", "java.lang.Integer")
        )
    }

    /** Element type of a wrapper, tolerating light-PSI returning either a simple or FQ name. */
    private fun simpleOrQualifiedNameOf(type: ResolvedType): String = when (type) {
        is ResolvedType.ClassType -> type.psiClass.qualifiedName ?: type.psiClass.name ?: "<anonymous>"
        is ResolvedType.UnresolvedType -> type.canonicalText
        else -> type.qualifiedName()
    }

    private fun addWrapperStubs() {
        loadFile(
            "reactor/core/publisher/Flux.java",
            "package reactor.core.publisher; public class Flux<T> {}"
        )
        loadFile(
            "org/springframework/http/ResponseEntity.java",
            "package org.springframework.http; public class ResponseEntity<T> {}"
        )
    }
}
