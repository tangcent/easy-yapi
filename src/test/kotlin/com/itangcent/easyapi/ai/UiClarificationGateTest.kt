package com.itangcent.easyapi.ai

import com.itangcent.easyapi.ai.agent.AgentEvent
import com.itangcent.easyapi.ai.agent.Clarification
import com.itangcent.easyapi.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.ai.agent.ClarificationQuestion
import com.itangcent.easyapi.ai.agent.QuestionKind
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Tests for [UiClarificationGate].
 */
class UiClarificationGateTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testAwaitEmitsEventAndCompleteResumes() = runBlocking {
        val events = MutableSharedFlow<AgentEvent>(replay = 16, extraBufferCapacity = 16)
        val collected = mutableListOf<AgentEvent>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.collect { collected.add(it) }
        }
        val gate = UiClarificationGate(events)
        val clar = Clarification(
            prompt = null,
            questions = listOf(ClarificationQuestion("q1", "Q?", QuestionKind.FREE_TEXT))
        )

        val deferred = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            gate.await(clar)
        }

        assertTrue("gate should be pending after await", gate.isPending())
        assertTrue(
            "await should emit ClarificationRequested",
            collected.any { it is AgentEvent.ClarificationRequested }
        )

        gate.complete(ClarificationAnswers(mapOf("q1" to listOf("hello"))))
        val result = deferred.await()
        assertEquals(listOf("hello"), result.answers["q1"])
        assertFalse("gate should no longer be pending", gate.isPending())

        // Second complete is a no-op (must not throw).
        gate.complete(ClarificationAnswers(emptyMap()))

        collector.cancel()
    }

    fun testCompleteRawFilesUnderRawKey() = runBlocking {
        val events = MutableSharedFlow<AgentEvent>(replay = 16, extraBufferCapacity = 16)
        val gate = UiClarificationGate(events)
        val clar = Clarification(null, listOf(ClarificationQuestion("q1", "Q?", QuestionKind.FREE_TEXT)))

        val deferred = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            gate.await(clar)
        }
        gate.completeRaw("just globally")
        val result = deferred.await()
        assertEquals(listOf("just globally"), result.answers[ClarificationAnswers.RAW_KEY])
    }
}
