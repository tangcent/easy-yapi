package com.itangcent.easyapi.core.mcp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

/**
 * Guards the cross-version contract of the MCP integration across the whole plugin: only the classes of
 * `core/mcp` may reference the MCP Server API at all, and only the parts of it that kept their shape from
 * IntelliJ IDEA 2025.2.1 — the version the plugin compiles against — onwards:
 *
 * | allowed reference | why it is safe |
 * |---|---|
 * | `McpToolset` | marker interface, no members |
 * | `annotations/McpTool` | read reflectively by the server; only `name` is used |
 * | `annotations/McpDescription` | same; only `description` is used |
 *
 * Everything else changed after 2025.2.1: the `project` extension moved to another file class, and
 * `mcpFail` and the `McpExpectedError` constructor gained a parameter. A call to any of them compiles
 * against 2025.2.1 and then fails with `NoSuchMethodError` on later IDEs — no compiler error, no test
 * failure on the build IDE. A reference outside `core/mcp` is worse: the MCP Server plugin is an optional
 * dependency, so such a class breaks as soon as it loads in an IDE without that plugin.
 *
 * Source scanning cannot catch either: a top-level function or extension property is imported by name,
 * while the bytecode refers to the file class holding it (`ProjectContextElementKt`), and Kotlin moves
 * lambda bodies into synthetic classes (`EasyApiMcpToolset$scanEndpoints$2`). So the guard reads the
 * class files themselves: every symbolic reference lives in a `CONSTANT_Utf8` entry of the constant pool.
 *
 * Adding to [ALLOWED] has to be a deliberate act: first check that the new member exists, with the same
 * signature, on every IDE version the plugin supports.
 *
 * Run with: `./gradlew test --tests "*.McpApiCompatGuardTest"`
 */
class McpApiCompatGuardTest {

    @Test
    fun noClassOutsideCoreMcpReferencesTheMcpApi() {
        val offenders = pluginClasses()
            .filterKeys { !it.startsWith(MCP_PACKAGE) }
            .mapValues { (_, bytes) -> mcpReferences(bytes) }
            .filterValues { it.isNotEmpty() }
        assertTrue(
            "Only the classes of $MCP_PACKAGE may reference the MCP Server API; it is an optional dependency, " +
                "so these classes would fail to load in an IDE without it:\n" +
                offenders.entries.joinToString("\n") { "  ${it.key} -> ${it.value.sorted()}" },
            offenders.isEmpty()
        )
    }

    @Test
    fun coreMcpReferencesOnlyVersionStableMcpApi() {
        val offenders = pluginClasses()
            .filterKeys { it.startsWith(MCP_PACKAGE) }
            .mapValues { (_, bytes) -> mcpReferences(bytes) - ALLOWED }
            .filterValues { it.isNotEmpty() }
        assertTrue(
            "These classes reference MCP Server API that is not stable across IDE versions, so they would " +
                "fail with NoSuchMethodError on IDEs newer than the build IDE. Use only $ALLOWED:\n" +
                offenders.entries.joinToString("\n") { "  ${it.key} -> ${it.value.sorted()}" },
            offenders.isEmpty()
        )
    }

    /** Keeps the checks above from passing vacuously: the scan reaches the whole plugin, not one package. */
    @Test
    fun scanCoversTheWholePlugin() {
        val classes = pluginClasses()
        assertTrue(
            "the scan should cover the whole plugin (about 2,400 classes), found ${classes.size}",
            classes.size >= MIN_PLUGIN_CLASSES
        )
        val missing = ANCHORS.filterNot { it in classes }
        assertTrue("the scan should include classes outside $MCP_PACKAGE; missing $missing", missing.isEmpty())
    }

    /** Keeps the checks above from passing vacuously: the toolset does use the allowed API, and the scan sees it. */
    @Test
    fun scanSeesTheToolsetsOwnMcpReferences() {
        val toolset = pluginClasses().getValue(TOOLSET)
        val found = mcpReferences(toolset)
        assertTrue(
            "the scan should find every allowed reference in $TOOLSET; found ${found.sorted()}",
            found.containsAll(ALLOWED)
        )
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Every class file of the plugin, keyed by its path from the class root
     * (`com/itangcent/easyapi/core/mcp/EasyApiMcpToolset.class`).
     */
    private fun pluginClasses(): Map<String, ByteArray> {
        val location = EasyApiMcpToolset::class.java.getResource("EasyApiMcpToolset.class")
            ?: throw AssertionError("EasyApiMcpToolset.class not found on the test classpath")
        return when (location.protocol) {
            // Gradle runs the tests against the plugin jar in the test sandbox: the jar that ships.
            // The IDE's class loader has its own URL connection type, so `jar:<file>!/<entry>` is split by hand.
            "jar" -> {
                val jarFile = location.toString().removePrefix("jar:").substringBefore("!/")
                ZipFile(File(URI(jarFile))).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .associate { entry -> entry.name to zip.getInputStream(entry).use { it.readBytes() } }
                }
            }
            // A run from the IDE may load the classes straight from the compiler output.
            "file" -> {
                // EasyApiMcpToolset.class sits one directory per segment of TOOLSET below the class root.
                val root = (0..TOOLSET.count { it == '/' })
                    .fold(File(location.toURI())) { file, _ -> file.parentFile }
                root.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".class") }
                    .associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
            }

            else -> throw AssertionError("unexpected location of EasyApiMcpToolset.class: $location")
        }
    }

    /** Internal names under `com/intellij/mcpserver/` that the class file's constant pool refers to. */
    private fun mcpReferences(classBytes: ByteArray): Set<String> =
        utf8Constants(classBytes)
            .flatMap { constant -> MCP_NAME.findAll(constant).map { it.value } }
            .toSet()

    /** The `CONSTANT_Utf8` entries of a class file's constant pool (JVMS 4.4). */
    private fun utf8Constants(classBytes: ByteArray): List<String> {
        val input = DataInputStream(ByteArrayInputStream(classBytes))
        check(input.readInt() == 0xCAFEBABE.toInt()) { "not a class file" }
        input.readUnsignedShort() // minor_version
        input.readUnsignedShort() // major_version
        val count = input.readUnsignedShort()
        val constants = mutableListOf<String>()
        var index = 1
        while (index < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> constants += input.readUTF() // Utf8: u2 length + modified UTF-8, as DataInput reads it
                3, 4 -> input.skipBytes(4) // Integer, Float
                5, 6 -> {
                    input.skipBytes(8) // Long, Double take two slots
                    index++
                }

                7, 8, 16, 19, 20 -> input.skipBytes(2) // Class, String, MethodType, Module, Package
                9, 10, 11, 12, 17, 18 -> input.skipBytes(4) // refs, NameAndType, Dynamic, InvokeDynamic
                15 -> input.skipBytes(3) // MethodHandle
                else -> error("unknown constant pool tag $tag at index $index")
            }
            index++
        }
        return constants
    }

    private companion object {
        val ALLOWED = setOf(
            "com/intellij/mcpserver/McpToolset",
            "com/intellij/mcpserver/annotations/McpTool",
            "com/intellij/mcpserver/annotations/McpDescription",
        )

        /** An internal class name; `$` is part of it, so `McpToolset$Companion` is caught as its own name. */
        val MCP_NAME = Regex("""com/intellij/mcpserver/[\w/$]+""")

        const val MCP_PACKAGE = "com/itangcent/easyapi/core/mcp/"
        const val TOOLSET = "com/itangcent/easyapi/core/mcp/EasyApiMcpToolset.class"

        /** Far below the plugin's size, far above one package: a scan of `core/mcp` alone finds a few dozen. */
        const val MIN_PLUGIN_CLASSES = 1000

        /** Classes outside `core/mcp` that must be in the scan: the MCP code's own collaborators. */
        val ANCHORS = listOf(
            "com/itangcent/easyapi/core/export/TargetedExportService.class",
            "com/itangcent/easyapi/core/dashboard/ApiScanner.class",
        )
    }
}
