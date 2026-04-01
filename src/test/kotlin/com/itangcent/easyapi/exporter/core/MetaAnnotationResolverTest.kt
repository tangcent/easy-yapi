package com.itangcent.easyapi.exporter.core

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class MetaAnnotationResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        MetaAnnotationResolver.clearCache()
    }

    private fun loadTestFiles() {
        loadFile("spring/Controller.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/RestController.java")
        loadFile("annotation/MyController.java")
        loadFile("annotation/Public.java")
        loadFile("api/UserCtrl.java")
        loadFile("model/Result.java")
    }

    fun testDirectAnnotationMatch() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val hasRestController = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf("org.springframework.web.bind.annotation.RestController")
        )
        assertTrue(
            "Should find direct @RestController annotation on UserCtrl",
            hasRestController
        )
    }

    fun testMetaAnnotationOneLevel() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val hasController = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf("org.springframework.stereotype.Controller")
        )
        assertTrue(
            "Should find @Controller via meta-annotation (@RestController -> @Controller)",
            hasController
        )
    }

    fun testFindMetaAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val found = MetaAnnotationResolver.findMetaAnnotation(
            psiClass!!,
            setOf("org.springframework.stereotype.Controller")
        )
        assertEquals(
            "Should find @Controller as the matching meta-annotation",
            "org.springframework.stereotype.Controller",
            found
        )
    }

    fun testNoMatchForNonAnnotatedClass() = runTest {
        val psiClass = findClass("com.itangcent.model.Result")
        assertNotNull("Result class should exist", psiClass)

        val hasController = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf("org.springframework.stereotype.Controller")
        )
        assertFalse(
            "Should not find @Controller on non-annotated class",
            hasController
        )
    }

    fun testMultipleTargetAnnotations() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val hasAny = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf(
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RestController",
                "javax.ws.rs.Path"
            )
        )
        assertTrue(
            "Should find at least one of the target annotations",
            hasAny
        )
    }

    fun testCacheIsUsed() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        MetaAnnotationResolver.clearCache()

        val result1 = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf("org.springframework.stereotype.Controller")
        )
        assertTrue("First call should find meta-annotation", result1)

        val result2 = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass,
            setOf("org.springframework.stereotype.Controller")
        )
        assertTrue("Second call should also find meta-annotation (from cache)", result2)
    }

    fun testClearCache() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val result1 = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf("org.springframework.stereotype.Controller")
        )
        assertTrue("Should find meta-annotation before clear", result1)

        MetaAnnotationResolver.clearCache()

        val result2 = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass,
            setOf("org.springframework.stereotype.Controller")
        )
        assertTrue("Should find meta-annotation after cache clear", result2)
    }

    fun testFindMetaAnnotationReturnsFirstMatch() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val found = MetaAnnotationResolver.findMetaAnnotation(
            psiClass!!,
            setOf(
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.stereotype.Controller"
            )
        )
        assertEquals(
            "Should return direct annotation match first",
            "org.springframework.web.bind.annotation.RestController",
            found
        )
    }

    fun testNonExistentAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl class should exist", psiClass)

        val hasAnnotation = MetaAnnotationResolver.hasMetaAnnotation(
            psiClass!!,
            setOf("com.nonexistent.FakeAnnotation")
        )
        assertFalse(
            "Should not find non-existent annotation",
            hasAnnotation
        )
    }
}
