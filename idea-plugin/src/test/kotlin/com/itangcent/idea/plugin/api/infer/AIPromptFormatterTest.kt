package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case for [AIPromptFormatter]
 */
class AIPromptFormatterTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var aiPromptFormatter: AIPromptFormatter

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
    }

    fun testFormatReferencedClassesForPrompt_EmptyList() {
        val result = aiPromptFormatter.formatReferencedClassesForPrompt(emptyList())
        assertEquals("", result)
    }

    fun testFormatReferencedClassesForPrompt_SingleClass() {
        val classInfo = AIMethodInferHelper.ClassInfo(
            name = "com.example.User",
            fields = listOf(
                AIMethodInferHelper.FieldInfo(
                    name = "id",
                    type = "Long",
                    annotations = listOf("NotNull")
                ),
                AIMethodInferHelper.FieldInfo(
                    name = "name",
                    type = "String",
                    annotations = listOf("Size")
                )
            ),
            methods = listOf(
                AIMethodInferHelper.MethodSummary(
                    name = "getId",
                    returnType = "Long",
                    parameters = emptyList()
                ),
                AIMethodInferHelper.MethodSummary(
                    name = "setId",
                    returnType = "void",
                    parameters = listOf("id: Long")
                )
            )
        )

        val result = aiPromptFormatter.formatReferencedClassesForPrompt(listOf(classInfo))

        // Verify the formatted output contains expected elements
        assertTrue(result.contains("Referenced Classes:"))
        assertTrue(result.contains("Class: com.example.User"))
        assertTrue(result.contains("Fields:"))
        assertTrue(result.contains("- id: Long @NotNull"))
        assertTrue(result.contains("- name: String @Size"))
        assertTrue(result.contains("Methods:"))
        assertTrue(result.contains("- getId(): Long"))
        assertTrue(result.contains("- setId(id: Long): void"))
    }

    fun testFormatReferencedClassesForPrompt_MultipleClasses() {
        val class1 = AIMethodInferHelper.ClassInfo(
            name = "com.example.User",
            fields = listOf(
                AIMethodInferHelper.FieldInfo(
                    name = "id",
                    type = "Long",
                    annotations = listOf("NotNull")
                )
            ),
            methods = listOf(
                AIMethodInferHelper.MethodSummary(
                    name = "getId",
                    returnType = "Long",
                    parameters = emptyList()
                )
            )
        )

        val class2 = AIMethodInferHelper.ClassInfo(
            name = "com.example.Order",
            fields = listOf(
                AIMethodInferHelper.FieldInfo(
                    name = "orderId",
                    type = "String",
                    annotations = listOf("NotBlank")
                )
            ),
            methods = listOf(
                AIMethodInferHelper.MethodSummary(
                    name = "getOrderId",
                    returnType = "String",
                    parameters = emptyList()
                )
            )
        )

        val result = aiPromptFormatter.formatReferencedClassesForPrompt(listOf(class1, class2))

        // Verify both classes are included in the output
        assertTrue(result.contains("Class: com.example.User"))
        assertTrue(result.contains("Class: com.example.Order"))
        assertTrue(result.contains("- id: Long @NotNull"))
        assertTrue(result.contains("- orderId: String @NotBlank"))
        assertTrue(result.contains("- getId(): Long"))
        assertTrue(result.contains("- getOrderId(): String"))
    }

    fun testFormatMethodInfoPrompt() {
        val methodInfo = AIMethodInferHelper.MethodInfo(
            className = "com.example.UserController",
            methodName = "getUserById",
            returnTypeName = "Result<User>",
            parameters = listOf(
                AIMethodInferHelper.ParameterInfo(
                    name = "id",
                    type = "Long",
                    annotations = listOf("PathVariable")
                )
            ),
            methodAnnotations = listOf("GetMapping"),
            methodCode = "public Result<User> getUserById(@PathVariable Long id) { return userService.findById(id); }",
            classContext = listOf(
                AIMethodInferHelper.FieldInfo(
                    name = "userService",
                    type = "UserService"
                )
            ),
            referencedClasses = listOf(
                AIMethodInferHelper.ClassInfo(
                    name = "com.example.User",
                    fields = listOf(
                        AIMethodInferHelper.FieldInfo(
                            name = "id",
                            type = "Long",
                            annotations = emptyList()
                        ),
                        AIMethodInferHelper.FieldInfo(
                            name = "name",
                            type = "String",
                            annotations = emptyList()
                        )
                    ),
                    methods = emptyList()
                ),
                AIMethodInferHelper.ClassInfo(
                    name = "com.example.Result",
                    fields = listOf(
                        AIMethodInferHelper.FieldInfo(
                            name = "code",
                            type = "int",
                            annotations = emptyList()
                        ),
                        AIMethodInferHelper.FieldInfo(
                            name = "message",
                            type = "String",
                            annotations = emptyList()
                        ),
                        AIMethodInferHelper.FieldInfo(
                            name = "data",
                            type = "T",
                            annotations = emptyList()
                        )
                    ),
                    methods = emptyList()
                )
            ),
            additionalRequestedClasses = emptyList()
        )

        val result = aiPromptFormatter.formatMethodInfoPrompt(methodInfo)

        // Verify the prompt contains all the expected sections
        assertTrue(result.contains("Class: com.example.UserController"))
        assertTrue(result.contains("Method: getUserById"))
        assertTrue(result.contains("Declared Return Type: Result<User>"))
        assertTrue(result.contains("Method Annotations:"))
        assertTrue(result.contains("- GetMapping"))
        assertTrue(result.contains("Parameters:"))
        assertTrue(result.contains("- id: Long @PathVariable"))
        assertTrue(result.contains("Class Fields:"))
        assertTrue(result.contains("- userService: UserService"))
        assertTrue(result.contains("Referenced Classes:"))
        assertTrue(result.contains("Class: com.example.User"))
        assertTrue(result.contains("Class: com.example.Result"))
        assertTrue(result.contains("Method Code:"))
        assertTrue(result.contains("public Result<User> getUserById(@PathVariable Long id) { return userService.findById(id); }"))
    }

    fun testFormatMethodInfoPrompt_WithAdditionalRequestedClasses() {
        val methodInfo = AIMethodInferHelper.MethodInfo(
            className = "com.example.UserController",
            methodName = "getUserById",
            returnTypeName = "Result<User>",
            parameters = listOf(
                AIMethodInferHelper.ParameterInfo(
                    name = "id",
                    type = "Long",
                    annotations = listOf("PathVariable")
                )
            ),
            methodAnnotations = listOf("GetMapping"),
            methodCode = "public Result<User> getUserById(@PathVariable Long id) { return userService.findById(id); }",
            classContext = listOf(
                AIMethodInferHelper.FieldInfo(
                    name = "userService",
                    type = "UserService"
                )
            ),
            referencedClasses = emptyList(),
            additionalRequestedClasses = listOf(
                AIMethodInferHelper.ClassInfo(
                    name = "com.example.UserDetails",
                    fields = listOf(
                        AIMethodInferHelper.FieldInfo(
                            name = "address",
                            type = "String",
                            annotations = emptyList()
                        ),
                        AIMethodInferHelper.FieldInfo(
                            name = "phone",
                            type = "String",
                            annotations = emptyList()
                        )
                    ),
                    methods = emptyList()
                )
            )
        )

        val result = aiPromptFormatter.formatMethodInfoPrompt(methodInfo)

        // Verify the prompt contains the additional requested classes section
        assertTrue(result.contains("Class: com.example.UserDetails"))
        assertTrue(result.contains("- address: String"))
        assertTrue(result.contains("- phone: String"))
    }
} 