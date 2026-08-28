package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.source.ExtensionConfigSource
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class BuiltInExtensionOnClassAndNegativeConditionScenarioTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var harness: BuiltInExtensionExecutionHarness

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        ExtensionConfigRegistry.loadExtensions()
        loadJDKClass("java.util.List")
        loadJDKClass("java.lang.Class")
        loadJDKClass("java.lang.ClassLoader")
        loadJDKClass("java.lang.Thread")
        loadJDKClass("java.lang.CharSequence")
        harness = BuiltInExtensionExecutionHarness(
            project = project,
            loadPsiFile = { path, content -> loadFile(path, content) },
            waitForClass = { fqn -> waitForClass(fqn) }
        )
    }

    fun testFastjsonUsesExplicitSelectionAndChangesEndpointModelsOnlyWhenJsonFieldIsAvailable() = runTest {
        val scenario = scenario("fastjson")
        val onClass = requireNotNull(scenario.extension.onClass)
        assertFalse(
            message(scenario, "selection", "default state", "Fastjson is disabled by default"),
            scenario.extension.defaultEnabled
        )

        harness.execute(
            scenario,
            springMvcPlan(
                "com/alibaba/fastjson/annotation/JSONField.java",
                "api/fastjson/FastjsonDTO.java",
                "api/fastjson/FastjsonController.java"
            )
        ) { session ->
            val condition = presentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = true, condition = condition)
            assertSourceSelection(
                scenario = scenario,
                selectedCodes = arrayOf(scenario.extension.code),
                selectionMode = "explicit selection",
                condition = condition
            )
            val services = installExtension(session, RuleKeys.FIELD_NAME.name, condition)
            val dto = requireClass("com.itangcent.fastjson.FastjsonDTO", scenario, condition)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                condition,
                "Fastjson DTO model"
            )

            assertTrue(
                message(scenario, "assert", condition, "JSONField renamed id"),
                model.fields.containsKey("product_id")
            )
            assertTrue(
                message(scenario, "assert", condition, "JSONField renamed name"),
                model.fields.containsKey("product_name")
            )
            assertFalse(message(scenario, "assert", condition, "original id field"), model.fields.containsKey("id"))

            val controller = requireClass("com.itangcent.fastjson.FastjsonController", scenario, condition)
            val endpoint = requireNotNull(services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == "/fastjson/create" && it.httpMetadata?.method == HttpMethod.POST
            }) {
                message(scenario, "export", condition, "Fastjson POST endpoint")
            }
            val requestBody = requireObject(endpoint.httpMetadata?.body, scenario, condition, "Fastjson request body")
            assertTrue(
                message(scenario, "assert", condition, "renamed Fastjson request body field"),
                requestBody.fields.containsKey("product_id")
            )
        }

        harness.execute(
            scenario,
            springMvcPlan(
                "api/fastjson/FastjsonDTO.java",
                "api/fastjson/FastjsonController.java"
            )
        ) { session ->
            val condition = absentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = false, condition = condition)
            val services = installWithoutExtension(session, RuleKeys.FIELD_NAME.name, condition)
            val dto = requireClass("com.itangcent.fastjson.FastjsonDTO", scenario, condition)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                condition,
                "Fastjson DTO model without JSONField"
            )

            assertTrue(message(scenario, "assert", condition, "unrenamed id field"), model.fields.containsKey("id"))
            assertTrue(message(scenario, "assert", condition, "unrenamed name field"), model.fields.containsKey("name"))
            assertFalse(
                message(scenario, "assert", condition, "JSONField alias"),
                model.fields.containsKey("product_id")
            )

            val controller = requireClass("com.itangcent.fastjson.FastjsonController", scenario, condition)
            val endpoint = requireNotNull(services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == "/fastjson/create" && it.httpMetadata?.method == HttpMethod.POST
            }) {
                message(scenario, "export", condition, "Fastjson POST endpoint without JSONField")
            }
            val requestBody =
                requireObject(endpoint.httpMetadata?.body, scenario, condition, "unrenamed Fastjson request body")
            assertTrue(
                message(scenario, "assert", condition, "unrenamed Fastjson request body field"),
                requestBody.fields.containsKey("id")
            )
        }
    }

    fun testGsonNegativeExposeConditionRetainsOnlyMatchingFieldsAndRequiresSerializedNameClass() = runTest {
        val scenario = scenario("gson")
        val onClass = requireNotNull(scenario.extension.onClass)
        assertTrue(
            message(scenario, "selection", "default state", "Gson is enabled by default"),
            scenario.extension.defaultEnabled
        )

        harness.execute(
            scenario,
            fixturePlan(
                "com/google/gson/annotations/Expose.java",
                "com/google/gson/annotations/SerializedName.java",
                inlineSources = mapOf(GSON_CONDITION_DTO_FQN to gsonConditionDtoSource)
            )
        ) { session ->
            val condition = presentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = true, condition = condition)
            assertSourceSelection(scenario, null, "no user override", condition)
            val services = installExtension(session, RuleKeys.FIELD_IGNORE.name, condition)
            val dto = requireClass(GSON_CONDITION_DTO_FQN, scenario, condition)
            val hiddenField = requireNotNull(dto.fields.singleOrNull { it.name == "hidden" }) {
                message(scenario, "fixture", condition, "serialize=false field")
            }
            val visibleField = requireNotNull(dto.fields.singleOrNull { it.name == "visible" }) {
                message(scenario, "fixture", condition, "serialize=true field")
            }
            val unannotatedField = requireNotNull(dto.fields.singleOrNull { it.name == "unannotated" }) {
                message(scenario, "fixture", condition, "unannotated field")
            }
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                condition,
                "Gson DTO model"
            )

            assertTrue(
                message(scenario, "rule", condition, "serialize=false negative condition"),
                services.ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, hiddenField)
            )
            assertFalse(
                message(scenario, "rule", condition, "serialize=true negative condition"),
                services.ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, visibleField)
            )
            assertFalse(
                message(scenario, "rule", condition, "unmatched negative condition"),
                services.ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, unannotatedField)
            )
            assertTrue(
                message(scenario, "assert", condition, "SerializedName field"),
                model.fields.containsKey("external_id")
            )
            assertFalse(
                message(scenario, "assert", condition, "serialize=false field"),
                model.fields.containsKey("hidden")
            )
            assertTrue(
                message(scenario, "assert", condition, "serialize=true field"),
                model.fields.containsKey("visible")
            )
            assertTrue(
                message(scenario, "assert", condition, "unmatched field"),
                model.fields.containsKey("unannotated")
            )
        }

        harness.execute(
            scenario,
            fixturePlan(
                "com/google/gson/annotations/Expose.java",
                inlineSources = mapOf(GSON_CONDITION_DTO_FQN to gsonConditionDtoSource)
            )
        ) { session ->
            val condition = absentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = false, condition = condition)
            val services = installWithoutExtension(session, RuleKeys.FIELD_IGNORE.name, condition)
            val dto = requireClass(GSON_CONDITION_DTO_FQN, scenario, condition)
            val hiddenField = requireNotNull(dto.fields.singleOrNull { it.name == "hidden" }) {
                message(scenario, "fixture", condition, "serialize=false field without Gson source")
            }
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                condition,
                "Gson DTO model without SerializedName"
            )

            assertFalse(
                message(scenario, "rule", condition, "serialize=false field without Gson source"),
                services.ruleEngine.evaluate(RuleKeys.FIELD_IGNORE, hiddenField)
            )
            assertTrue(message(scenario, "assert", condition, "original id field"), model.fields.containsKey("id"))
            assertTrue(
                message(scenario, "assert", condition, "serialize=false field without Gson source"),
                model.fields.containsKey("hidden")
            )
            assertTrue(
                message(scenario, "assert", condition, "serialize=true field without Gson source"),
                model.fields.containsKey("visible")
            )
            assertFalse(
                message(scenario, "assert", condition, "SerializedName alias"),
                model.fields.containsKey("external_id")
            )
        }
    }

    fun testFieldUtilsAppliesNegativeGroovyConditionAndExistingFieldFilters() = runTest {
        val scenario = scenario("field-utils")
        assertTrue(
            message(scenario, "selection", "default state", "field-utils is enabled by default"),
            scenario.extension.defaultEnabled
        )

        harness.execute(
            scenario,
            fixturePlan(
                "api/fieldutils/FieldUtilsDTO.java",
                inlineSources = mapOf(
                    JAVA_LANG_TRACE_BEAN_FQN to javaLangTraceBeanSource,
                    INHERITED_FIELD_DTO_FQN to inheritedFieldDtoSource
                )
            )
        ) { session ->
            val condition = "negative Groovy condition with inherited java.lang field"
            assertSourceSelection(scenario, null, "no user override", condition)
            val services = installExtension(session, RuleKeys.FIELD_IGNORE.name, condition)
            val utilityDto = requireClass("com.itangcent.fieldutils.FieldUtilsDTO", scenario, condition)
            val utilityModel = requireObject(
                services.psiClassHelper.buildObjectModel(utilityDto, JsonOption.ALL),
                scenario,
                condition,
                "field-utils DTO model"
            )

            assertTrue(
                message(scenario, "assert", condition, "private field"),
                utilityModel.fields.containsKey("normalField")
            )
            assertFalse(
                message(scenario, "assert", condition, "transient field"),
                utilityModel.fields.containsKey("transientField")
            )
            assertFalse(
                message(scenario, "assert", condition, "serialVersionUID"),
                utilityModel.fields.containsKey("serialVersionUID")
            )
            assertFalse(
                message(scenario, "assert", condition, "public field"),
                utilityModel.fields.containsKey("publicField")
            )

            val inheritedDto = requireClass(INHERITED_FIELD_DTO_FQN, scenario, condition)
            val inheritedModel = requireObject(
                services.psiClassHelper.buildObjectModel(inheritedDto, JsonOption.ALL),
                scenario,
                condition,
                "inherited java.lang field model"
            )
            assertTrue(
                message(scenario, "assert", condition, "declared child field"),
                inheritedModel.fields.containsKey("ownField")
            )
            assertFalse(
                message(scenario, "assert", condition, "inherited java.lang field"),
                inheritedModel.fields.containsKey("traceId")
            )
        }
    }

    fun testFieldUtilsIgnoresCommonClassTypedFields() = runTest {
        val scenario = scenario("field-utils")
        harness.execute(
            scenario,
            fixturePlan("api/fieldutils/FieldUtilsDTO.java")
        ) { session ->
            val condition = "common-class type list rule"
            assertSourceSelection(scenario, null, "no user override", condition)
            val services = installExtension(session, RuleKeys.FIELD_IGNORE.name, condition)
            val utilityDto = requireClass("com.itangcent.fieldutils.FieldUtilsDTO", scenario, condition)
            val utilityModel = requireObject(
                services.psiClassHelper.buildObjectModel(utilityDto, JsonOption.ALL),
                scenario,
                condition,
                "field-utils DTO model"
            )

            assertTrue(
                message(scenario, "assert", condition, "normal private field"),
                utilityModel.fields.containsKey("normalField")
            )
            assertFalse(
                message(scenario, "assert", condition, "Class-typed field"),
                utilityModel.fields.containsKey("rawTypeField")
            )
            assertFalse(
                message(scenario, "assert", condition, "ClassLoader-typed field"),
                utilityModel.fields.containsKey("classLoaderField")
            )
            assertFalse(
                message(scenario, "assert", condition, "Thread-typed field"),
                utilityModel.fields.containsKey("threadField")
            )
            assertFalse(
                message(scenario, "assert", condition, "CharSequence-typed field"),
                utilityModel.fields.containsKey("charSequenceField")
            )
        }
    }

    fun testJakartaValidationRequiresFieldsOnlyWhenJakartaConstraintsAreAvailable() = runTest {
        assertValidationOnClassScenario(
            ValidationFixture(
                extensionCode = "jakarta-validation",
                packageName = "jakarta.validation",
                dtoClass = "com.itangcent.validation.ValidatedUserDTO",
                controllerClass = "com.itangcent.validation.ValidationController",
                dtoResource = "api/validation/ValidatedUserDTO.java",
                controllerResource = "api/validation/ValidationController.java",
                postPath = "/validated/user"
            )
        )
    }

    fun testJavaxValidationRequiresFieldsOnlyWhenJavaxConstraintsAreAvailable() = runTest {
        assertValidationOnClassScenario(
            ValidationFixture(
                extensionCode = "javax-validation",
                packageName = "javax.validation",
                dtoClass = "com.itangcent.validation.javax.JavaxValidatedUserDTO",
                controllerClass = "com.itangcent.validation.javax.JavaxValidationController",
                dtoResource = "api/validation/javax/JavaxValidatedUserDTO.java",
                controllerResource = "api/validation/javax/JavaxValidationController.java",
                postPath = "/javax/validated/user"
            )
        )
    }

    fun testSpringWebfluxUnwrapsElementAndListOnlyWhenMonoIsAvailable() = runTest {
        val scenario = scenario("spring-webflux")
        val onClass = requireNotNull(scenario.extension.onClass)
        assertTrue(
            message(scenario, "selection", "default state", "spring-webflux is enabled by default"),
            scenario.extension.defaultEnabled
        )

        harness.execute(
            scenario,
            springMvcPlan(
                "model/UserInfo.java",
                "reactor/core/publisher/Mono.java",
                "reactor/core/publisher/Flux.java",
                "org/reactivestreams/Publisher.java",
                "org/reactivestreams/Subscriber.java",
                "org/reactivestreams/Subscription.java",
                "api/webflux/ReactiveController.java"
            )
        ) { session ->
            val condition = presentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = true, condition = condition)
            assertSourceSelection(scenario, null, "no user override", condition)
            val services = installExtension(session, RuleKeys.JSON_RULE_CONVERT.name, condition)
            val controller = requireClass("com.itangcent.webflux.ReactiveController", scenario, condition)
            val endpoints = services.springMvcExporter.export(controller)
            val monoEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/reactive/user/{id}" && it.httpMetadata?.method == HttpMethod.GET
            }) {
                message(scenario, "export", condition, "Mono endpoint")
            }
            val fluxEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/reactive/users" && it.httpMetadata?.method == HttpMethod.GET
            }) {
                message(scenario, "export", condition, "Flux endpoint")
            }
            val requestEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/reactive/user" && it.httpMetadata?.method == HttpMethod.POST
            }) {
                message(scenario, "export", condition, "Mono request endpoint")
            }

            val monoResponse =
                requireObject(monoEndpoint.httpMetadata?.responseBody, scenario, condition, "unwrapped Mono response")
            val fluxResponse = requireNotNull(fluxEndpoint.httpMetadata?.responseBody) {
                message(scenario, "assert", condition, "unwrapped Flux response")
            }
            val fluxItems = requireNotNull(fluxResponse.asArray()) {
                message(scenario, "assert", condition, "Flux response list")
            }
            val requestBody =
                requireObject(requestEndpoint.httpMetadata?.body, scenario, condition, "unwrapped Mono request")

            assertTrue(
                message(scenario, "assert", condition, "Mono response element"),
                monoResponse.fields.containsKey("id")
            )
            assertNotNull(message(scenario, "assert", condition, "Flux response list element"), fluxItems.item)
            assertTrue(
                message(scenario, "assert", condition, "Mono request element"),
                requestBody.fields.containsKey("id")
            )
        }

        harness.execute(
            scenario,
            springMvcPlan(
                "model/UserInfo.java",
                "reactor/core/publisher/Flux.java",
                "org/reactivestreams/Publisher.java",
                "org/reactivestreams/Subscriber.java",
                "org/reactivestreams/Subscription.java",
                "api/webflux/ReactiveController.java"
            )
        ) { session ->
            val condition = absentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = false, condition = condition)
            val services = installWithoutExtension(session, RuleKeys.JSON_RULE_CONVERT.name, condition)
            val controller = requireClass("com.itangcent.webflux.ReactiveController", scenario, condition)
            val endpoints = services.springMvcExporter.export(controller)
            val monoEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/reactive/user/{id}" && it.httpMetadata?.method == HttpMethod.GET
            }) {
                message(scenario, "export", condition, "Mono endpoint without Mono class")
            }
            val fluxEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/reactive/users" && it.httpMetadata?.method == HttpMethod.GET
            }) {
                message(scenario, "export", condition, "Flux endpoint without Mono class")
            }

            assertFalse(
                message(scenario, "assert", condition, "Mono response element without WebFlux rules"),
                containsField(monoEndpoint.httpMetadata?.responseBody, "id")
            )
            assertFalse(
                message(scenario, "assert", condition, "Flux response element without WebFlux rules"),
                containsField(fluxEndpoint.httpMetadata?.responseBody, "id")
            )
            assertFalse(
                message(scenario, "assert", condition, "Flux list without WebFlux rules"),
                fluxEndpoint.httpMetadata?.responseBody is ObjectModel.Array
            )
        }
    }

    private suspend fun assertValidationOnClassScenario(fixture: ValidationFixture) {
        val scenario = scenario(fixture.extensionCode)
        val onClass = requireNotNull(scenario.extension.onClass)
        assertTrue(
            message(scenario, "selection", "default state", "validation extension is enabled by default"),
            scenario.extension.defaultEnabled
        )
        val constraintResources = fixture.constraintResources()

        harness.execute(
            scenario,
            springMvcPlan(
                *(constraintResources + listOf(
                    fixture.dtoResource,
                    fixture.controllerResource
                )).toTypedArray()
            )
        ) { session ->
            val condition = presentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = true, condition = condition)
            assertSourceSelection(scenario, null, "no user override", condition)
            val services = installExtension(session, RuleKeys.FIELD_REQUIRED.name, condition)
            val dto = requireClass(fixture.dtoClass, scenario, condition)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                condition,
                "${fixture.extensionCode} DTO model"
            )

            listOf("id", "name", "email").forEach { fieldName ->
                assertTrue(
                    message(scenario, "assert", condition, "required $fieldName field"),
                    model.fields.getValue(fieldName).required
                )
            }
            assertFalse(
                message(scenario, "assert", condition, "unannotated address field"),
                model.fields.getValue("address").required
            )

            val controller = requireClass(fixture.controllerClass, scenario, condition)
            val getUserParameter = requireNotNull(controller.methods.singleOrNull { it.name == "getUser" }) {
                message(scenario, "fixture", condition, "getUser method")
            }.parameterList.parameters.single()
            assertTrue(
                message(scenario, "rule", condition, "required validated parameter"),
                services.ruleEngine.evaluate(RuleKeys.PARAM_REQUIRED, getUserParameter)
            )
            val postEndpoint = requireNotNull(services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == fixture.postPath && it.httpMetadata?.method == HttpMethod.POST
            }) {
                message(scenario, "export", condition, "validated POST endpoint")
            }
            val requestBody =
                requireObject(postEndpoint.httpMetadata?.body, scenario, condition, "validated request body")
            assertTrue(
                message(scenario, "assert", condition, "required request field"),
                requestBody.fields.getValue("id").required
            )
        }

        harness.execute(
            scenario,
            springMvcPlan(fixture.dtoResource, fixture.controllerResource)
        ) { session ->
            val condition = absentCondition(onClass)
            assertOnClassAvailability(session, scenario, onClass, expected = false, condition = condition)
            val services = installWithoutExtension(session, RuleKeys.FIELD_REQUIRED.name, condition)
            val dto = requireClass(fixture.dtoClass, scenario, condition)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                condition,
                "${fixture.extensionCode} DTO model without constraints"
            )
            assertFalse(
                message(scenario, "assert", condition, "required id field without validation source"),
                model.fields.getValue("id").required
            )

            val controller = requireClass(fixture.controllerClass, scenario, condition)
            val getUserParameter = requireNotNull(controller.methods.singleOrNull { it.name == "getUser" }) {
                message(scenario, "fixture", condition, "getUser method without constraints")
            }.parameterList.parameters.single()
            assertFalse(
                message(scenario, "rule", condition, "validated parameter without validation source"),
                services.ruleEngine.evaluate(RuleKeys.PARAM_REQUIRED, getUserParameter)
            )
            val postEndpoint = requireNotNull(services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == fixture.postPath && it.httpMetadata?.method == HttpMethod.POST
            }) {
                message(scenario, "export", condition, "validated POST endpoint without constraints")
            }
            val requestBody = requireObject(
                postEndpoint.httpMetadata?.body,
                scenario,
                condition,
                "request body without validation source"
            )
            assertFalse(
                message(scenario, "assert", condition, "required request field without validation source"),
                requestBody.fields.getValue("id").required
            )
        }
    }

    private suspend fun assertOnClassAvailability(
        session: BuiltInExtensionExecutionHarness.ExtensionScenarioSession,
        scenario: ResolvedBuiltInExtensionScenario,
        fqn: String,
        expected: Boolean,
        condition: String
    ) {
        val probe = session.probeFqn(fqn)
        if (expected) {
            assertTrue(message(scenario, "fixture", condition, "resolvable exact FQN ${probe.fqn}"), probe.resolvable)
        } else {
            assertFalse(message(scenario, "fixture", condition, "missing exact FQN ${probe.fqn}"), probe.resolvable)
        }
    }

    private suspend fun assertSourceSelection(
        scenario: ResolvedBuiltInExtensionScenario,
        selectedCodes: Array<String>?,
        selectionMode: String,
        condition: String
    ) {
        val parser = ConfigTextParser.getInstance(project)
        val source = ExtensionConfigSource(project, selectedCodes, parser)
        val entries = source.collect().toList()
        val expectedEntries = parser.parse(scenario.extension.content, source.sourceId).toList()

        assertTrue(
            message(scenario, "selection", condition, "$selectionMode expected extension entries"),
            expectedEntries.isNotEmpty()
        )
        assertTrue(
            message(scenario, "selection", condition, "$selectionMode extension source entries"),
            expectedEntries.all { expected ->
                entries.any { entry ->
                    entry.key == expected.key && entry.value == expected.value && entry.sourceId == source.sourceId
                }
            }
        )
    }

    private suspend fun installExtension(
        session: BuiltInExtensionExecutionHarness.ExtensionScenarioSession,
        ruleKey: String,
        condition: String
    ): ExtensionExecutionServices {
        val reader = session.installIsolatedReader()
        val scenario = session.scenario
        val matchingRuleKeys = reader.extensionRuleKeys.filter { it.matchesRuleKey(ruleKey) }
        assertTrue(
            message(scenario, "reload", condition, "isolated extension rules"),
            reader.extensionRuleKeys.isNotEmpty()
        )
        assertTrue(message(scenario, "reload", condition, "target rule $ruleKey"), matchingRuleKeys.isNotEmpty())
        assertTrue(
            message(scenario, "reload", condition, "target rule source"),
            matchingRuleKeys.any { key -> reader.sourcesFor(key).any { it.sourceId == "extension" } }
        )
        return session.reacquireServices()
    }

    private suspend fun installWithoutExtension(
        session: BuiltInExtensionExecutionHarness.ExtensionScenarioSession,
        ruleKey: String,
        condition: String
    ): ExtensionExecutionServices {
        val reader = session.installIsolatedReader()
        val scenario = session.scenario
        assertTrue(
            message(scenario, "reload", condition, "filtered extension rules"),
            reader.extensionRuleKeys.isEmpty()
        )
        assertTrue(
            message(scenario, "reload", condition, "filtered target rule $ruleKey"),
            reader.extensionRuleKeys.none { it.matchesRuleKey(ruleKey) }
        )
        return session.reacquireServices()
    }

    private fun String.matchesRuleKey(ruleKey: String): Boolean {
        return this == ruleKey || startsWith("$ruleKey[")
    }

    private fun scenario(code: String): ResolvedBuiltInExtensionScenario {
        return requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == code
        }) {
            "Missing extension scenario: $code"
        }
    }

    private fun springMvcPlan(
        vararg resources: String,
        inlineSources: Map<String, String> = emptyMap()
    ): ExtensionFixturePlan {
        return fixturePlan(*springMvcResources, *resources, inlineSources = inlineSources)
    }

    private fun fixturePlan(
        vararg resources: String,
        inlineSources: Map<String, String> = emptyMap()
    ): ExtensionFixturePlan {
        val stubs = linkedMapOf<String, String>()
        resources.forEach { resource ->
            val content = resourceText(resource)
            stubs[qualifiedName(content, resource)] = content
        }
        stubs.putAll(inlineSources)
        return ExtensionFixturePlan(psiStubs = stubs)
    }

    private fun requireClass(
        qualifiedName: String,
        scenario: ResolvedBuiltInExtensionScenario,
        condition: String
    ) = requireNotNull(findClass(qualifiedName)) {
        message(scenario, "fixture", condition, "resolvable class $qualifiedName")
    }

    private fun requireObject(
        model: ObjectModel?,
        scenario: ResolvedBuiltInExtensionScenario,
        condition: String,
        observable: String
    ): ObjectModel.Object {
        return requireNotNull(model?.asObject()) {
            message(scenario, "assert", condition, observable)
        }
    }

    private fun containsField(model: ObjectModel?, fieldName: String): Boolean {
        return when (model) {
            is ObjectModel.Object -> model.fields.containsKey(fieldName)
            is ObjectModel.Array -> containsField(model.item, fieldName)
            else -> false
        }
    }

    private fun resourceText(resource: String): String {
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
            "Missing test fixture resource: $resource"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun qualifiedName(content: String, resource: String): String {
        val packageName = requireNotNull(packagePattern.find(content)?.groupValues?.get(1)) {
            "Fixture does not declare a package: $resource"
        }
        val typeName = requireNotNull(typePattern.find(content)?.groupValues?.get(1)) {
            "Fixture does not declare a top-level type: $resource"
        }
        return "$packageName.$typeName"
    }

    private fun presentCondition(fqn: String): String = "on-class $fqn is present"

    private fun absentCondition(fqn: String): String = "on-class $fqn is absent"

    private fun message(
        scenario: ResolvedBuiltInExtensionScenario,
        phase: String,
        condition: String,
        observable: String
    ): String {
        val defaultState = if (scenario.extension.defaultEnabled) "enabled" else "disabled"
        return "extension=${scenario.extension.code}; default=$defaultState; phase=$phase; " +
                "condition=$condition; observable=$observable"
    }

    private data class ValidationFixture(
        val extensionCode: String,
        val packageName: String,
        val dtoClass: String,
        val controllerClass: String,
        val dtoResource: String,
        val controllerResource: String,
        val postPath: String
    ) {
        fun constraintResources(): List<String> {
            val resourceRoot = packageName.replace('.', '/') + "/constraints"
            return listOf("NotNull.java", "NotBlank.java", "NotEmpty.java")
                .map { "$resourceRoot/$it" }
        }
    }

    private companion object {
        const val GSON_CONDITION_DTO_FQN = "com.itangcent.gson.GsonConditionDTO"
        const val JAVA_LANG_TRACE_BEAN_FQN = "java.lang.fixture.TraceBean"
        const val INHERITED_FIELD_DTO_FQN = "com.itangcent.fieldutils.InheritedFieldDTO"

        val packagePattern = Regex("""package\s+([A-Za-z_][\w.]*)\s*;""")
        val typePattern =
            Regex("""(?m)^\s*(?:(?:public|protected|private|abstract|final|static)\s+)*(?:class|interface|enum|@interface)\s+([A-Za-z_]\w*)""")
        val springMvcResources = arrayOf(
            "spring/RequestMapping.java",
            "spring/GetMapping.java",
            "spring/PostMapping.java",
            "spring/PutMapping.java",
            "spring/RequestParam.java",
            "spring/PathVariable.java",
            "spring/RequestBody.java",
            "spring/RestController.java",
            "spring/Controller.java"
        )

        val gsonConditionDtoSource = """
            package com.itangcent.gson;

            import com.google.gson.annotations.Expose;
            import com.google.gson.annotations.SerializedName;

            public class GsonConditionDTO {
                @SerializedName("external_id")
                private Long id;

                @Expose(serialize = false)
                private String hidden;

                @Expose(serialize = true)
                private String visible;

                private String unannotated;
            }
        """.trimIndent()

        val javaLangTraceBeanSource = """
            package java.lang.fixture;

            public class TraceBean {
                protected String traceId;
            }
        """.trimIndent()

        val inheritedFieldDtoSource = """
            package com.itangcent.fieldutils;

            import java.lang.fixture.TraceBean;

            public class InheritedFieldDTO extends TraceBean {
                private String ownField;
            }
        """.trimIndent()
    }
}
