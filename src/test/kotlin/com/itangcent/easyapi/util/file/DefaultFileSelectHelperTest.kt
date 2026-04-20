package com.itangcent.easyapi.util.file

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class DefaultFileSelectHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var fileSelectHelper: DefaultFileSelectHelper

    override fun setUp() {
        super.setUp()
        fileSelectHelper = DefaultFileSelectHelper()
    }

    fun testHelperExists() {
        assertNotNull("DefaultFileSelectHelper should be created", fileSelectHelper)
    }

    fun testHelperImplementsInterface() {
        assertTrue(
            "DefaultFileSelectHelper should implement FileSelectHelper",
            fileSelectHelper is FileSelectHelper
        )
    }
}
