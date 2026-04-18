package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ApiDashboardToolWindowFactoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testFactoryExists() {
        val factory = ApiDashboardToolWindowFactory()
        assertNotNull("Factory should be instantiable", factory)
    }
}
