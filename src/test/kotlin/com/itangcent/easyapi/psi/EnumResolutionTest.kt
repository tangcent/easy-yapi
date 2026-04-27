package com.itangcent.easyapi.psi

import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelValueConverter
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking

/**
 * Tests for enum resolution in two distinct cases:
 *
 * **Case 1: Field declared as enum type** (`UserType status`)
 * - Default: JSON type = STRING, options = enum constant names
 * - `enum.use.custom=code`: JSON type = INT, options = code field values
 * - `enum.use.custom=name`: JSON type = STRING, options = enum constant names
 * - `enum.use.custom=ordinal`: JSON type = INT, options = ordinal indices
 *
 * **Case 2: Normal-typed field with `@see EnumClass`** (`int type` with `@see UserType`)
 * - `@see UserType#type` → explicit field, uses that field's values
 * - `@see UserType#getType()` → getter resolved to field, uses that field's values
 * - `@see UserType` (no member) → auto-match by type (int → Integer code)
 */
class EnumResolutionTest {

    // ================================================================
    //  Shared enum source used across all test classes
    // ================================================================

    companion object {
        const val USER_TYPE_ENUM = """
            package constant;
            public enum UserType {
                /** who is not logged in */
                GUEST(30, "unspecified"),
                /** system manager */
                ADMIN(1100, "administrator"),
                /** developer that designs this app */
                DEVELOPER(1200, "developer");

                private final Integer code;
                private final String desc;

                UserType(Integer code, String desc) {
                    this.code = code;
                    this.desc = desc;
                }

                public Integer getCode() { return code; }
                public String getDesc() { return desc; }
            }
        """

        const val SIMPLE_STATUS_ENUM = """
            package constant;
            public enum SimpleStatus {
                /** Active */
                ACTIVE,
                /** Inactive */
                INACTIVE,
                /** Pending review */
                PENDING
            }
        """
    }

    // ================================================================
    //  Case 1: Enum-typed field — default (no config)
    // ================================================================

    class Case1_Default : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testEnumFieldDefaultJsonType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /** user type */
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass)

            assertNotNull(result)
            assertTrue("Should be Object", result is ObjectModel.Object)
            val typeField = (result as ObjectModel.Object).fields["type"]
            assertNotNull("Should have 'type' field", typeField)
            // Default: enum → STRING
            assertTrue(
                "Enum field should be Single",
                typeField!!.model is ObjectModel.Single
            )
            assertEquals(
                "Default enum JSON type should be STRING",
                JsonType.STRING, (typeField.model as ObjectModel.Single).type
            )
        }

        fun testEnumFieldDefaultOptions() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /** user type */
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options", typeField.options)
            // Default: options use enum constant names
            val values = typeField.options!!.map { it.value }
            assertEquals(
                "Default options should be enum constant names",
                listOf("GUEST", "ADMIN", "DEVELOPER"), values
            )
        }

        fun testSimpleEnumFieldOptions() = runBlocking {
            myFixture.addFileToProject("constant/SimpleStatus.java", SIMPLE_STATUS_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/TaskDto.java", """
                package model;
                import constant.SimpleStatus;
                public class TaskDto {
                    public SimpleStatus status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.TaskDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val statusField = (result as ObjectModel.Object).fields["status"]!!
            assertEquals(JsonType.STRING, (statusField.model as ObjectModel.Single).type)
            assertNotNull(statusField.options)
            assertEquals(
                listOf("ACTIVE", "INACTIVE", "PENDING"),
                statusField.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Case 1: Enum-typed field — enum.use.custom=code
    // ================================================================

    class Case1_CustomCode : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "code"
        )

        fun testEnumFieldCustomCodeJsonType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            // code is Integer → JSON type should be INT
            assertEquals(
                "enum.use.custom=code should produce INT type",
                JsonType.INT, (typeField.model as ObjectModel.Single).type
            )
        }

        fun testEnumFieldCustomCodeOptions() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options", typeField.options)
            val values = typeField.options!!.map { it.value }
            assertEquals(
                "Options should use code field values",
                listOf(30, 1100, 1200), values
            )
        }
    }

    // ================================================================
    //  Case 1: Enum-typed field — enum.use.custom=desc
    // ================================================================

    class Case1_CustomDesc : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "desc"
        )

        fun testEnumFieldCustomDescJsonType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            // desc is String → JSON type should be STRING
            assertEquals(
                "enum.use.custom=desc should produce STRING type",
                JsonType.STRING, (typeField.model as ObjectModel.Single).type
            )
        }

        fun testEnumFieldCustomDescOptions() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options", typeField.options)
            val values = typeField.options!!.map { it.value }
            assertEquals(
                "Options should use desc field values",
                listOf("unspecified", "administrator", "developer"), values
            )
        }
    }

    // ================================================================
    //  Case 1: Enum-typed field — enum.use.custom=name
    // ================================================================

    class Case1_CustomName : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "name"
        )

        fun testEnumFieldCustomNameJsonType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertEquals(
                "enum.use.custom=name should produce STRING type",
                JsonType.STRING, (typeField.model as ObjectModel.Single).type
            )
        }

        fun testEnumFieldCustomNameOptions() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull(typeField.options)
            assertEquals(
                "Options should be enum constant names",
                listOf("GUEST", "ADMIN", "DEVELOPER"),
                typeField.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Case 1: Enum-typed field — enum.use.custom=ordinal
    // ================================================================

    class Case1_CustomOrdinal : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "ordinal"
        )

        fun testEnumFieldCustomOrdinalJsonType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertEquals(
                "enum.use.custom=ordinal should produce INT type",
                JsonType.INT, (typeField.model as ObjectModel.Single).type
            )
        }

        fun testEnumFieldCustomOrdinalOptions() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    public UserType type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull(typeField.options)
            assertEquals(
                "Options should be ordinal indices",
                listOf(0, 1, 2),
                typeField.options!!.map { it.value }
            )
        }
    }


    // ================================================================
    //  Case 2: Normal-typed field with @see — explicit field
    // ================================================================

    class Case2_SeeWithExplicitField : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testSeeEnumWithHashField() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * User Type
                     * @see UserType#code
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            // JSON type is int (from the field declaration), not from enum
            assertEquals(JsonType.INT, (typeField.model as ObjectModel.Single).type)
            // Options should use code field values
            assertNotNull("Should have options from @see", typeField.options)
            assertEquals(
                "Options should use code field values via @see",
                listOf(30, 1100, 1200),
                typeField.options!!.map { it.value }
            )
        }

        fun testSeeEnumWithGetterMethod() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * User Type
                     * @see UserType#getCode()
                     */
                    public Integer type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options from @see with getter", typeField.options)
            assertEquals(
                "Options should use code field values via getter @see",
                listOf(30, 1100, 1200),
                typeField.options!!.map { it.value }
            )
        }

        fun testSeeEnumWithLinkSyntax() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * User Type
                     * @see {@link UserType#code}
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options from {@link} @see", typeField.options)
            assertEquals(
                "Options should use code field values via {@link}",
                listOf(30, 1100, 1200),
                typeField.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Case 2: Normal-typed field with @see — no member (auto-match)
    // ================================================================

    class Case2_SeeWithAutoMatch : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testSeeEnumAutoMatchByIntType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * User Type
                     * @see UserType
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options from @see auto-match", typeField.options)
            // int type → matches Integer code in UserType
            assertEquals(
                "Auto-match should select code field (Integer matches int)",
                listOf(30, 1100, 1200),
                typeField.options!!.map { it.value }
            )
        }

        fun testSeeEnumAutoMatchByStringType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * User Type
                     * @see UserType
                     */
                    public String type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options from @see auto-match", typeField.options)
            // String type → matches String desc in UserType
            assertEquals(
                "Auto-match should select desc field (String matches String)",
                listOf("unspecified", "administrator", "developer"),
                typeField.options!!.map { it.value }
            )
        }

        fun testSeeEnumNoMatchFallsBackToName() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * User Type
                     * @see UserType
                     */
                    public boolean type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull("Should have options even without type match", typeField.options)
            // boolean doesn't match any field in UserType → falls back to enum constant names
            assertEquals(
                "No type match should fall back to enum constant names",
                listOf("GUEST", "ADMIN", "DEVELOPER"),
                typeField.options!!.map { it.value }
            )
        }

        fun testSeeSimpleEnumAutoMatch() = runBlocking {
            myFixture.addFileToProject("constant/SimpleStatus.java", SIMPLE_STATUS_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/TaskDto.java", """
                package model;
                import constant.SimpleStatus;
                public class TaskDto {
                    /**
                     * Task status
                     * @see SimpleStatus
                     */
                    public String status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.TaskDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val statusField = (result as ObjectModel.Object).fields["status"]!!
            assertNotNull("Should have options", statusField.options)
            // SimpleStatus has no instance fields → falls back to enum constant names
            assertEquals(
                "Simple enum @see should use constant names",
                listOf("ACTIVE", "INACTIVE", "PENDING"),
                statusField.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Case 2: @see with desc field options include descriptions
    // ================================================================

    class Case2_OptionDescriptions : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testSeeEnumOptionsHaveDescriptions() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * @see UserType#code
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val typeField = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull(typeField.options)
            // When using code field, descriptions should come from doc comments or other fields
            val firstOption = typeField.options!!.first()
            assertEquals(30, firstOption.value)
            // Description should be from doc comment ("who is not logged in") or from other fields ("unspecified")
            assertNotNull("Option should have a description", firstOption.desc)
        }
    }
}
