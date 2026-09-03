package com.itangcent.easyapi.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard test that every fixed literal key read via `configReader.getFirst("<name>")`
 * in the `src/main` source tree is declared either:
 * 1. In [ImplicitConfigKeys.all] (for keys not declared as a constant in [RuleKeys])
 * 2. As a [RuleKey] constant in [RuleKeys] (or channel/framework-specific *RuleKeys)
 *
 * Failures here indicate that a new fixed config key was added but not registered
 * in the catalog — this keeps AI tooling and the external skill catalog in sync.
 * Dynamic key names (open prefixes like `mock[...]`) are excluded by construction.
 */
class ImplicitKeyCompletenessTest {

    @Test
    fun allFixedLiteralsInGetFirstAreRegistered() {
        val projectRoot = File(".").absoluteFile
        val srcMainDir = projectRoot.resolve("src/main/kotlin")

        // Collect every `getFirst("<literal>")` call where the argument is a literal string.
        // Skip comments (the regex will still catch them in comments — we handle via the known list).
        val seenLiterals = HashSet<String>()

        srcMainDir.walk()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { lineNum, line ->
                    findLiteralGetFirstCalls(line, seenLiterals)
                }
            }

        // Collect all registered names (primary + aliases) from all sources.
        val allRegistered = HashSet<String>()

        // 1. RuleKeys (general)
        RuleKey.collectFrom(RuleKeys).forEach { key ->
            allRegistered.addAll(key.allNames)
        }
        // 2. Channel-specific keys
        listOf(
            com.itangcent.easyapi.channel.postman.PostmanRuleKeys,
            com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys,
            com.itangcent.easyapi.channel.openapi.OpenApiRuleKeys
        ).forEach { keysObj ->
            RuleKey.collectFrom(keysObj).forEach { key ->
                allRegistered.addAll(key.allNames)
            }
        }
        // 3. Framework-specific keys
        RuleKey.collectFrom(com.itangcent.easyapi.framework.custom.CustomRuleKeys).forEach { key ->
            allRegistered.addAll(key.allNames)
        }
        // 4. Implicit config keys
        ImplicitConfigKeys.all.forEach { key ->
            allRegistered.addAll(key.allNames)
        }

        // Known false positives: these are examples in doc/comments, not actual calls.
        val knownFalsePositives = setOf(
            "server", "…", "fixed.name", "<literal>"
        )

        val unregistered = seenLiterals.filterNot { it in allRegistered || it in knownFalsePositives }
        if (unregistered.isNotEmpty()) {
            println("Found unregistered fixed literal keys in getFirst:")
            unregistered.forEach { println("  - '$it'") }
            println("Please register these in either:")
            println("  1. ImplicitConfigKeys.all (if they are not a RuleKey constant)")
            println("  2. The appropriate *RuleKeys object (if they are a RuleKey constant)")
        }

        assertEquals("Found unregistered fixed literal keys in getFirst calls", emptyList<String>(), unregistered)

        // Sanity: we found at least 7 real literals (5 implicit + 2 RuleKeys).
        val realLiterals = seenLiterals.filterNot { it in knownFalsePositives }
        assertTrue("Expected at least 7 real literals, found ${realLiterals.size}", realLiterals.size >= 7)
    }

    /**
     * Extract all `"..."` literals inside `getFirst(...)` calls.
     * Simple heuristic that works for the coding conventions used in this project.
     */
    private fun findLiteralGetFirstCalls(line: String, out: MutableSet<String>) {
        // Match: getFirst("some.key")
        val regex = Regex("getFirst\\s*\\(\\s*\"([^\"]+)\"\\s*[),]")
        regex.findAll(line).forEach { match ->
            val literal = match.groups[1]?.value ?: return@forEach
            if (literal.isNotBlank()) {
                out.add(literal)
            }
        }
    }
}
