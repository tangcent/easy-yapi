package com.itangcent.easyapi.util.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ModuleHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testResolveModuleNameByPathWithSrcDirectory() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/src/main/java/com/example/App.java")
        assertEquals("Should extract module name before /src/", "my-app", result)
    }

    fun testResolveModuleNameByPathWithMainDirectory() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/main/java/com/example/App.java")
        assertEquals("Should extract module name before /main/", "my-app", result)
    }

    fun testResolveModuleNameByPathWithJavaDirectory() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/java/com/example/App.java")
        assertEquals("Should extract module name before /java/", "my-app", result)
    }

    fun testResolveModuleNameByPathWithKotlinDirectory() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/kotlin/com/example/App.kt")
        assertEquals("Should extract module name before /kotlin/", "my-app", result)
    }

    fun testResolveModuleNameByPathWithScalaDirectory() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/scala/com/example/App.scala")
        assertEquals("Should extract module name before /scala/", "my-app", result)
    }

    fun testResolveModuleNameByPathWithNoSourceMarker() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/com/example/App.java")
        assertEquals("Should return last segment when no source marker", "App.java", result)
    }

    fun testResolveModuleNameByPathWithNullInput() {
        val result = ModuleHelper.resolveModuleNameByPath(null)
        assertNull("Should return null for null input", result)
    }

    fun testResolveModuleNameByPathWithBlankInput() {
        val result = ModuleHelper.resolveModuleNameByPath("")
        assertNull("Should return null for empty input", result)
        val result2 = ModuleHelper.resolveModuleNameByPath("   ")
        assertNull("Should return null for blank input", result2)
    }

    fun testResolveModuleNameByPathWithWindowsBackslash() {
        val result = ModuleHelper.resolveModuleNameByPath("C:\\projects\\my-app\\src\\main\\java\\com\\example\\App.java")
        assertEquals("Should handle Windows backslashes", "my-app", result)
    }

    fun testResolveModuleNameByPathWithNestedModule() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/parent/child-module/src/main/java/com/example/App.java")
        assertEquals("Should extract innermost module name before /src/", "child-module", result)
    }

    fun testResolveModuleNameByPathWithTrailingSlash() {
        val result = ModuleHelper.resolveModuleNameByPath("/projects/my-app/src/")
        assertEquals("Should handle trailing slash", "my-app", result)
    }

    fun testResolveModuleNameByPathWithSimpleDirectoryName() {
        val result = ModuleHelper.resolveModuleNameByPath("/my-module")
        assertEquals("Should return the directory name itself", "my-module", result)
    }

    fun testResolveModuleReturnsModuleForPsiElement() = runBlocking {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!

        val module = ModuleHelper.resolveModule(psiClass)

        assertNotNull("Should find module for PSI element in test fixture", module)
    }

    fun testResolveModuleNameReturnsNameForPsiElement() = runBlocking {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!

        val moduleName = ModuleHelper.resolveModuleName(psiClass)

        assertNotNull("Should return module name for PSI element in test fixture", moduleName)
    }
}
