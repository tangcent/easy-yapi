package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.postman.model.PostmanEvent
import com.itangcent.easyapi.exporter.postman.model.PostmanScript
import org.junit.Assert.*
import org.junit.Test

class PostmanScriptMergerTest {

    @Test
    fun testMergeEmptyEvents() {
        val events = emptyList<PostmanEvent>()
        val merged = PostmanScriptMerger.merge(events)

        assertNotNull(merged)
        assertTrue("Merged events should be empty", merged.isEmpty())
    }

    @Test
    fun testMergeSingleEvent() {
        val events = listOf(
            PostmanEvent(
                listen = "prerequest",
                script = PostmanScript(exec = listOf("console.log('test');"))
            )
        )
        val merged = PostmanScriptMerger.merge(events)

        assertNotNull(merged)
        assertEquals("Should have 1 event", 1, merged.size)
    }

    @Test
    fun testMergeMultipleEvents() {
        val events = listOf(
            PostmanEvent(
                listen = "prerequest",
                script = PostmanScript(exec = listOf("console.log('pre1');"))
            ),
            PostmanEvent(
                listen = "prerequest",
                script = PostmanScript(exec = listOf("console.log('pre2');"))
            ),
            PostmanEvent(
                listen = "test",
                script = PostmanScript(exec = listOf("console.log('test1');"))
            )
        )
        val merged = PostmanScriptMerger.merge(events)

        assertNotNull(merged)
        assertTrue("Should have merged events", merged.isNotEmpty())
    }

    @Test
    fun testMergeWithDifferentTypes() {
        val events = listOf(
            PostmanEvent(
                listen = "prerequest",
                script = PostmanScript(exec = listOf("pm.environment.set('token', 'abc');"))
            ),
            PostmanEvent(
                listen = "test",
                script = PostmanScript(exec = listOf("pm.test('Status is 200', function() {", "    pm.response.to.have.status(200);", "});"))
            )
        )
        val merged = PostmanScriptMerger.merge(events)

        assertNotNull(merged)
        assertEquals("Should have 2 events", 2, merged.size)
    }
}
