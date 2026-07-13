package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.util.json.GsonUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [GetPsiClassInfoTool].
 *
 * Verifies single-class lookup, batch lookup, the missing-class error path,
 * and the `fqns`-array batch mode. Uses the light fixture's VFS to provide
 * source classes — the tool resolves classes via [com.intellij.psi.JavaPsiFacade.findClass]
 * in the project scope.
 */
class GetPsiClassInfoToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun ctx(): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = AgentMemory(),
        approvals = NoOpApprovalGate()
    )

    private fun addClasses() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/User.java",
                """
                package com.example;
                public class User {
                    private String name;
                    private int age;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/Order.java",
                """
                package com.example;
                public class Order {
                    public String id;
                    public int total;
                }
                """.trimIndent()
            )
        }
    }

    /**
     * Seeds a generic `Result<T>` container and an `AuthResponse` DTO that
     * references `Result<User>`. Used by the type-enrichment tests to verify
     * `typeFqn` on the outer class encodes type args inline.
     */
    private fun addClassesForTypeEnrichment() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/User.java",
                """
                package com.example;
                public class User {
                    private String name;
                    private int age;
                    public String getName() { return name; }
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/Result.java",
                """
                package com.example;
                public class Result<T> {
                    public T data;
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/AuthResponse.java",
                """
                package com.example;
                import com.example.User;
                public class AuthResponse {
                    public String token;
                    public Result<User> data;
                    public String getToken() { return token; }
                }
                """.trimIndent()
            )
        }
    }

    // ------------------------------------------------------------------
    // Task 6 — type enrichment (typeFqn / returnTypeFqn — inline type args)
    // ------------------------------------------------------------------

    fun testFieldIncludesTypeFqnForClassType() {
        addClasses()
        // Ensure java.lang.String is resolvable in the light fixture — the
        // mock JDK may not expose a resolvable qualifiedName for it.
        loadJDKClass("java.lang.String")
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.User"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val fields = map["fields"] as List<*>
        val nameField = fields.first {
            (it as Map<*, *>)["name"] == "name"
        } as Map<*, *>
        Assert.assertEquals(
            "String field should resolve typeFqn to java.lang.String",
            "java.lang.String",
            nameField["typeFqn"]
        )
    }

    fun testFieldEncodesTypeArgsInlineInTypeFqn() {
        addClassesForTypeEnrichment()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.AuthResponse"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val fields = map["fields"] as List<*>
        val dataField = fields.first {
            (it as Map<*, *>)["name"] == "data"
        } as Map<*, *>
        // Type arguments are encoded inline in `typeFqn` via
        // ResolvedType.qualifiedName() — no separate `typeArguments` key.
        Assert.assertEquals(
            "Result<User> typeFqn should encode type arg inline",
            "com.example.Result<com.example.User>",
            dataField["typeFqn"]
        )
        Assert.assertFalse(
            "no typeArguments key — type args are inline in typeFqn",
            dataField.containsKey("typeArguments")
        )
    }

    fun testFieldTypeFqnNullForPrimitive() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.User"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val fields = map["fields"] as List<*>
        val ageField = fields.first {
            (it as Map<*, *>)["name"] == "age"
        } as Map<*, *>
        Assert.assertNull(
            "primitive int field should have null typeFqn",
            ageField["typeFqn"]
        )
    }

    fun testFieldNeverEmitsTypeArgumentsKey() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.User"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val fields = map["fields"] as List<*>
        val nameField = fields.first {
            (it as Map<*, *>)["name"] == "name"
        } as Map<*, *>
        Assert.assertFalse(
            "typeArguments key is never emitted — type args are inline in typeFqn",
            nameField.containsKey("typeArguments")
        )
    }

    fun testMethodReturnTypeFqnResolved() {
        addClasses()
        // Ensure java.lang.String is resolvable in the light fixture.
        loadJDKClass("java.lang.String")
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.User"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        val methods = map["methods"] as List<*>
        val getNameMethod = methods.first {
            (it as Map<*, *>)["name"] == "getName"
        } as Map<*, *>
        Assert.assertEquals(
            "getName(): String should resolve returnTypeFqn to java.lang.String",
            "java.lang.String",
            getNameMethod["returnTypeFqn"]
        )
    }

    // ------------------------------------------------------------------
    // Task 7 — tolerance + context parameter
    // ------------------------------------------------------------------

    /**
     * Seeds two `User` classes in different packages plus an `Order` class in
     * `com.example` that imports `com.example.User`. Used by the tolerance
     * tests to verify simple-name resolution with and without context.
     */
    private fun addClassesForTolerance() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/User.java",
                """
                package com.example;
                public class User {
                    public String name;
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/dto/User.java",
                """
                package com.example.dto;
                public class User {
                    public String name;
                }
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/Order.java",
                """
                package com.example;
                import com.example.User;
                public class Order {
                    public User buyer;
                }
                """.trimIndent()
            )
        }
    }

    fun testAcceptsSimpleNameWithContext() {
        addClassesForTolerance()
        // Simple name "User" + context "com.example.Order" → Order's imports
        // resolve to com.example.User (not com.example.dto.User).
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf(
                    "fqn" to "User",
                    "context" to "com.example.Order"
                ),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals(
            "context should resolve User to com.example.User",
            "com.example.User",
            map["fqn"]
        )
    }

    fun testAcceptsSimpleNameWithoutContextWhenUnambiguous() {
        // Seed only one User class — simple name resolves via PsiShortNamesCache.
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/User.java",
                """
                package com.example;
                public class User {
                    public String name;
                }
                """.trimIndent()
            )
        }
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "User"),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals("com.example.User", map["fqn"])
    }

    fun testAmbiguousSimpleNameReturnsErrorGuidingToFindClassesByName() {
        addClassesForTolerance()
        // Two User classes (com.example.User + com.example.dto.User), no
        // context → ambiguous → error guiding to find_classes_by_name.
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "User"),
                ctx()
            )
        }
        Assert.assertTrue("expected Error result, got $result", result is ToolResult.Error)
        val msg = (result as ToolResult.Error).message
        Assert.assertTrue(
            "error should mention find_classes_by_name: $msg",
            msg.contains("find_classes_by_name")
        )
        Assert.assertTrue(
            "error should mention ambiguous: $msg",
            msg.contains("ambiguous")
        )
    }

    fun testContextUnresolvableFallsBackSilently() {
        addClassesForTolerance()
        // Bogus context → falls back to no-context path. With two User
        // classes this would be ambiguous, so instead seed only one User
        // class and use a bogus context to verify silent fallback.
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/OnlyUser.java",
                """
                package com.example;
                public class OnlyUser {
                    public String name;
                }
                """.trimIndent()
            )
        }
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf(
                    "fqn" to "OnlyUser",
                    "context" to "bogus.not.a.real.fqn.or.path"
                ),
                ctx()
            )
        }
        Assert.assertTrue(
            "bogus context should fall back silently to no-context resolution: $result",
            result is ToolResult.Text
        )
        val map = GsonUtils.fromJson<Map<String, Any?>>((result as ToolResult.Text).value)
        Assert.assertEquals("com.example.OnlyUser", map["fqn"])
    }

    fun testSchemaDeclaresContextProperty() {
        val schema = GetPsiClassInfoTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare context", props.containsKey("context"))
        // context must be optional — not in `required`.
        val required = schema["required"] as? List<*>
        Assert.assertNull(
            "context must be optional (no required array expected)",
            required
        )
    }

    fun testReturnsInfoForSingleClass() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.User"),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain name", text.contains("User"))
        Assert.assertTrue("should contain fqn", text.contains("com.example.User"))
        // Should list fields and methods.
        Assert.assertTrue("should mention field name", text.contains("name"))
        Assert.assertTrue("should mention method getName", text.contains("getName"))
    }

    fun testReturnsErrorWhenClassNotFound() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqn" to "com.example.DoesNotExist"),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("not found")
        )
    }

    fun testRejectsMissingParameter() {
        val result = runBlocking {
            GetPsiClassInfoTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("missing parameter")
        )
    }

    fun testBlankFqnTreatedAsMissing() {
        val result = runBlocking {
            GetPsiClassInfoTool().execute(mapOf("fqn" to "   "), ctx())
        }
        Assert.assertTrue(result is ToolResult.Error)
    }

    fun testBatchReturnsMapOfFqnsToInfo() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User", "com.example.Order")),
                ctx()
            )
        }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain User key", text.contains("com.example.User"))
        Assert.assertTrue("should contain Order key", text.contains("com.example.Order"))
    }

    fun testBatchIncludesNotFoundEntryForMissingClass() {
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User", "com.example.Missing")),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        // The present class returns its info map; the missing one returns
        // the "not found" string placeholder.
        Assert.assertTrue("should contain User info", text.contains("com.example.User"))
        Assert.assertTrue(
            "missing class should map to 'not found'",
            text.contains("not found")
        )
    }

    fun testBatchIgnoresBlankEntries() {
        addClasses()
        // Blank entries in the `fqns` array must be filtered out, leaving
        // a single-element batch that resolves the one non-blank FQN.
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User", "  ", "")),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertTrue("should contain User info", text.contains("com.example.User"))
    }

    fun testSingleFqnsArrayWithOneElementReturnsObjectMap() {
        // When `fqns` has exactly one element, the tool still returns the
        // batch shape (a JSON object mapping FQN → info), not the single
        // object shape. This verifies the branch that picks batch vs single
        // based on list size.
        addClasses()
        val result = runBlocking {
            GetPsiClassInfoTool().execute(
                mapOf("fqns" to listOf("com.example.User")),
                ctx()
            )
        }
        Assert.assertTrue(result is ToolResult.Text)
        // The single-`fqn` path returns a flat object with "name"; the
        // single-element `fqns` path returns { "com.example.User": {...} }.
        val text = (result as ToolResult.Text).value
        Assert.assertTrue(
            "single-element fqns should still return a map keyed by FQN",
            text.contains("\"com.example.User\"")
        )
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "get_psi_class_info must be registered",
            names.contains("get_psi_class_info")
        )
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, GetPsiClassInfoTool().kind)
    }

    fun testSchemaDeclaresFqnAndFqnsProperties() {
        val schema = GetPsiClassInfoTool().parametersSchema
        val props = schema["properties"] as Map<*, *>
        Assert.assertTrue("should declare fqn", props.containsKey("fqn"))
        Assert.assertTrue("should declare fqns", props.containsKey("fqns"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
