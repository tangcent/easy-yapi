package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.settings.Settings
import org.junit.Before

abstract class IntegrationTestBase {

    protected open fun createSettings(): Settings = ApiFixtures.createSettings()

    @Before
    open fun setUp() {
    }
}
