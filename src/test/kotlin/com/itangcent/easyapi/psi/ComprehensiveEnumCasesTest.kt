package com.itangcent.easyapi.psi

import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException

/**
 * Comprehensive test that loads a Java DTO and a Kotlin DTO — each with fields
 * covering all enum resolution cases from the enum-resolution spec — and verifies
 * that every field in the resulting [ObjectModel] has the expected options.
 *
 * Cases covered (see `.spec/enum-resolution/requirements.md`):
 * - Case 1a: enum-typed field, default name serialization → STRING, constant names
 * - Case 1b: @JsonValue on getter → field's type, field's values
 * - Case 1b: @EnumValue on field → field's type, field's values
 * - Case 2: @see Enum#field → field's type, field's values
 * - Case 2: @see Enum#getField() → field's type, field's values
 * - Case 2: @see Enum#name() → STRING, constant names
 * - Case 2: @see Enum (class-only, auto-match by type) → matched field's type, values
 *
 * The test enables `enumFieldAutoInferEnabled` so the class-only `@see` case
 * can auto-match by type. The config carries the combined `@JsonValue` +
 * `@EnumValue` groovy recipe so annotation-based resolution works.
 */
class ComprehensiveEnumCasesTest {

    companion object {
        /**
         * Combined `enum.use.custom` groovy rule that detects both `@JsonValue`
         * (on getter or field) and `@EnumValue` (on field). This mirrors the
         * recipes shipped in `jackson.config` and `mybatis-plus.config`.
         */
        const val COMBINED_CONFIG_TEXT = """
enum.use.custom=groovy:```
    if (!it.isEnum()) return null
    def m = it.methods().find {
        it.hasAnn("com.fasterxml.jackson.annotation.JsonValue") && !it.hasModifier("static")
    }
    if (m != null) {
        def n = m.name()
        def derived = n.startsWith("get") ? n.substring(3,4).toLowerCase() + n.substring(4)
                    : n.startsWith("is")  ? n.substring(2,3).toLowerCase() + n.substring(3)
                    : n
        return it.fields().any { it.name() == derived && !it.isStatic() && !it.isEnumField() } ? derived : null
    }
    def f = it.fields().find { it.hasAnn("com.fasterxml.jackson.annotation.JsonValue") && !it.isStatic() && !it.isEnumField() }
    if (f != null) return f.name()
    f = it.fields().find { it.hasAnn("com.baomidou.mybatisplus.annotation.EnumValue") && !it.isStatic() && !it.isEnumField() }
    return f?.name()
```
"""

        /**
         * Paths of all supporting files (annotation stubs + enums) that must be
         * loaded into the test fixture before loading the DTO under test.
         */
        val SUPPORTING_FILE_PATHS: List<String> = listOf(
            // Annotation stubs (at their real FQN paths so the groovy rule can match by FQN)
            "com/fasterxml/jackson/annotation/JsonValue.java",
            "com/baomidou/mybatisplus/annotation/EnumValue.java",
            // Enum classes
            "enumcases/constant/SimpleStatus.java",
            "enumcases/constant/UserType.java",
            "enumcases/constant/JacksonStatus.java",
            "enumcases/constant/MyBatisStatus.java",
            "enumcases/constant/NameConflictEnum.java"
        )

        /**
         * Verifies every field in the given [ObjectModel.Object] has the expected
         * JSON type and option values. Shared by the Java and Kotlin DTO tests.
         */
        fun assertAllFieldsHaveExpectedOptions(result: ObjectModel?) {
            assertNotNull("buildObjectModel should return a result", result)
            assertTrue("Result should be Object", result is ObjectModel.Object)
            val fields = (result as ObjectModel.Object).fields

            // ---- Case 1a: simpleStatus — default name serialization ----
            val simpleStatus = fields["simpleStatus"]
            assertNotNull("Should have 'simpleStatus' field", simpleStatus)
            assertEquals(
                "Case 1a: simpleStatus should be STRING",
                JsonType.STRING, (simpleStatus!!.model as ObjectModel.Single).type
            )
            assertNotNull("Case 1a: simpleStatus should have options", simpleStatus.options)
            assertEquals(
                "Case 1a: simpleStatus options should be constant names",
                listOf("ACTIVE", "INACTIVE", "PENDING"),
                simpleStatus.options!!.map { it.value }
            )

            // ---- Case 1b: jacksonStatus — @JsonValue on getter ----
            val jacksonStatus = fields["jacksonStatus"]
            assertNotNull("Should have 'jacksonStatus' field", jacksonStatus)
            assertEquals(
                "Case 1b @JsonValue: jacksonStatus should be INT",
                JsonType.INT, (jacksonStatus!!.model as ObjectModel.Single).type
            )
            assertNotNull("Case 1b @JsonValue: jacksonStatus should have options", jacksonStatus.options)
            assertEquals(
                "Case 1b @JsonValue: jacksonStatus options should use code field values",
                listOf(30, 1100, 1200),
                jacksonStatus.options!!.map { it.value }
            )

            // ---- Case 1b: myBatisStatus — @EnumValue on field ----
            val myBatisStatus = fields["myBatisStatus"]
            assertNotNull("Should have 'myBatisStatus' field", myBatisStatus)
            assertEquals(
                "Case 1b @EnumValue: myBatisStatus should be INT",
                JsonType.INT, (myBatisStatus!!.model as ObjectModel.Single).type
            )
            assertNotNull("Case 1b @EnumValue: myBatisStatus should have options", myBatisStatus.options)
            assertEquals(
                "Case 1b @EnumValue: myBatisStatus options should use code field values",
                listOf(10, 20, 30),
                myBatisStatus.options!!.map { it.value }
            )

            // ---- Case 2: userCode — @see Enum#field ----
            val userCode = fields["userCode"]
            assertNotNull("Should have 'userCode' field", userCode)
            assertEquals(
                "Case 2 @see#field: userCode should be INT",
                JsonType.INT, (userCode!!.model as ObjectModel.Single).type
            )
            assertNotNull("Case 2 @see#field: userCode should have options", userCode.options)
            assertEquals(
                "Case 2 @see#field: userCode options should use code field values",
                listOf(30, 1100, 1200),
                userCode.options!!.map { it.value }
            )

            // ---- Case 2: userDesc — @see Enum#getField() ----
            val userDesc = fields["userDesc"]
            assertNotNull("Should have 'userDesc' field", userDesc)
            assertEquals(
                "Case 2 @see#getter: userDesc should be STRING",
                JsonType.STRING, (userDesc!!.model as ObjectModel.Single).type
            )
            assertNotNull("Case 2 @see#getter: userDesc should have options", userDesc.options)
            assertEquals(
                "Case 2 @see#getter: userDesc options should use desc field values",
                listOf("unspecified", "administrator", "developer"),
                userDesc.options!!.map { it.value }
            )

            // ---- Case 2: constantName — @see Enum#name() ----
            val constantName = fields["constantName"]
            assertNotNull("Should have 'constantName' field", constantName)
            assertEquals(
                "Case 2 @see#name(): constantName should be STRING",
                JsonType.STRING, (constantName!!.model as ObjectModel.Single).type
            )
            assertNotNull("Case 2 @see#name(): constantName should have options", constantName.options)
            assertEquals(
                "Case 2 @see#name(): constantName options should be constant names",
                listOf("ONE", "TWO", "THREE"),
                constantName.options!!.map { it.value }
            )

            // ---- Case 2: autoMatchedCode — @see Enum (class-only, auto-match) ----
            val autoMatchedCode = fields["autoMatchedCode"]
            assertNotNull("Should have 'autoMatchedCode' field", autoMatchedCode)
            assertEquals(
                "Case 2 @see class-only: autoMatchedCode should be INT",
                JsonType.INT, (autoMatchedCode!!.model as ObjectModel.Single).type
            )
            assertNotNull(
                "Case 2 @see class-only: autoMatchedCode should have options",
                autoMatchedCode.options
            )
            assertEquals(
                "Case 2 @see class-only: autoMatchedCode options should use code field values (int → Integer code)",
                listOf(30, 1100, 1200),
                autoMatchedCode.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Java DTO test
    // ================================================================

    class WithJavaDto : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            COMBINED_CONFIG_TEXT
        )

        override fun setUp() {
            super.setUp()
            // Enable auto-infer so class-only @see can auto-match by type.
            settingBinder.update {
                enumFieldAutoInferEnabled = true
            }
            SUPPORTING_FILE_PATHS.forEach { loadFile(it) }
        }

        fun testJavaDtoAllFieldsHaveOptions() = runBlocking {
            loadFile("enumcases/model/ComprehensiveEnumCasesDto.java")
            val psiClass = findClass("enumcases.model.ComprehensiveEnumCasesDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            assertAllFieldsHaveExpectedOptions(result)
        }
    }

    // ================================================================
    //  Kotlin DTO test
    // ================================================================

    class WithKotlinDto : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            COMBINED_CONFIG_TEXT
        )

        override fun setUp() {
            super.setUp()
            try {
                Class.forName("org.jetbrains.kotlin.idea.KotlinLanguage")
            } catch (e: ClassNotFoundException) {
                throw AssumptionViolatedException("Kotlin plugin not available")
            }
            // Enable auto-infer so class-only @see can auto-match by type.
            settingBinder.update {
                enumFieldAutoInferEnabled = true
            }
            SUPPORTING_FILE_PATHS.forEach { loadFile(it) }
        }

        fun testKotlinDtoAllFieldsHaveOptions() = runBlocking {
            loadFile("enumcases/model/ComprehensiveEnumCasesDto.kt")
            val psiClass = findClass("enumcases.model.ComprehensiveEnumCasesDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            assertAllFieldsHaveExpectedOptions(result)
        }
    }
}
