package com.itangcent.easyapi.config

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class ConfigSyncServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configSyncService: ConfigSyncService

    override fun setUp() {
        super.setUp()
        configSyncService = ConfigSyncService.getInstance(project)
    }

    override fun tearDown() {
        configSyncService.dispose()
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(configSyncService)
        assertSame(configSyncService, ConfigSyncService.getInstance(project))
    }

    fun testStart() {
        configSyncService.start()
        runBlocking {
            delay(100)
        }
    }

    fun testDispose() {
        configSyncService.dispose()

        runBlocking {
            delay(100)
        }
    }
}