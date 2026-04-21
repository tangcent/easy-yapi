package com.itangcent.easyapi.util.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class MavenHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testGetMavenIdReturnsNullWhenNoMavenOrGradle() {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!

        val result = MavenHelper.getMavenId(psiClass)

        assertNull("Should return null when neither Maven nor Gradle project is available", result)
    }

    fun testGetMavenIdReturnsNullGracefullyForAnyPsiClass() {
        loadFile("model/Result.java")
        val psiClass = findClass("com.itangcent.model.Result")!!

        val result = MavenHelper.getMavenId(psiClass)

        assertNull("Should return null gracefully when no build tool integration", result)
    }

    fun testMavenIdDataEquality() {
        val data1 = MavenIdData("com.example", "app", "1.0")
        val data2 = MavenIdData("com.example", "app", "1.0")
        assertEquals("Same data should be equal", data1, data2)
    }

    fun testMavenIdDataInequality() {
        val data1 = MavenIdData("com.example", "app", "1.0")
        val data2 = MavenIdData("com.other", "app", "1.0")
        assertTrue("Different groupId should not be equal", data1 != data2)
    }

    fun testMavenIdDataHashCode() {
        val data1 = MavenIdData("com.example", "app", "1.0")
        val data2 = MavenIdData("com.example", "app", "1.0")
        assertEquals("Equal objects should have same hashCode", data1.hashCode(), data2.hashCode())
    }

    fun testMavenIdDataToString() {
        val data = MavenIdData("com.example", "app", "1.0")
        val str = data.toString()
        assertTrue("toString should contain groupId", str.contains("com.example"))
        assertTrue("toString should contain artifactId", str.contains("app"))
        assertTrue("toString should contain version", str.contains("1.0"))
    }
}
