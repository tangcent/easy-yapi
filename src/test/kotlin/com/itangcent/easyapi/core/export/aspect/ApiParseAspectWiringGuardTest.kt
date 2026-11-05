package com.itangcent.easyapi.core.export.aspect

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural guard for the aspect refactor.
 *
 * The whole point of the refactor is that the lifecycle rule keys are referenced
 * from exactly one place per family — the two aspects — instead of being sprinkled
 * through the framework exporters. A behavioral test cannot prove that (an
 * exporter could re-add a hook and still produce the same endpoint), so this
 * test reads the production sources and fails on a direct reference.
 *
 * Also pins the EP wiring in `plugin.xml` and the shared framework-name symbol.
 *
 * Run with: `./gradlew test --tests "*.ApiParseAspectWiringGuardTest"`
 */
class ApiParseAspectWiringGuardTest {

    private val mainSrcRoot = File("src/main/kotlin")
    private val pluginXml = File("src/main/resources/META-INF/plugin.xml")

    /** The two implementations are the only allowed references. */
    private val allowedReferenceFiles = setOf(
        "com/itangcent/easyapi/core/export/aspect/StandardRuleEventAspect.kt",
        "com/itangcent/easyapi/framework/custom/CustomRuleEventAspect.kt"
    )

    /** The declarations themselves are excluded — this is where the keys are defined. */
    private val declarationFiles = setOf(
        "com/itangcent/easyapi/core/rule/RuleKeys.kt",
        "com/itangcent/easyapi/framework/custom/CustomRuleKeys.kt"
    )

    private val lifecycleKeyRegex = Regex(
        """\b(?:API_CLASS_PARSE_BEFORE|API_CLASS_PARSE_AFTER""" +
            """|API_METHOD_PARSE_BEFORE|API_METHOD_PARSE_AFTER""" +
            """|API_PARAM_PARSE_BEFORE|API_PARAM_PARSE_AFTER""" +
            """|EXPORT_AFTER""" +
            """|CUSTOM_CLASS_PARSE_BEFORE|CUSTOM_CLASS_PARSE_AFTER""" +
            """|CUSTOM_METHOD_PARSE_BEFORE|CUSTOM_METHOD_PARSE_AFTER""" +
            """|CUSTOM_EXPORT_AFTER)\b"""
    )

    @Test
    fun onlyLifecycleAspectsReferenceEventKeys() {
        assertTrue("Source root should exist at ${mainSrcRoot.absolutePath}", mainSrcRoot.exists())

        val offenders = mutableListOf<String>()
        mainSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = relativePath(file)
                if (relative in allowedReferenceFiles || relative in declarationFiles) return@forEach

                val stripped = stripComments(file.readText())
                if (lifecycleKeyRegex.containsMatchIn(stripped)) {
                    offenders.add(relative)
                }
            }

        assertTrue(
            "Lifecycle rule keys must only be referenced by StandardRuleEventAspect and " +
                "CustomRuleEventAspect; exporters must dispatch through ApiParseAspects. " +
                "Offenders:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    /**
     * A `//` inside a string literal must not be treated as a comment start,
     * otherwise a real key reference later on the same line becomes invisible
     * to the guard (a false negative).
     */
    @Test
    fun stripCommentsKeepsTextAfterCommentMarkerInsideStringLiteral() {
        val source = """val url = "http://example.com"; engine.evaluate(RuleKeys.EXPORT_AFTER, m)"""
        assertTrue(
            "Key after a `//` inside a string literal must survive stripping: ${stripComments(source)}",
            lifecycleKeyRegex.containsMatchIn(stripComments(source))
        )
    }

    @Test
    fun stripCommentsRemovesLineAndBlockComments() {
        val source = """
            // engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, cls)
            /* engine.evaluate(RuleKeys.API_METHOD_PARSE_AFTER, method) */
            val ok = 1
        """.trimIndent()
        assertTrue(
            "Commented-out keys must not be flagged: ${stripComments(source)}",
            !lifecycleKeyRegex.containsMatchIn(stripComments(source))
        )
    }

    /**
     * Positive control: the same matcher must still flag a known reference, so a
     * broken regex cannot make [onlyLifecycleAspectsReferenceEventKeys] vacuously
     * pass.
     */
    @Test
    fun guardRegexDetectsKnownReference() {
        assertTrue(
            "Matcher should flag a standard key reference",
            lifecycleKeyRegex.containsMatchIn("engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)")
        )
        assertTrue(
            "Matcher should flag a custom key reference",
            lifecycleKeyRegex.containsMatchIn("engine.evaluate(CustomRuleKeys.CUSTOM_EXPORT_AFTER, method)")
        )
        assertTrue(
            "Matcher should flag a bare `EXPORT_AFTER` reference",
            lifecycleKeyRegex.containsMatchIn("RuleKeys.EXPORT_AFTER")
        )
        assertTrue(
            "Matcher must not mistake a longer identifier for a key",
            !lifecycleKeyRegex.containsMatchIn("SomeOtherAPI_CLASS_PARSE_BEFOREThing")
        )
    }

    @Test
    fun pluginXmlRegistersExtensionPointAndBothImplementations() {
        assertTrue("plugin.xml should exist at ${pluginXml.absolutePath}", pluginXml.exists())
        val xml = pluginXml.readText()

        val extensionPoint = Regex(
            """<extensionPoint\s+name="apiParseAspect"\s+interface="com\.itangcent\.easyapi\.core\.export\.aspect\.ApiParseAspect\""""
        )
        assertTrue(
            "plugin.xml must declare the apiParseAspect extension point backed by ApiParseAspect",
            extensionPoint.containsMatchIn(xml)
        )

        assertTrue(
            "plugin.xml must register StandardRuleEventAspect by FQN",
            xml.contains(
                """<apiParseAspect implementation="com.itangcent.easyapi.core.export.aspect.StandardRuleEventAspect"/>"""
            )
        )
        assertTrue(
            "plugin.xml must register CustomRuleEventAspect by FQN",
            xml.contains(
                """<apiParseAspect implementation="com.itangcent.easyapi.framework.custom.CustomRuleEventAspect"/>"""
            )
        )
    }

    @Test
    fun pluginXmlRegistersDescriptionTitleDedupAspect() {
        assertTrue("plugin.xml should exist at ${pluginXml.absolutePath}", pluginXml.exists())
        assertTrue(
            "plugin.xml must register DescriptionTitleDedupAspect by FQN",
            pluginXml.readText().contains(
                """<apiParseAspect implementation="com.itangcent.easyapi.core.export.aspect.DescriptionTitleDedupAspect"/>"""
            )
        )
    }

    /**
     * The custom exporter's `frameworkName` and the custom aspect's `appliesTo`
     * must compare the same symbol, otherwise the aspect silently stops matching.
     */
    @Test
    fun customAspectAndExporterShareFrameworkNameSymbol() {
        val exporter = File(mainSrcRoot, "com/itangcent/easyapi/framework/custom/CustomClassExporter.kt")
        val aspect = File(mainSrcRoot, "com/itangcent/easyapi/framework/custom/CustomRuleEventAspect.kt")
        assertTrue("CustomClassExporter.kt should exist", exporter.exists())
        assertTrue("CustomRuleEventAspect.kt should exist", aspect.exists())

        val symbol = """CustomApiRecognizer\.FRAMEWORK_NAME"""
        assertTrue(
            "CustomClassExporter.frameworkName must use CustomApiRecognizer.FRAMEWORK_NAME",
            Regex(symbol).containsMatchIn(stripComments(exporter.readText()))
        )
        assertTrue(
            "CustomRuleEventAspect.appliesTo must use CustomApiRecognizer.FRAMEWORK_NAME",
            Regex(symbol).containsMatchIn(stripComments(aspect.readText()))
        )
    }

    // --- helpers ---

    private fun relativePath(file: File): String =
        file.path.replace('\\', '/')
            .removePrefix(mainSrcRoot.path.replace('\\', '/') + "/")

    /**
     * Removes Kotlin comments without touching string or char literals.
     *
     * A regex pass is not enough: a `//` inside a string (`"http://host"`) would
     * truncate the rest of the line, hiding a real key reference that follows it.
     * This scanner tracks single-line strings, raw (`"""`) strings, char literals
     * and nested block comments.
     */
    private fun stripComments(content: String): String {
        val out = StringBuilder(content.length)
        var i = 0
        val length = content.length
        while (i < length) {
            when {
                content.startsWith("\"\"\"", i) -> {
                    val end = content.indexOf("\"\"\"", i + 3)
                    val stop = if (end < 0) length else end + 3
                    out.append(content, i, stop)
                    i = stop
                }

                content[i] == '"' -> {
                    val stop = skipQuoted(content, i, '"')
                    out.append(content, i, stop)
                    i = stop
                }

                content[i] == '\'' -> {
                    val stop = skipQuoted(content, i, '\'')
                    out.append(content, i, stop)
                    i = stop
                }

                content.startsWith("//", i) -> {
                    var j = i + 2
                    while (j < length && content[j] != '\n') j++
                    i = j
                }

                content.startsWith("/*", i) -> {
                    var j = i + 2
                    var depth = 1
                    while (j < length && depth > 0) {
                        when {
                            content.startsWith("/*", j) -> {
                                depth++
                                j += 2
                            }

                            content.startsWith("*/", j) -> {
                                depth--
                                j += 2
                            }

                            else -> j++
                        }
                    }
                    i = j
                }

                else -> {
                    out.append(content[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Returns the index just past the closing [quote], or the line end if unterminated. */
    private fun skipQuoted(content: String, start: Int, quote: Char): Int {
        var j = start + 1
        while (j < content.length) {
            when (content[j]) {
                '\\' -> j += 2
                quote -> return j + 1
                '\n' -> return j
                else -> j++
            }
        }
        return content.length
    }
}
