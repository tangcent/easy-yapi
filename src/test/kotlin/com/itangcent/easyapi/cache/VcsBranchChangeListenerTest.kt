package com.itangcent.easyapi.cache

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class VcsBranchChangeListenerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var listener: VcsBranchChangeListener

    override fun setUp() {
        super.setUp()
        listener = VcsBranchChangeListener.getInstance(project)
    }

    fun testGetInstance() {
        assertNotNull(listener)
        assertSame(listener, VcsBranchChangeListener.getInstance(project))
    }

    fun testBranchWillChange() {
        listener.branchWillChange("feature/test")
    }

    fun testBranchHasChanged() {
        listener.start()
        listener.branchHasChanged("feature/test")
        runBlocking {
            delay(100)
        }
    }

    fun testStart() {
        listener.start()
    }
}
