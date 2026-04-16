package com.itangcent.easyapi.util.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ProjectClassAvailabilityServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var availabilityService: ProjectClassAvailabilityService

    override fun setUp() {
        super.setUp()
        availabilityService = ProjectClassAvailabilityService.getInstance(project)
    }

    fun testHasClassInProjectReturnsTrueForExistingClass() = runTest {
        loadFile("spring/RestController.java")
        
        val hasClass = availabilityService.hasClassInProject(
            "org.springframework.web.bind.annotation.RestController"
        )
        assertTrue("Should find RestController annotation", hasClass)
    }

    fun testHasClassInProjectReturnsFalseForNonExistentClass() = runTest {
        val hasClass = availabilityService.hasClassInProject(
            "com.nonexistent.Class"
        )
        assertFalse("Should not find non-existent class", hasClass)
    }

    fun testHasAnyClassInProjectReturnsTrueWhenAnyExists() = runTest {
        loadFile("spring/RestController.java")
        
        val hasAny = availabilityService.hasAnyClassInProject(
            setOf(
                "com.nonexistent.Class",
                "org.springframework.web.bind.annotation.RestController"
            )
        )
        assertTrue("Should find at least one class", hasAny)
    }

    fun testHasAnyClassInProjectReturnsFalseWhenNoneExists() = runTest {
        val hasAny = availabilityService.hasAnyClassInProject(
            setOf(
                "com.nonexistent.Class1",
                "com.nonexistent.Class2"
            )
        )
        assertFalse("Should not find any class", hasAny)
    }

    fun testCacheIsUsedForRepeatedCalls() = runTest {
        loadFile("spring/RestController.java")
        
        val qName = "org.springframework.web.bind.annotation.RestController"
        
        val result1 = availabilityService.hasClassInProject(qName)
        assertTrue("First call should find class", result1)
        
        val result2 = availabilityService.hasClassInProject(qName)
        assertTrue("Second call should return cached result", result2)
    }

    fun testClearCache() = runTest {
        loadFile("spring/RestController.java")
        
        val qName = "org.springframework.web.bind.annotation.RestController"
        
        val result1 = availabilityService.hasClassInProject(qName)
        assertTrue("Should find class before clear", result1)
        
        availabilityService.clearCache()
        
        val result2 = availabilityService.hasClassInProject(qName)
        assertTrue("Should still find class after cache clear", result2)
    }

    fun testMultipleFrameworks() = runTest {
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        
        val springAnnotations = setOf(
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.bind.annotation.GetMapping"
        )
        
        val hasSpring = availabilityService.hasAnyClassInProject(springAnnotations)
        assertTrue("Should find Spring annotations", hasSpring)
        
        val jaxrsAnnotations = setOf(
            "javax.ws.rs.Path",
            "javax.ws.rs.GET"
        )
        
        val hasJaxrs = availabilityService.hasAnyClassInProject(jaxrsAnnotations)
        assertFalse("Should not find JAX-RS annotations", hasJaxrs)
    }
}
