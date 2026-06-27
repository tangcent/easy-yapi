package com.itangcent.easyapi.ai

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class AiModelsTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testAiMessageSealedHierarchy() {
        val subclasses = AiMessage::class.sealedSubclasses.map { it.simpleName }.toSet()
        assertTrue("System", "System" in subclasses)
        assertTrue("User", "User" in subclasses)
        assertTrue("Assistant", "Assistant" in subclasses)
        assertTrue("ToolResult", "ToolResult" in subclasses)
    }

    fun testSystemMessage() {
        val msg = AiMessage.System("you are a rule agent")
        assertEquals("you are a rule agent", msg.content)
    }

    fun testUserMessage() {
        val msg = AiMessage.User("create a rule")
        assertEquals("create a rule", msg.content)
    }

    fun testAssistantMessageWithToolCalls() {
        val msg = AiMessage.Assistant(
            content = null,
            toolCalls = listOf(AiToolCall("call-1", "read_rule_file", "{\"path\":\"/x\"}"))
        )
        assertNull(msg.content)
        assertEquals(1, msg.toolCalls!!.size)
        assertEquals("read_rule_file", msg.toolCalls!![0].name)
    }

    fun testToolResultMessage() {
        val msg = AiMessage.ToolResult("call-1", "read_rule_file", "file content")
        assertEquals("call-1", msg.toolCallId)
        assertEquals("read_rule_file", msg.name)
        assertEquals("file content", msg.content)
    }

    fun testAiChatRequestStructure() {
        val req = AiChatRequest(
            messages = listOf(AiMessage.System("s"), AiMessage.User("u")),
            tools = listOf(AiToolSpec("t", "d", "{}")),
            maxTokens = 100
        )
        assertEquals(2, req.messages.size)
        assertEquals(1, req.tools.size)
        assertEquals(100, req.maxTokens)
    }

    fun testAiChatResponseStructure() {
        val resp = AiChatResponse(
            message = AiMessage.Assistant("hello", null),
            finishReason = "stop"
        )
        assertEquals("hello", (resp.message as AiMessage.Assistant).content)
        assertEquals("stop", resp.finishReason)
    }

    fun testNoProviderTypesReferenced() {
        // The DTOs should not reference any provider-specific or LangChain4j types.
        // Verify by checking the package — all types are in com.itangcent.easyapi.ai.
        val dtoClasses = listOf(
            AiChatRequest::class, AiMessage::class, AiToolSpec::class,
            AiToolCall::class, AiChatResponse::class
        )
        dtoClasses.forEach { cls ->
            assertEquals(
                "DTO ${cls.simpleName} should be in com.itangcent.easyapi.ai",
                "com.itangcent.easyapi.ai",
                cls.java.`package`.name
            )
        }
    }
}
