package com.itangcent.idea.plugin

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.BaseContextTest
import com.itangcent.test.mock
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Test case for [DataEventCollector]
 *
 * @author tangcent
 */
internal class DataEventCollectorTest : BaseContextTest() {

    @Inject
    private lateinit var dataEventCollector: DataEventCollector

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        builder.mock(AnActionEvent::class) {
            Mockito.`when`(it.getData(CommonDataKeys.PROJECT))
                .thenReturn(mockProject)
            Mockito.`when`(it.getData(DataKey.create<String>("hello")))
                .thenReturn("world")
        }
    }

    @Test
    fun getData() {
        Assertions.assertEquals(mockProject, dataEventCollector.getData(CommonDataKeys.PROJECT))
        //cached
        Assertions.assertEquals(mockProject, dataEventCollector.getData(CommonDataKeys.PROJECT))

        Assertions.assertEquals("world", dataEventCollector.getData("hello"))
        //cached
        Assertions.assertEquals("world", dataEventCollector.getData("hello"))

        //create DataEventCollector by constructor
        DataEventCollector(actionContext.instance(AnActionEvent::class)).let {
            Assertions.assertEquals(mockProject, it.getData(CommonDataKeys.PROJECT))
            Assertions.assertEquals("world", it.getData("hello"))
        }
    }

    @Test
    fun getDataWithDisableDataReachBeforeAccess() {
        dataEventCollector.disableDataReach()
        Assertions.assertNull(dataEventCollector.getData(CommonDataKeys.PROJECT))
        Assertions.assertNull(dataEventCollector.getData("hello"))
    }

    @Test
    fun getDataWithDisableDataReachAfterAccess() {
        Assertions.assertEquals(mockProject, dataEventCollector.getData(CommonDataKeys.PROJECT))
        Assertions.assertEquals("world", dataEventCollector.getData("hello"))
        dataEventCollector.disableDataReach()
        //cached
        Assertions.assertEquals(mockProject, dataEventCollector.getData(CommonDataKeys.PROJECT))
        Assertions.assertEquals("world", dataEventCollector.getData("hello"))
    }
}