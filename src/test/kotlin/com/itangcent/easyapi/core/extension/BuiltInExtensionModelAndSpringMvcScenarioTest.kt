package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class BuiltInExtensionModelAndSpringMvcScenarioTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var harness: BuiltInExtensionExecutionHarness

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        ExtensionConfigRegistry.loadExtensions()
        loadJDKClass("java.util.Date")
        loadJDKClass("java.math.BigInteger")
        harness = BuiltInExtensionExecutionHarness(
            project = project,
            loadPsiFile = { path, content -> loadFile(path, content) },
            waitForClass = { fqn -> waitForClass(fqn) }
        )
    }

    fun testConvertsScenarioProducesConvertedModelsAndEndpoint() = runTest {
        val scenario = scenario("converts")
        harness.execute(
            scenario,
            springMvcPlan(
                "api/converts/ConvertDTO.java",
                "api/converts/ConvertController.java"
            )
        ) { session ->
            val services = session.installServices("converted scalar fields and POST endpoint")
            val dto = requireClass("com.itangcent.converts.ConvertDTO", scenario)
            val dateField = requireNotNull(dto.fields.singleOrNull { it.name == "createTime" })
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "converted DTO model"
            )

            assertEquals(
                scenarioMessage(scenario, "rule", "Date conversion"),
                "java.lang.String",
                services.ruleEngine.evaluate(RuleKeys.JSON_RULE_CONVERT, dateField.type, dateField)
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "Date field model type"),
                "string",
                model.fields.getValue("createTime").model.asSingle()?.type
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "BigInteger field model type"),
                "long",
                model.fields.getValue("id").model.asSingle()?.type
            )

            val controller = requireClass("com.itangcent.converts.ConvertController", scenario)
            val endpoint = services.springMvcExporter.export(controller).singleOrNull()
            assertNotNull(scenarioMessage(scenario, "export", "POST endpoint"), endpoint)
            assertEquals(
                scenarioMessage(scenario, "assert", "endpoint path"),
                "/converts/create",
                endpoint?.httpMetadata?.path
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "endpoint HTTP method"),
                HttpMethod.POST,
                endpoint?.httpMetadata?.method
            )
        }
    }

    fun testDeprecatedScenarioProducesDeprecatedDocumentation() = runTest {
        val scenario = scenario("deprecated")
        harness.execute(
            scenario,
            springMvcPlan(
                "api/deprecated/DeprecatedDTO.java",
                "api/deprecated/DeprecatedController.java"
            )
        ) { session ->
            val services = session.installServices("deprecated field and endpoint documentation")
            val dto = requireClass("com.itangcent.deprecated.DeprecatedDTO", scenario)
            val deprecatedField = requireNotNull(dto.fields.singleOrNull { it.name == "oldField" })
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "deprecated DTO model"
            )

            val fieldDocumentation = services.ruleEngine.evaluate(RuleKeys.FIELD_DOC, deprecatedField)
            assertTrue(
                scenarioMessage(scenario, "rule", "deprecated field documentation"),
                fieldDocumentation?.contains("已废弃") == true
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "deprecated field model documentation"),
                model.fields.getValue("oldField").comment?.contains("已废弃") == true
            )

            val controller = requireClass("com.itangcent.deprecated.DeprecatedController", scenario)
            val endpoint = services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == "/deprecated/old"
            }
            assertNotNull(scenarioMessage(scenario, "export", "deprecated endpoint"), endpoint)
            assertTrue(
                scenarioMessage(scenario, "assert", "deprecated endpoint documentation"),
                endpoint?.description?.contains("已废弃") == true
            )
        }
    }

    fun testIgnoreScenarioExcludesIgnoredClassAndMethod() = runTest {
        val scenario = scenario("ignore")
        harness.execute(
            scenario,
            springMvcPlan(
                "model/UserInfo.java",
                "api/ignore/IgnoredController.java",
                "api/ignore/NormalController.java"
            )
        ) { session ->
            val services = session.installServices("ignored class and method endpoint exclusion")
            val normalController = requireClass("com.itangcent.ignore.NormalController", scenario)
            val ignoredMethod = requireNotNull(normalController.methods.singleOrNull { it.name == "ignoredMethod" })
            val userInfo = requireClass("com.itangcent.model.UserInfo", scenario)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(userInfo, JsonOption.ALL),
                scenario,
                "unaffected comparison model"
            )

            assertTrue(
                scenarioMessage(scenario, "rule", "ignored method"),
                services.ruleEngine.evaluate(RuleKeys.IGNORE, ignoredMethod)
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "unaffected comparison model fields"),
                model.fields.isNotEmpty()
            )

            val ignoredController = requireClass("com.itangcent.ignore.IgnoredController", scenario)
            assertTrue(
                scenarioMessage(scenario, "assert", "ignored controller endpoints"),
                services.springMvcExporter.export(ignoredController).isEmpty()
            )
            val normalEndpoints = services.springMvcExporter.export(normalController)
            assertNotNull(
                scenarioMessage(scenario, "assert", "normal method endpoint"),
                normalEndpoints.singleOrNull { it.httpMetadata?.path == "/normal/method" }
            )
            assertNull(
                scenarioMessage(scenario, "assert", "ignored method endpoint"),
                normalEndpoints.singleOrNull { it.httpMetadata?.path == "/normal/ignored-method" }
            )
        }
    }

    fun testJacksonScenarioRenamesOrdersAndIgnoresFields() = runTest {
        val scenario = scenario("jackson")
        harness.execute(
            scenario,
            springMvcPlan(
                "com/fasterxml/jackson/annotation/JsonProperty.java",
                "com/fasterxml/jackson/annotation/JsonIgnore.java",
                "com/fasterxml/jackson/annotation/JsonFormat.java",
                "api/jackson/UserDTO.java",
                "api/jackson/UserController.java",
                "com/fasterxml/jackson/annotation/JsonPropertyOrder.java",
                "api/jackson/OrderedDTO.java"
            )
        ) { session ->
            val services = session.installServices("Jackson field rename, ignore, and order")
            val dto = requireClass("com.itangcent.jackson.UserDTO", scenario)
            val idField = requireNotNull(dto.fields.singleOrNull { it.name == "id" })
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "Jackson DTO model"
            )

            assertEquals(
                scenarioMessage(scenario, "rule", "JsonProperty field name"),
                "user_id",
                services.ruleEngine.evaluate(RuleKeys.FIELD_NAME, idField)
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "renamed fields"),
                model.fields.containsKey("user_id") && model.fields.containsKey("user_name")
            )
            assertFalse(
                scenarioMessage(scenario, "assert", "original field names"),
                model.fields.containsKey("id") || model.fields.containsKey("name")
            )
            assertFalse(
                scenarioMessage(scenario, "assert", "JsonIgnore field"),
                model.fields.containsKey("password")
            )
            val orderedDto = requireClass("com.itangcent.jackson.OrderedDTO", scenario)
            val orderedModel = requireObject(
                services.psiClassHelper.buildObjectModel(orderedDto, JsonOption.ALL),
                scenario,
                "Jackson ordered DTO model"
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "JsonPropertyOrder field order"),
                listOf("name", "email", "age"),
                orderedModel.fields.keys.toList()
            )

            val controller = requireClass("com.itangcent.jackson.UserController", scenario)
            val endpoint = services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == "/user/create" && it.httpMetadata?.method == HttpMethod.POST
            }
            val body = requireObject(
                endpoint?.httpMetadata?.body,
                scenario,
                "Jackson request body"
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "renamed request model fields"),
                body.fields.containsKey("user_id") && body.fields.containsKey("user_name")
            )
            assertFalse(
                scenarioMessage(scenario, "assert", "ignored request model field"),
                body.fields.containsKey("password")
            )
        }
    }

    fun testMybatisPlusScenarioUsesAnnotatedEnumValues() = runTest {
        val scenario = scenario("mybatis-plus")
        harness.execute(
            scenario,
            springMvcPlan(
                "com/baomidou/mybatisplus/annotation/EnumValue.java",
                "api/mybatisplus/UserType.java",
                "api/mybatisplus/UserDTO.java",
                "api/mybatisplus/UserController.java"
            )
        ) { session ->
            val services = session.installServices("annotated enum option values")
            val enumClass = requireClass("com.itangcent.mybatisplus.UserType", scenario)
            val dto = requireClass("com.itangcent.mybatisplus.UserDTO", scenario)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "MyBatis-Plus DTO model"
            )

            assertEquals(
                scenarioMessage(scenario, "rule", "enum custom value field"),
                "code",
                services.ruleEngine.evaluate(RuleKeys.ENUM_USE_CUSTOM, enumClass)
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "enum option values"),
                listOf("30", "1100", "1200"),
                model.fields.getValue("type").options.orEmpty().map { it.value.toString() }
            )

            val controller = requireClass("com.itangcent.mybatisplus.UserController", scenario)
            val endpoints = services.springMvcExporter.export(controller)
            assertEquals(scenarioMessage(scenario, "assert", "exported endpoint count"), 2, endpoints.size)
            assertNotNull(
                scenarioMessage(scenario, "assert", "POST endpoint"),
                endpoints.singleOrNull { it.httpMetadata?.path == "/mybatisplus/user/create" }
            )
        }
    }

    fun testSpringConfigurationScenarioResolvesPropertiesPrefix() = runTest {
        val scenario = scenario("spring-configuration")
        harness.execute(
            scenario,
            fixturePlan(
                "org/springframework/boot/context/properties/ConfigurationProperties.java",
                "api/config/AppConfig.java"
            )
        ) { session ->
            val services = session.installServices("configuration properties prefix")
            val configurationClass = requireClass("com.itangcent.config.AppConfig", scenario)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(configurationClass, JsonOption.ALL),
                scenario,
                "configuration properties model"
            )

            assertEquals(
                scenarioMessage(scenario, "rule", "configuration properties prefix"),
                "app.config",
                services.ruleEngine.evaluate(RuleKeys.PROPERTIES_PREFIX, configurationClass)
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "configuration model fields"),
                setOf("name", "version", "timeout"),
                model.fields.keys
            )
        }
    }

    fun testSpringValidationsScenarioMarksRequiredFieldsAndFiltersBindingResult() = runTest {
        val scenario = scenario("spring-validations")
        harness.execute(
            scenario,
            springMvcPlan(
                "org/springframework/lang/NonNull.java",
                "org/springframework/validation/BindingResult.java",
                "org/springframework/format/annotation/DateTimeFormat.java",
                "api/validation/spring/SpringValidatedUserDTO.java",
                "api/validation/spring/SpringValidationController.java"
            )
        ) { session ->
            val services = session.installServices("required fields and filtered BindingResult parameter")
            val dto = requireClass("com.itangcent.validation.spring.SpringValidatedUserDTO", scenario)
            val requiredField = requireNotNull(dto.fields.singleOrNull { it.name == "id" })
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "Spring validation DTO model"
            )

            assertTrue(
                scenarioMessage(scenario, "rule", "NonNull required field"),
                services.ruleEngine.evaluate(RuleKeys.FIELD_REQUIRED, requiredField)
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "required field model"),
                model.fields.getValue("id").required
            )

            val controller = requireClass("com.itangcent.validation.spring.SpringValidationController", scenario)
            val endpoint = services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == "/spring/validated/user" && it.httpMetadata?.method == HttpMethod.POST
            }
            assertNotNull(scenarioMessage(scenario, "export", "validated POST endpoint"), endpoint)
            assertTrue(
                scenarioMessage(scenario, "assert", "filtered BindingResult parameter"),
                endpoint?.httpMetadata?.parameters.orEmpty()
                    .none { it.name.contains("bindingResult", ignoreCase = true) }
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "required request field"),
                requireObject(endpoint?.httpMetadata?.body, scenario, "validated request body")
                    .fields
                    .getValue("id")
                    .required
            )
        }
    }

    fun testSpringScenarioUnwrapsHttpWrappers() = runTest {
        val scenario = scenario("spring")
        harness.execute(
            scenario,
            springMvcPlan(
                "model/UserInfo.java",
                "org/springframework/http/HttpEntity.java",
                "org/springframework/http/RequestEntity.java",
                "org/springframework/http/ResponseEntity.java",
                "org/springframework/http/HttpStatus.java",
                "org/springframework/http/HttpHeaders.java",
                "org/springframework/web/context/request/async/DeferredResult.java",
                "api/spring/EntityController.java"
            )
        ) { session ->
            val services = session.installServices("unwrapped HTTP response and request bodies")
            val controller = requireClass("com.itangcent.spring.entity.EntityController", scenario)
            val responseMethod = requireNotNull(controller.methods.singleOrNull { it.name == "getUser" })
            val userInfo = requireClass("com.itangcent.model.UserInfo", scenario)
            val userModel = requireObject(
                services.psiClassHelper.buildObjectModel(userInfo, JsonOption.ALL),
                scenario,
                "unwrapped response model"
            )

            assertEquals(
                scenarioMessage(scenario, "rule", "ResponseEntity conversion"),
                "com.itangcent.model.UserInfo",
                services.ruleEngine.evaluate(RuleKeys.JSON_RULE_CONVERT, responseMethod.returnType!!, responseMethod)
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "unwrapped response model fields"),
                userModel.fields.containsKey("id") && userModel.fields.containsKey("name")
            )

            val endpoints = services.springMvcExporter.export(controller)
            val responseEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/entity/user/{id}" && it.httpMetadata?.method == HttpMethod.GET
            })
            assertTrue(
                scenarioMessage(scenario, "assert", "ResponseEntity response body"),
                requireObject(responseEndpoint.httpMetadata?.responseBody, scenario, "ResponseEntity response body")
                    .fields
                    .containsKey("id")
            )
            val requestEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/entity/request" && it.httpMetadata?.method == HttpMethod.POST
            })
            assertTrue(
                scenarioMessage(scenario, "assert", "RequestEntity request body"),
                requireObject(requestEndpoint.httpMetadata?.body, scenario, "RequestEntity request body")
                    .fields
                    .containsKey("name")
            )
        }
    }

    fun testSwaggerScenarioExportsStructuredMetadata() = runTest {
        val scenario = scenario("swagger")
        harness.execute(
            scenario,
            springMvcPlan(
                "io/swagger/annotations/Api.java",
                "io/swagger/annotations/ApiParam.java",
                "io/swagger/annotations/ApiModel.java",
                "io/swagger/annotations/ApiModelProperty.java",
                "io/swagger/annotations/ApiOperation.java",
                "io/swagger/annotations/ApiImplicitParam.java",
                "io/swagger/annotations/ApiImplicitParams.java",
                "io/swagger/annotations/ApiResponse.java",
                "io/swagger/annotations/ApiResponses.java",
                "api/swagger/ProductDTO.java",
                "api/swagger/ProductController.java"
            )
        ) { session ->
            val services = session.installServices("Swagger endpoint and request model metadata")
            val dto = requireClass("com.itangcent.swagger.ProductDTO", scenario)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "Swagger DTO model"
            )
            val controller = requireClass("com.itangcent.swagger.ProductController", scenario)
            val getMethod = requireNotNull(controller.methods.singleOrNull { it.name == "getProduct" })

            assertEquals(
                scenarioMessage(scenario, "rule", "Swagger operation documentation"),
                "Get product by ID",
                services.ruleEngine.evaluate(RuleKeys.METHOD_DOC, getMethod)
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "Swagger required model field"),
                model.fields.getValue("id").required
            )
            assertFalse(
                scenarioMessage(scenario, "assert", "Swagger hidden model field"),
                model.fields.containsKey("internalNotes")
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "Swagger renamed model field"),
                model.fields.containsKey("skuCode")
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "Swagger @ApiModelProperty example"),
                "SKU-0001",
                model.fields.getValue("skuCode").demo
            )
            assertNull(
                scenarioMessage(scenario, "assert", "Swagger field without an example"),
                model.fields.getValue("stock").demo
            )

            val endpoints = services.springMvcExporter.export(controller)
            val getEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/product/get/{id}" && it.httpMetadata?.method == HttpMethod.GET
            })
            assertEquals(
                scenarioMessage(scenario, "assert", "Swagger endpoint description"),
                "Get product by ID",
                getEndpoint.description
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "Swagger required path parameter"),
                getEndpoint.httpMetadata?.parameters.orEmpty().single { it.name == "id" }.required
            )
            val listEndpoint = requireNotNull(endpoints.singleOrNull { it.httpMetadata?.path == "/product/list" })
            assertTrue(
                scenarioMessage(scenario, "assert", "Swagger hidden query parameter"),
                listEndpoint.httpMetadata?.parameters.orEmpty().none { it.name == "filter" }
            )

            assertImplicitParams(scenario, endpoints)
            assertApiOperationAttributes(scenario, endpoints)
            assertApiResponses(scenario, endpoints)
            assertParamExample(scenario, endpoints)
        }
    }

    /**
     * `@ApiImplicitParam(s)` declares parameters that have no matching Java
     * signature (typically headers). The `export.after` rules in `swagger.config`
     * push them onto the endpoint after the Spring MVC binding pass.
     */
    private fun assertImplicitParams(
        scenario: ResolvedBuiltInExtensionScenario,
        endpoints: List<ApiEndpoint>
    ) {
        val implicitEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/implicit"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams endpoint")
        }
        val headers = implicitEndpoint.httpMetadata?.headers.orEmpty().associateBy { it.name }

        assertTrue(
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams header names"),
            headers.containsKey("Authorization") && headers.containsKey("tenantId")
        )
        assertEquals(
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams required header"),
            true,
            headers["Authorization"]?.required
        )
        assertEquals(
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams header description"),
            "token令牌",
            headers["Authorization"]?.description
        )
        val implicitParam = requireNotNull(
            implicitEndpoint.httpMetadata?.parameters.orEmpty().singleOrNull { it.name == "traceId" }
        ) {
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams query parameter")
        }
        assertEquals(
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams query default value"),
            "0",
            implicitParam.defaultValue
        )
        assertFalse(
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParams optional query parameter"),
            implicitParam.required
        )

        val singleEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/implicit/single"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParam endpoint")
        }
        val singleHeader = requireNotNull(
            singleEndpoint.httpMetadata?.headers.orEmpty().singleOrNull { it.name == "X-Trace-Id" }
        ) {
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParam header")
        }
        assertFalse(
            scenarioMessage(scenario, "assert", "Swagger @ApiImplicitParam optional header"),
            singleHeader.required
        )
    }

    /**
     * `@ApiOperation` carries more than its title: `notes` merges into the same
     * description, `hidden` drops the operation, and `httpMethod` is the fallback
     * method for a mapping that declares none.
     */
    private fun assertApiOperationAttributes(
        scenario: ResolvedBuiltInExtensionScenario,
        endpoints: List<ApiEndpoint>
    ) {
        val updateEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/update"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiOperation notes endpoint")
        }
        assertEquals(
            scenarioMessage(scenario, "assert", "Swagger @ApiOperation notes"),
            "Update a product\nRequires the product to exist",
            updateEndpoint.description
        )

        assertNull(
            scenarioMessage(scenario, "assert", "Swagger @ApiOperation hidden"),
            endpoints.singleOrNull { it.httpMetadata?.path == "/product/cleanup" }
        )

        val disableEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/disable"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiOperation httpMethod endpoint")
        }
        assertEquals(
            scenarioMessage(scenario, "assert", "Swagger @ApiOperation httpMethod"),
            HttpMethod.DELETE,
            disableEndpoint.httpMetadata?.method
        )
    }

    /**
     * `@ApiResponse` / `@ApiResponses` append the declared status code and
     * message to the endpoint description. Response headers are intentionally
     * not routed anywhere — see the note in `swagger.config`.
     */
    private fun assertApiResponses(
        scenario: ResolvedBuiltInExtensionScenario,
        endpoints: List<ApiEndpoint>
    ) {
        val deleteEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/delete"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiResponse endpoint")
        }
        assertTrue(
            scenarioMessage(scenario, "assert", "Swagger @ApiResponse description"),
            deleteEndpoint.description.orEmpty().contains("**response 404**: product not found")
        )

        val reserveEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/reserve"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiResponses endpoint")
        }
        val reserveDescription = reserveEndpoint.description.orEmpty()
        assertTrue(
            scenarioMessage(scenario, "assert", "Swagger @ApiResponses description"),
            reserveDescription.contains("**response 400**: bad request") &&
                    reserveDescription.contains("**response 409**: stock conflict")
        )
    }

    /**
     * `@ApiParam#example` feeds `param.demo`. A parameter without an example
     * must not pick up an empty one, so the absent case is pinned too.
     */
    private fun assertParamExample(
        scenario: ResolvedBuiltInExtensionScenario,
        endpoints: List<ApiEndpoint>
    ) {
        val examplesEndpoint = requireNotNull(endpoints.singleOrNull {
            it.httpMetadata?.path == "/product/examples"
        }) {
            scenarioMessage(scenario, "assert", "Swagger @ApiParam example endpoint")
        }
        val page = requireNotNull(
            examplesEndpoint.httpMetadata?.parameters.orEmpty().singleOrNull { it.name == "page" }
        ) {
            scenarioMessage(scenario, "assert", "Swagger @ApiParam example parameter")
        }
        assertEquals(
            scenarioMessage(scenario, "assert", "Swagger @ApiParam example"),
            "1",
            page.example
        )
        val size = requireNotNull(
            examplesEndpoint.httpMetadata?.parameters.orEmpty().singleOrNull { it.name == "size" }
        ) {
            scenarioMessage(scenario, "assert", "Swagger @ApiParam example-less parameter")
        }
        assertNull(
            scenarioMessage(scenario, "assert", "Swagger parameter without an example"),
            size.example
        )
    }

    fun testSwagger3ScenarioExportsStructuredMetadata() = runTest {
        val scenario = scenario("swagger3")
        harness.execute(
            scenario,
            springMvcPlan(
                "io/swagger/v3/oas/annotations/Operation.java",
                "io/swagger/v3/oas/annotations/Parameter.java",
                "io/swagger/v3/oas/annotations/Hidden.java",
                "io/swagger/v3/oas/annotations/media/Schema.java",
                "io/swagger/v3/oas/annotations/tags/Tag.java",
                "io/swagger/v3/oas/annotations/tags/Tags.java",
                "api/swagger3/OrderDTO.java",
                "api/swagger3/OrderController.java"
            )
        ) { session ->
            val services = session.installServices("OpenAPI operation, parameter, and model metadata")
            val dto = requireClass("com.itangcent.swagger3.OrderDTO", scenario)
            val model = requireObject(
                services.psiClassHelper.buildObjectModel(dto, JsonOption.ALL),
                scenario,
                "OpenAPI DTO model"
            )
            val controller = requireClass("com.itangcent.swagger3.OrderController", scenario)
            val getMethod = requireNotNull(controller.methods.singleOrNull { it.name == "getOrder" })

            assertEquals(
                scenarioMessage(scenario, "rule", "OpenAPI operation name"),
                "Get order by ID",
                services.ruleEngine.evaluate(RuleKeys.API_NAME, getMethod)
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "OpenAPI renamed required model field"),
                model.fields.getValue("orderId").required
            )
            assertFalse(
                scenarioMessage(scenario, "assert", "OpenAPI hidden model field"),
                model.fields.containsKey("internalField")
            )

            val endpoints = services.springMvcExporter.export(controller)
            val getEndpoint = requireNotNull(endpoints.singleOrNull {
                it.httpMetadata?.path == "/order/get/{id}" && it.httpMetadata?.method == HttpMethod.GET
            })
            assertEquals(
                scenarioMessage(scenario, "assert", "OpenAPI endpoint name"),
                "Get order by ID",
                getEndpoint.name
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "OpenAPI required path parameter"),
                getEndpoint.httpMetadata?.parameters.orEmpty().single { it.name == "id" }.required
            )
            assertNull(
                scenarioMessage(scenario, "assert", "OpenAPI hidden endpoint"),
                endpoints.singleOrNull { it.httpMetadata?.path == "/order/internal" }
            )

            // Method-level `@Parameter` (no matching argument) is only reachable through
            // `export.after[@io.swagger.v3.oas.annotations.Parameter]`, which runs
            // `resolve_parameter` -> `api.setParam(name, null, required, desc, example)`.
            // Without this assertion the swagger3 `export.after` rules have no direct coverage:
            // name/required/hidden above all come from other keys.
            val searchEndpoint = requireNotNull(
                endpoints.singleOrNull { it.httpMetadata?.path == "/order/search" }
            ) { scenarioMessage(scenario, "assert", "OpenAPI search endpoint") }
            val keyword = requireNotNull(
                searchEndpoint.httpMetadata?.parameters.orEmpty().singleOrNull { it.name == "keyword" }
            ) { scenarioMessage(scenario, "assert", "export.after[Parameter] added the query param") }
            assertEquals(
                scenarioMessage(scenario, "assert", "export.after[Parameter] description"),
                "search keyword",
                keyword.description
            )
            assertTrue(
                scenarioMessage(scenario, "assert", "export.after[Parameter] required"),
                keyword.required
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "export.after[Parameter] example"),
                "phone",
                keyword.example
            )
        }
    }

    private fun scenario(code: String): ResolvedBuiltInExtensionScenario {
        return requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == code
        }) {
            "Missing extension scenario: $code"
        }
    }

    private fun springMvcPlan(vararg resources: String): ExtensionFixturePlan {
        return fixturePlan(*springMvcResources, *resources)
    }

    private fun fixturePlan(vararg resources: String): ExtensionFixturePlan {
        val stubs = linkedMapOf<String, String>()
        resources.forEach { resource ->
            val content = resourceText(resource)
            stubs[qualifiedName(content, resource)] = content
        }
        return ExtensionFixturePlan(psiStubs = stubs)
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

    private fun requireClass(
        qualifiedName: String,
        scenario: ResolvedBuiltInExtensionScenario
    ) = requireNotNull(findClass(qualifiedName)) {
        scenarioMessage(scenario, "fixture", "resolvable class $qualifiedName")
    }

    private fun requireObject(
        model: ObjectModel?,
        scenario: ResolvedBuiltInExtensionScenario,
        observable: String
    ): ObjectModel.Object {
        return requireNotNull(model?.asObject()) {
            scenarioMessage(scenario, "assert", observable)
        }
    }

    private suspend fun BuiltInExtensionExecutionHarness.ExtensionScenarioSession.installServices(
        observable: String
    ): ExtensionExecutionServices {
        val reader = installIsolatedReader()
        assertTrue(
            scenarioMessage(scenario, "reload", observable),
            reader.extensionRuleKeys.isNotEmpty()
        )
        assertTrue(
            scenarioMessage(scenario, "reload", "$observable source metadata"),
            reader.extensionRuleKeys.all { key -> reader.sourcesFor(key).all { it.sourceId == "extension" } }
        )
        return reacquireServices()
    }

    private fun scenarioMessage(
        scenario: ResolvedBuiltInExtensionScenario,
        phase: String,
        observable: String
    ): String {
        val defaultState = if (scenario.extension.defaultEnabled) "enabled" else "disabled"
        return "extension=${scenario.extension.code}; default=$defaultState; phase=$phase; " +
                "condition=${scenario.conditionInput}; observable=$observable"
    }

    private companion object {
        val packagePattern = Regex("""package\s+([A-Za-z_][\w.]*)\s*;""")
        val typePattern = Regex("""(?m)^\s*(?:public\s+)?(?:class|interface|enum|@interface)\s+([A-Za-z_]\w*)""")
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
    }
}
