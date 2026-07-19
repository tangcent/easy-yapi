package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.ai.agent.Proposal
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import java.nio.file.Files

/**
 * Tests for [WriteRuleFileTool].
 *
 * Although the tool is reserved (not registered in v1's `standardRuleTools()`),
 * its `execute` is functional and must behave correctly if a future version
 * registers it. These tests cover the four documented behaviors:
 * - Writes content to a file inside an allowed rule directory.
 * - Rejects paths outside the allowed rule directories with an error.
 * - Rejects missing/blank `path` or `content` parameters.
 * - Clears any staged proposal in [AgentMemory] after a successful write.
 */
class WriteRuleFileToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun ctx(workingMemory: AgentMemory = AgentMemory()): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = workingMemory,
        approvals = NoOpApprovalGate()
    )

    // ------------------------------------------------------------------
    // successful write
    // ------------------------------------------------------------------

    fun testWritesContentToAllowedFile() {
        val easyapiDir = projectEasyapiDir()
        try {
            val targetPath = java.io.File(easyapiDir, "written.rules").absolutePath
            val content = "api.name=Test\nfield.ignore=id"

            val result = runBlocking {
                WriteRuleFileTool().execute(
                    mapOf("path" to targetPath, "content" to content),
                    ctx()
                )
            }
            Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
            val text = (result as ToolResult.Text).value
            Assert.assertTrue(
                "should report chars written: $text",
                text.contains("Wrote ${content.length} chars")
            )
            Assert.assertTrue(
                "should mention the resolved path: $text",
                text.contains("written.rules")
            )

            val onDisk = Files.readString(java.nio.file.Paths.get(targetPath))
            Assert.assertEquals(content, onDisk)
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------
    // outside-dir rejection
    // ------------------------------------------------------------------

    fun testRejectsPathOutsideAllowedDirs() {
        // /tmp is not an allowed rule directory — resolution returns null.
        val tmp = Files.createTempFile("easyapi-outside-", ".rules").toFile()
        try {
            val result = runBlocking {
                WriteRuleFileTool().execute(
                    mapOf("path" to tmp.absolutePath, "content" to "api.name=Nope"),
                    ctx()
                )
            }
            Assert.assertTrue("expected Error result, got $result", result is ToolResult.Error)
            val msg = (result as ToolResult.Error).message
            Assert.assertTrue(
                "should mention outside allowed",
                msg.contains("outside allowed")
            )
            // The file must not have been written to.
            Assert.assertEquals(
                "file should not be modified",
                "",
                tmp.readText()
            )
        } finally {
            tmp.delete()
        }
    }

    fun testRejectsPathEscapeAttempt() {
        // A `..` escape from inside the rule dir resolves outside the
        // allowed dir and must be refused.
        val easyapiDir = projectEasyapiDir()
        try {
            val escapePath = java.io.File(easyapiDir, "../../../etc/passwd").absolutePath
            val result = runBlocking {
                WriteRuleFileTool().execute(
                    mapOf("path" to escapePath, "content" to "api.name=Evil"),
                    ctx()
                )
            }
            Assert.assertTrue(result is ToolResult.Error)
            Assert.assertTrue(
                (result as ToolResult.Error).message.contains("outside allowed")
            )
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------
    // missing / blank parameter validation
    // ------------------------------------------------------------------

    fun testRejectsMissingPath() {
        val result = runBlocking {
            WriteRuleFileTool().execute(
                mapOf("content" to "api.name=Test"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("missing required parameter")
        )
    }

    fun testRejectsMissingContent() {
        val easyapiDir = projectEasyapiDir()
        try {
            val targetPath = java.io.File(easyapiDir, "blank.rules").absolutePath
            val result = runBlocking {
                WriteRuleFileTool().execute(
                    mapOf("path" to targetPath),
                    ctx()
                )
            }
            Assert.assertTrue(result is ToolResult.Error)
            Assert.assertTrue(
                (result as ToolResult.Error).message.contains("missing required parameter")
            )
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    fun testRejectsBlankPath() {
        val result = runBlocking {
            WriteRuleFileTool().execute(
                mapOf("path" to "   ", "content" to "api.name=Test"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("missing required parameter")
        )
    }

    fun testRejectsBlankContent() {
        val easyapiDir = projectEasyapiDir()
        try {
            val targetPath = java.io.File(easyapiDir, "blank-content.rules").absolutePath
            val result = runBlocking {
                WriteRuleFileTool().execute(
                    mapOf("path" to targetPath, "content" to "   "),
                    ctx()
                )
            }
            Assert.assertTrue(result is ToolResult.Error)
            Assert.assertTrue(
                (result as ToolResult.Error).message.contains("missing required parameter")
            )
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------
    // proposal clearing
    // ------------------------------------------------------------------

    fun testSuccessfulWriteClearsStagedProposal() {
        val easyapiDir = projectEasyapiDir()
        try {
            val targetPath = java.io.File(easyapiDir, "with-proposal.rules").absolutePath
            val memory = AgentMemory().apply {
                proposal = Proposal(content = "api.name=Old", suggestedFileName = "old.rules")
            }

            val result = runBlocking {
                WriteRuleFileTool().execute(
                    mapOf("path" to targetPath, "content" to "api.name=New"),
                    ctx(workingMemory = memory)
                )
            }
            Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
            Assert.assertNull(
                "proposal should be cleared after successful write",
                memory.proposal
            )
        } finally {
            easyapiDir.deleteRecursively()
        }
    }

    fun testFailedWriteDoesNotClearProposal() {
        // An outside-dir rejection must leave the staged proposal intact so
        // the user can retry / inspect the previous draft.
        val memory = AgentMemory().apply {
            proposal = Proposal(content = "api.name=Keep", suggestedFileName = "keep.rules")
        }
        val result = runBlocking {
            WriteRuleFileTool().execute(
                mapOf("path" to "/tmp/not-allowed.rules", "content" to "api.name=New"),
                ctx(workingMemory = memory)
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertNotNull(
            "proposal should be preserved when write fails",
            memory.proposal
        )
        Assert.assertEquals("api.name=Keep", memory.proposal!!.content)
    }

    // ------------------------------------------------------------------
    // Tool metadata
    // ------------------------------------------------------------------

    fun testToolKindIsAction() {
        Assert.assertEquals(ToolKind.ACTION, WriteRuleFileTool().kind)
    }

    fun testToolRequiresApproval() {
        Assert.assertTrue(WriteRuleFileTool().requiresApproval)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun projectEasyapiDir(): java.io.File {
        val basePath = project.basePath
            ?: throw IllegalStateException("project base path required")
        return java.io.File(basePath, ".easyapi").apply { mkdirs() }
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
