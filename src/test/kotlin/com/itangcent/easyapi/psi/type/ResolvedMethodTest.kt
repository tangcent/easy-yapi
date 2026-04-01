package com.itangcent.easyapi.psi.type

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * Focused tests for [ResolvedMethod.superMethod], [superMethods], and [searchAnnotation].
 */
class ResolvedMethodTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
    }

    fun testSuperMethod_WithOverride() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val sup = getItem.superMethod()
        assertNotNull(sup)
        assertEquals("AnnotatedBaseCtrl", sup?.psiMethod?.containingClass?.name)
    }

    fun testSuperMethod_NoSuper() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val list = methods.first { it.name == "list" }
        assertNull(list.superMethod())
    }

    fun testSuperMethods_Chain() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val chain = getItem.superMethods().toList()
        assertTrue("Should have at least 1 super method", chain.isNotEmpty())
        assertEquals("PlainBaseCtrl", chain.first().psiMethod.containingClass?.name)
    }

    fun testSearchAnnotation_FindsOnSelf() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val saveItem = methods.first { it.name == "saveItem" }
        val ann = saveItem.searchAnnotation("org.springframework.web.bind.annotation.PostMapping")
        assertNotNull(ann)
    }

    fun testSearchAnnotation_FindsOnSuper() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val ann = getItem.searchAnnotation("org.springframework.web.bind.annotation.GetMapping")
        assertNotNull(ann)
    }

    fun testSearchAnnotation_NotFound() = runTest {
        loadFile("api/inherit/SimpleCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.SimpleCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val list = methods.first { it.name == "list" }
        val ann = list.searchAnnotation("org.springframework.web.bind.annotation.DeleteMapping")
        assertNull(ann)
    }

    fun testSearchAnnotation_SetOverload() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }
        val springAnns = setOf(
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping"
        )
        val ann = getItem.searchAnnotation(springAnns)
        assertNotNull(ann)
    }

    fun testClassType_SearchAnnotation() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val ct = ResolvedType.ClassType(psiClass, emptyList())
        // PlainSubCtrl has no @RequestMapping, but AnnotatedBaseCtrl does
        val ann = ct.searchAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        assertNotNull("Should find @RequestMapping from AnnotatedBaseCtrl", ann)
    }

    fun testSuperMethod_GenericTypesResolvedInSuperContext() = runTest {
        // GenericBaseCtrl<T> has getItem(): Result<T>
        // StringCtrl extends GenericBaseCtrl<String> with no override
        // superMethod() on StringCtrl.getItem should return a ResolvedMethod
        // whose returnType has T resolved to String via the supertype's generic context.
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")!!
        val methods = ResolvedType.ClassType(psiClass, emptyList()).methods()
        val getItem = methods.first { it.name == "getItem" }

        // StringCtrl has no override, so psiMethod is already from GenericBaseCtrl
        // superMethod() should find it in GenericBaseCtrl with T=String resolved
        val sup = getItem.superMethod()
        assertNotNull("Should find superMethod in GenericBaseCtrl", sup)
        assertEquals("GenericBaseCtrl", sup!!.psiMethod.containingClass?.name)

        val rt = sup.returnType as ResolvedType.ClassType
        assertEquals("Result", rt.psiClass.name)
        val typeArg = rt.typeArgs.first()
        assertTrue(
            "T should be resolved to String in super context, got: $typeArg",
            typeArg is ResolvedType.UnresolvedType && typeArg.canonicalText.contains("String") ||
            typeArg is ResolvedType.ClassType && typeArg.psiClass.name == "String"
        )
    }
}
