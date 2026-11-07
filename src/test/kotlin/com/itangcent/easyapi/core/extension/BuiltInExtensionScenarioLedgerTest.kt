package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.source.ExtensionConfigSource
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class BuiltInExtensionScenarioLedgerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configTextParser: ConfigTextParser

    override fun setUp() {
        super.setUp()
        ExtensionConfigRegistry.loadExtensions()
        configTextParser = ConfigTextParser.getInstance(project)
    }

    fun testRuntimeRegistryMatchesCoverageLedger() {
        val registeredCodes = ExtensionConfigRegistry.allExtensions().map { it.code }.toSet()
        val scenarioCodes = BuiltInExtensionScenarioLedger.scenarios.map { it.extensionCode }.toSet()
        val duplicatedScenarioCodes = BuiltInExtensionScenarioLedger.scenarios
            .groupBy { it.extensionCode }
            .filterValues { it.size > 1 }
            .keys

        assertEquals(
            "Expected exactly ${BuiltInExtensionScenarioLedger.controlledExtensionCount} registered extensions. " +
                    "Registered: $registeredCodes",
            BuiltInExtensionScenarioLedger.controlledExtensionCount,
            registeredCodes.size
        )
        assertEquals(
            "Every registered extension must have a traceable scenario. " +
                    "Missing scenarios: ${registeredCodes - scenarioCodes}; unexpected scenarios: ${scenarioCodes - registeredCodes}",
            registeredCodes,
            scenarioCodes
        )
        assertTrue(
            "Each extension must have one scenario descriptor. Duplicates: $duplicatedScenarioCodes",
            duplicatedScenarioCodes.isEmpty()
        )
    }

    fun testDefaultEnabledExtensionsEnterSourceWithoutSelectionOverride() = runTest {
        loadOnClassStubs()
        BuiltInExtensionScenarioLedger.resolvedScenarios()
            .filter { it.extension.defaultEnabled }
            .forEach { scenario ->
                val record = collect(scenario, null)

                assertTargetFingerprint(record)
            }
    }

    fun testDefaultDisabledExtensionsEnterSourceWhenExplicitlySelected() = runTest {
        loadOnClassStubs()
        BuiltInExtensionScenarioLedger.resolvedScenarios()
            .filterNot { it.extension.defaultEnabled }
            .forEach { scenario ->
                val record = collect(scenario, arrayOf(scenario.extension.code))

                assertTargetFingerprint(record)
                assertTrue(
                    record.failureMessage("selection"),
                    record.selection.contains(scenario.extension.code)
                )
            }
    }

    fun testIsolatedSelectionsKeepOnlyDeclaredExtensionsEnabled() = runTest {
        loadOnClassStubs()
        BuiltInExtensionScenarioLedger.resolvedScenarios().forEach { scenario ->
            val selection = BuiltInExtensionScenarioLedger.isolatedSelection(scenario)
            val record = collect(scenario, selection)
            val declaredCodes = scenario.declaredExtensionCodes
            val effectiveCodes = ExtensionConfigRegistry.selectedCodes(selection).toSet()
            val disabledDefaults = ExtensionConfigRegistry.allExtensions()
                .filter { it.defaultEnabled && it.code !in declaredCodes }
                .map { "-${it.code}" }

            assertTrue(
                record.failureMessage("isolation"),
                declaredCodes.all { it in selection }
            )
            assertTrue(
                record.failureMessage("isolation"),
                disabledDefaults.all { it in selection }
            )
            assertEquals(
                record.failureMessage("isolation"),
                declaredCodes,
                effectiveCodes
            )
            assertTargetFingerprint(record)
        }
    }

    private suspend fun collect(
        scenario: ResolvedBuiltInExtensionScenario,
        selection: Array<String>?
    ): BuiltInExtensionProbeRecord {
        val entries = ExtensionConfigSource(project, selection, configTextParser).collect().toList()
        return BuiltInExtensionProbeRecord(
            scenario = scenario,
            selection = selection?.toList().orEmpty(),
            selectionMode = if (selection == null) "no user override" else "explicit selection",
            sourceMetadata = entries.map { entry -> ExtensionSourceMetadata(entry.key, entry.sourceId) },
            entries = entries
        )
    }

    private suspend fun assertTargetFingerprint(record: BuiltInExtensionProbeRecord) {
        val expectedEntries = configTextParser
            .parse(record.scenario.extension.content, "extension")
            .toList()

        assertTrue(
            record.failureMessage("selection"),
            expectedEntries.isNotEmpty()
        )
        assertTrue(
            record.failureMessage("selection"),
            record.sourceMetadata.all { it.sourceId == "extension" }
        )
        assertTrue(
            record.failureMessage("selection"),
            expectedEntries.all { expected -> record.entries.any { it.sameSourceValue(expected) } }
        )
    }

    private suspend fun loadOnClassStubs() {
        val onClassNames = ExtensionConfigRegistry.allExtensions()
            .mapNotNull { it.onClass }
            .distinct()

        onClassNames.forEach { className ->
            val packageName = className.substringBeforeLast('.')
            val simpleName = className.substringAfterLast('.')
            loadFile(
                "${className.replace('.', '/')}.java",
                "package $packageName; public class $simpleName {}"
            )
        }
        onClassNames.forEach { waitForClass(it) }
        com.itangcent.easyapi.core.util.ide.ProjectClassAvailabilityService.getInstance(project).clearCache()
    }
}

internal data class BuiltInExtensionScenario(
    val extensionCode: String,
    val baseExtensions: Set<String> = emptySet(),
    val observableTerminalState: String
)

internal data class ResolvedBuiltInExtensionScenario(
    val descriptor: BuiltInExtensionScenario,
    val extension: ExtensionConfig,
    val conditionInput: String
) {
    val declaredExtensionCodes: Set<String>
        get() = descriptor.baseExtensions + extension.code
}

internal data class ExtensionSourceMetadata(
    val key: String,
    val sourceId: String
)

internal data class BuiltInExtensionProbeRecord(
    val scenario: ResolvedBuiltInExtensionScenario,
    val selection: List<String>,
    val selectionMode: String,
    val sourceMetadata: List<ExtensionSourceMetadata>,
    val entries: List<ConfigEntry>
) {
    fun failureMessage(phase: String): String {
        val defaultState = if (scenario.extension.defaultEnabled) "enabled" else "disabled"
        val selectedCodes = selection.ifEmpty { listOf("<none>") }
        val sources = sourceMetadata.joinToString(",") { "${it.key}->${it.sourceId}" }.ifEmpty { "<none>" }
        return "extension=${scenario.extension.code}; default=$defaultState; phase=$phase; " +
                "condition=${scenario.conditionInput}; observable=${scenario.descriptor.observableTerminalState}; " +
                "selectionMode=$selectionMode; selection=$selectedCodes; sources=$sources"
    }
}

internal object BuiltInExtensionScenarioLedger {

    const val controlledExtensionCount = 35

    val scenarios: List<BuiltInExtensionScenario> = listOf(
        BuiltInExtensionScenario("converts", observableTerminalState = "converted model scalar type"),
        BuiltInExtensionScenario("deprecated", observableTerminalState = "deprecated API or field documentation"),
        BuiltInExtensionScenario("fastjson", observableTerminalState = "JSONField-based field name"),
        BuiltInExtensionScenario("field-order-alphabetically-desc", observableTerminalState = "descending field order"),
        BuiltInExtensionScenario("field-order-alphabetically", observableTerminalState = "ascending field order"),
        BuiltInExtensionScenario(
            "field-order-child-first",
            observableTerminalState = "child fields before parent fields"
        ),
        BuiltInExtensionScenario(
            "field-order-parent-first",
            observableTerminalState = "parent fields before child fields"
        ),
        BuiltInExtensionScenario("field-utils", observableTerminalState = "filtered utility fields"),
        BuiltInExtensionScenario("field.name.camel-case", observableTerminalState = "lower camelCase field names"),
        BuiltInExtensionScenario("field.name.capitalize", observableTerminalState = "capitalized field names"),
        BuiltInExtensionScenario("field.name.kebab-case", observableTerminalState = "kebab-case field names"),
        BuiltInExtensionScenario("field.name.snake-case", observableTerminalState = "snake_case field names"),
        BuiltInExtensionScenario("field.name.uncapitalize", observableTerminalState = "uncapitalized field names"),
        BuiltInExtensionScenario("gson", observableTerminalState = "Gson field rename or exclusion"),
        BuiltInExtensionScenario("ignore", observableTerminalState = "excluded API endpoint"),
        BuiltInExtensionScenario("jackson", observableTerminalState = "Jackson field metadata"),
        BuiltInExtensionScenario(
            "jakarta-validation-strict",
            observableTerminalState = "group-matched required fields"
        ),
        BuiltInExtensionScenario("jakarta-validation", observableTerminalState = "required Jakarta validation fields"),
        BuiltInExtensionScenario("javax-validation-strict", observableTerminalState = "group-matched required fields"),
        BuiltInExtensionScenario("javax-validation", observableTerminalState = "required Javax validation fields"),
        BuiltInExtensionScenario("mybatis-plus", observableTerminalState = "annotated enum values"),
        BuiltInExtensionScenario("spring-configuration", observableTerminalState = "configuration properties prefix"),
        BuiltInExtensionScenario("spring-properties", observableTerminalState = "property-derived endpoint prefix"),
        BuiltInExtensionScenario(
            "spring-validations",
            observableTerminalState = "required fields and filtered binding result"
        ),
        BuiltInExtensionScenario("spring-webflux", observableTerminalState = "unwrapped reactive response model"),
        BuiltInExtensionScenario("spring", observableTerminalState = "unwrapped HTTP response body"),
        BuiltInExtensionScenario("springfox-openapi", observableTerminalState = "OpenAPI document metadata"),
        BuiltInExtensionScenario("swagger-openapi", observableTerminalState = "OpenAPI document metadata"),
        BuiltInExtensionScenario("swagger", observableTerminalState = "Swagger endpoint or model metadata"),
        BuiltInExtensionScenario("swagger3-openapi", observableTerminalState = "OpenAPI document metadata"),
        BuiltInExtensionScenario("swagger3", observableTerminalState = "OpenAPI endpoint or model metadata"),
        // YApi-specific extensions (easy-yapi only; easy-api has 26 shared extensions)
        BuiltInExtensionScenario("yapi", observableTerminalState = "YAPI export tags, status, and mock rules"),
        BuiltInExtensionScenario("yapi-mock", observableTerminalState = "mock data generation rules for parameters"),
        BuiltInExtensionScenario("yapi-swagger", observableTerminalState = "YAPI tags derived from Swagger annotations"),
        BuiltInExtensionScenario("yapi.project", observableTerminalState = "YAPI project grouping from comments")
    )

    fun resolvedScenarios(): List<ResolvedBuiltInExtensionScenario> {
        return scenarios.map { scenario ->
            val extension = requireNotNull(ExtensionConfigRegistry.getExtension(scenario.extensionCode)) {
                "No registered extension exists for scenario '${scenario.extensionCode}'"
            }
            ResolvedBuiltInExtensionScenario(
                descriptor = scenario,
                extension = extension,
                conditionInput = extension.onClass?.let { "on-class $it is available" } ?: "no on-class condition"
            )
        }
    }

    fun isolatedSelection(scenario: ResolvedBuiltInExtensionScenario): Array<String> {
        val declaredCodes = scenario.declaredExtensionCodes
        val selection = LinkedHashSet<String>()
        selection.addAll(declaredCodes)
        ExtensionConfigRegistry.allExtensions()
            .filter { it.defaultEnabled && it.code !in declaredCodes }
            .forEach { selection.add("-${it.code}") }
        return selection.toTypedArray()
    }
}

private fun ConfigEntry.sameSourceValue(other: ConfigEntry): Boolean {
    return key == other.key && value == other.value && sourceId == other.sourceId
}
