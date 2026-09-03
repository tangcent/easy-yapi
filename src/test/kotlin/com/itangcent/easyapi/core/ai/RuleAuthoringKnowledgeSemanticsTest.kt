package com.itangcent.easyapi.core.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the rule-authoring knowledge supplied to built-in and external AI agents. */
class RuleAuthoringKnowledgeSemanticsTest {

    @Test
    fun testClassIdentityExamplesUseQualifiedName() {
        val sources = buildList {
            add(File("src/main/resources/ai/agent-base.md"))
            addAll(markdownFiles(File("src/main/resources/ai/key-guides")))
            addAll(markdownFiles(File("docs/knowledge-base")))
            add(File("skills/easy-yapi-assistant/SKILL.md"))
        }
        val missing = sources.filterNot(File::isFile)
        assertTrue("Rule-authoring knowledge files must exist: $missing", missing.isEmpty())

        val violations = sources.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (CLASS_CONTEXT_SIMPLE_NAME.containsMatchIn(line)) {
                    "${source.path}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        assertTrue(
            "AI rule-authoring sources must not use class-context name(); " +
                "use qualifiedName() so FQN/package rules are unambiguous:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun testScriptReferenceDocumentsClassIdentitySemantics() {
        val reference = File("docs/knowledge-base/postman-script-reference.md").readText()
        assertTrue(
            "Script reference must document name() as a simple class name",
            reference.contains("simple class name")
        )
        assertTrue("Script reference must document qualifiedName()", reference.contains("qualifiedName()"))
        assertTrue("Script reference must document containingClass()", reference.contains("containingClass()"))
        assertTrue("Script reference must document defineClass()", reference.contains("defineClass()"))
    }

    private fun markdownFiles(directory: File): List<File> =
        directory.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()

    private companion object {
        val CLASS_CONTEXT_SIMPLE_NAME = Regex(
            """(?:containingClass|defineClass)\(\)\??\.name\(\)"""
        )
    }
}
