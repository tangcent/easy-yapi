package com.itangcent.easyapi.psi

import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.update
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

        /** Enum that declares an instance field literally named `name` (issue #1383). */
        const val NAME_CONFLICT_ENUM = """
            package constant;
            public enum NameConflict {
                /** first */
                ONE(1),
                /** second */
                TWO(2),
                /** third */
                THREE(3);

                private final Integer name;

                NameConflict(Integer name) {
                    this.name = name;
                }

                public Integer getName() { return name; }
            }
        """

        /** Enum with a single instance field — used to test INTELLIGENT mode in Case 1. */
        const val SINGLE_FIELD_ENUM = """
            package constant;
            public enum SingleField {
                /** guest */
                GUEST(30),
                /** admin */
                ADMIN(1100),
                /** developer */
                DEVELOPER(1200);

                private final Integer type;

                SingleField(Integer type) {
                    this.type = type;
                }

                public Integer getType() { return type; }
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

        override fun setUp() {
            super.setUp()
            // Auto-match by type is only performed in INTELLIGENT mode (issue #1383).
            settingBinder.update(GeneralSettings::class) {
                enumFieldAutoInferEnabled = true
            }
        }

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

    // ================================================================
    //  Issue #1383 — name/name() ambiguity (Case 1)
    //  An enum with an instance field literally named `name` should
    //  use that field's values when `enum.use.custom=name`, while
    //  `enum.use.custom=name()` still resolves to the pseudo-field.
    // ================================================================

    class Case1_NameFieldConflict : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "name"
        )

        fun testCustomNameResolvesToInstanceField() = runBlocking {
            myFixture.addFileToProject("constant/NameConflict.java", NAME_CONFLICT_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/NameConflictDto.java", """
                package model;
                import constant.NameConflict;
                public class NameConflictDto {
                    public NameConflict value;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.NameConflictDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["value"]!!
            // `name` resolves to the Integer instance field, not the pseudo-field
            assertEquals(
                "enum.use.custom=name with instance field `name` should produce INT type",
                JsonType.INT, (field.model as ObjectModel.Single).type
            )
            assertNotNull(field.options)
            assertEquals(
                "Options should use the `name` instance field values",
                listOf(1, 2, 3),
                field.options!!.map { it.value }
            )
        }
    }

    class Case1_NameCallPseudoField : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "name()"
        )

        fun testCustomNameCallResolvesToPseudoField() = runBlocking {
            myFixture.addFileToProject("constant/NameConflict.java", NAME_CONFLICT_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/NameConflictDto.java", """
                package model;
                import constant.NameConflict;
                public class NameConflictDto {
                    public NameConflict value;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.NameConflictDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["value"]!!
            // `name()` is unambiguous → pseudo-field → STRING
            assertEquals(
                "enum.use.custom=name() should produce STRING type",
                JsonType.STRING, (field.model as ObjectModel.Single).type
            )
            assertNotNull(field.options)
            assertEquals(
                "Options should be enum constant names",
                listOf("ONE", "TWO", "THREE"),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Issue #1383 — INTELLIGENT mode for Case 1
    //  When the enum has exactly one instance field, that field is
    //  auto-selected without requiring `enum.use.custom`.
    // ================================================================

    class Case1_IntelligentMode : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        override fun setUp() {
            super.setUp()
            settingBinder.update(GeneralSettings::class) {
                enumFieldAutoInferEnabled = true
            }
        }

        fun testIntelligentModeSelectsSingleInstanceField() = runBlocking {
            myFixture.addFileToProject("constant/SingleField.java", SINGLE_FIELD_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/SingleFieldDto.java", """
                package model;
                import constant.SingleField;
                public class SingleFieldDto {
                    public SingleField value;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.SingleFieldDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["value"]!!
            assertEquals(
                "INTELLIGENT mode should pick the single Integer field → INT",
                JsonType.INT, (field.model as ObjectModel.Single).type
            )
            assertNotNull(field.options)
            assertEquals(
                "Options should use the single instance field values",
                listOf(30, 1100, 1200),
                field.options!!.map { it.value }
            )
        }

        fun testIntelligentModeFallsBackToNameForMultiFieldEnum() = runBlocking {
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

            val field = (result as ObjectModel.Object).fields["type"]!!
            // UserType has two instance fields (code, desc) → fall back to name()
            assertEquals(
                "INTELLIGENT mode should fall back to STRING when multiple instance fields exist",
                JsonType.STRING, (field.model as ObjectModel.Single).type
            )
            assertEquals(
                "Options should be enum constant names",
                listOf("GUEST", "ADMIN", "DEVELOPER"),
                field.options!!.map { it.value }
            )
        }
    }

    class Case1_NameModeIgnoresSingleField : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        override fun setUp() {
            super.setUp()
            settingBinder.update(GeneralSettings::class) {
                enumFieldAutoInferEnabled = false
            }
        }

        fun testNameModeDoesNotAutoSelectSingleInstanceField() = runBlocking {
            myFixture.addFileToProject("constant/SingleField.java", SINGLE_FIELD_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/SingleFieldDto.java", """
                package model;
                import constant.SingleField;
                public class SingleFieldDto {
                    public SingleField value;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.SingleFieldDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["value"]!!
            // NAME mode → always enum constant name, even with a single instance field
            assertEquals(
                "NAME mode should produce STRING type",
                JsonType.STRING, (field.model as ObjectModel.Single).type
            )
            assertEquals(
                "Options should be enum constant names",
                listOf("GUEST", "ADMIN", "DEVELOPER"),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Issue #1383 — INTELLIGENT mode for Case 2 (@see auto-match)
    //  In NAME mode, a class-only @see falls back to enum constant
    //  names. In INTELLIGENT mode, type-based auto-match is applied.
    // ================================================================

    class Case2_NameModeIgnoresAutoMatch : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        override fun setUp() {
            super.setUp()
            settingBinder.update(GeneralSettings::class) {
                enumFieldAutoInferEnabled = false
            }
        }

        fun testNameModeFallsBackToConstantNames() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * @see UserType
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull(field.options)
            // NAME mode → no auto-match → enum constant names
            assertEquals(
                "NAME mode should fall back to enum constant names for class-only @see",
                listOf("GUEST", "ADMIN", "DEVELOPER"),
                field.options!!.map { it.value }
            )
        }
    }

    class Case2_IntelligentModeAutoMatch : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        override fun setUp() {
            super.setUp()
            settingBinder.update(GeneralSettings::class) {
                enumFieldAutoInferEnabled = true
            }
        }

        fun testIntelligentModeAutoMatchesByType() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/UserDto.java", """
                package model;
                import constant.UserType;
                public class UserDto {
                    /**
                     * @see UserType
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.UserDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["type"]!!
            assertNotNull(field.options)
            // INTELLIGENT mode → int matches Integer code
            assertEquals(
                "INTELLIGENT mode should auto-match by type to the `code` field",
                listOf(30, 1100, 1200),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Simple enum with zero instance fields
    // ================================================================

    class Case1_SimpleEnum : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testSimpleEnumUsesConstantNamesInNameMode() = runBlocking {
            myFixture.addFileToProject("constant/SimpleStatus.java", SIMPLE_STATUS_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/SimpleDto.java", """
                package model;
                import constant.SimpleStatus;
                public class SimpleDto {
                    public SimpleStatus status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.SimpleDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["status"]!!
            assertEquals(JsonType.STRING, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf("ACTIVE", "INACTIVE", "PENDING"),
                field.options!!.map { it.value }
            )
        }

        fun testSimpleEnumUsesConstantNamesInIntelligentMode() = runBlocking {
            settingBinder.update(GeneralSettings::class) {
                enumFieldAutoInferEnabled = true
            }
            myFixture.addFileToProject("constant/SimpleStatus.java", SIMPLE_STATUS_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/SimpleDto.java", """
                package model;
                import constant.SimpleStatus;
                public class SimpleDto {
                    public SimpleStatus status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.SimpleDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["status"]!!
            // No instance fields → falls back to name regardless of mode
            assertEquals(JsonType.STRING, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf("ACTIVE", "INACTIVE", "PENDING"),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  @JsonValue annotation detection (Case 1b)
    // ================================================================

    class Case1_JsonValueOnGetter : EasyApiLightCodeInsightFixtureTestCase() {

        private val jsonValueAnnotation = """
            package com.fasterxml.jackson.annotation;
            public @interface JsonValue {
            }
        """.trimIndent()

        val enumWithJsonValue = """
            package constant;
            import com.fasterxml.jackson.annotation.JsonValue;
            public enum StatusWithJsonValue {
                /** guest */
                GUEST(30),
                /** admin */
                ADMIN(1100),
                /** developer */
                DEVELOPER(1200);

                private final Integer code;

                StatusWithJsonValue(Integer code) {
                    this.code = code;
                }

                @JsonValue
                public Integer getCode() { return code; }
            }
        """.trimIndent()

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
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
                return f?.name()
            ```
            """.trimIndent()
        )

        fun testJsonValueOnGetterUsesCodeField() = runBlocking {
            myFixture.addFileToProject("com/fasterxml/jackson/annotation/JsonValue.java", jsonValueAnnotation)
            myFixture.addFileToProject("constant/StatusWithJsonValue.java", enumWithJsonValue)
            myFixture.addFileToProject(
                "model/StatusDto.java", """
                package model;
                import constant.StatusWithJsonValue;
                public class StatusDto {
                    public StatusWithJsonValue status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.StatusDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["status"]!!
            // @JsonValue on getCode() → code field → INT type, code values
            assertEquals(JsonType.INT, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf(30, 1100, 1200),
                field.options!!.map { it.value }
            )
        }
    }

    class Case1_JsonValueOnField : EasyApiLightCodeInsightFixtureTestCase() {

        private val jsonValueAnnotation = """
            package com.fasterxml.jackson.annotation;
            public @interface JsonValue {
            }
        """.trimIndent()

        val enumWithJsonValueOnField = """
            package constant;
            import com.fasterxml.jackson.annotation.JsonValue;
            public enum StatusWithJsonValueField {
                /** guest */
                GUEST(30),
                /** admin */
                ADMIN(1100);

                private final Integer code;

                StatusWithJsonValueField(Integer code) {
                    this.code = code;
                }

                @JsonValue
                private final Integer code;
            }
        """.trimIndent()

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
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
                return f?.name()
            ```
            """.trimIndent()
        )

        fun testJsonValueOnFieldUsesThatField() = runBlocking {
            myFixture.addFileToProject("com/fasterxml/jackson/annotation/JsonValue.java", jsonValueAnnotation)
            myFixture.addFileToProject("constant/StatusWithJsonValueField.java", enumWithJsonValueOnField)
            myFixture.addFileToProject(
                "model/StatusDto2.java", """
                package model;
                import constant.StatusWithJsonValueField;
                public class StatusDto2 {
                    public StatusWithJsonValueField status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.StatusDto2")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["status"]!!
            assertEquals(JsonType.INT, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf(30, 1100),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  @EnumValue annotation detection (Case 1b)
    // ================================================================

    class Case1_EnumValue : EasyApiLightCodeInsightFixtureTestCase() {

        private val enumValueAnnotation = """
            package com.baomidou.mybatisplus.annotation;
            public @interface EnumValue {
            }
        """.trimIndent()

        val enumWithEnumValue = """
            package constant;
            import com.baomidou.mybatisplus.annotation.EnumValue;
            public enum StatusWithEnumValue {
                /** guest */
                GUEST(30),
                /** admin */
                ADMIN(1100);

                private final Integer code;

                StatusWithEnumValue(Integer code) {
                    this.code = code;
                }

                @EnumValue
                private final Integer code;
            }
        """.trimIndent()

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            enum.use.custom=groovy:```
                if (!it.isEnum()) return null
                def f = it.fields().find { it.hasAnn("com.baomidou.mybatisplus.annotation.EnumValue") && !it.isStatic() && !it.isEnumField() }
                return f?.name()
            ```
            """.trimIndent()
        )

        fun testEnumValueOnFieldUsesThatField() = runBlocking {
            myFixture.addFileToProject("com/baomidou/mybatisplus/annotation/EnumValue.java", enumValueAnnotation)
            myFixture.addFileToProject("constant/StatusWithEnumValue.java", enumWithEnumValue)
            myFixture.addFileToProject(
                "model/StatusDto3.java", """
                package model;
                import constant.StatusWithEnumValue;
                public class StatusDto3 {
                    public StatusWithEnumValue status;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.StatusDto3")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["status"]!!
            assertEquals(JsonType.INT, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf(30, 1100),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Type reconciliation (Case 2)
    // ================================================================

    class Case2_TypeReconciliation : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testIntFieldSeeEnumCodeReconcilesToInteger() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/TypeReconDto.java", """
                package model;
                import constant.UserType;
                public class TypeReconDto {
                    /**
                     * @see UserType#code
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.TypeReconDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["type"]!!
            // int field + @see Enum#code (Integer) → INT (primitive↔boxed normalize)
            assertEquals(JsonType.INT, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf(30, 1100, 1200),
                field.options!!.map { it.value }
            )
        }

        fun testStringFieldSeeEnumCodeReconcilesToInteger() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/TypeReconDto2.java", """
                package model;
                import constant.UserType;
                public class TypeReconDto2 {
                    /**
                     * @see UserType#code
                     */
                    public String type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.TypeReconDto2")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["type"]!!
            // String field + @see Enum#code (Integer) → INT (incompatible, values authoritative)
            assertEquals(
                "String field with @see Enum#code (Integer) should reconcile to INT",
                JsonType.INT, (field.model as ObjectModel.Single).type
            )
            assertEquals(
                listOf(30, 1100, 1200),
                field.options!!.map { it.value }
            )
        }

        fun testLongFieldSeeEnumCodeReconcilesToInteger() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/TypeReconDto3.java", """
                package model;
                import constant.UserType;
                public class TypeReconDto3 {
                    /**
                     * @see UserType#code
                     */
                    public long type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.TypeReconDto3")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["type"]!!
            // long field + @see Enum#code (Integer) → INT (numeric narrowing, value-field wins)
            assertEquals(
                "long field with @see Enum#code (Integer) should reconcile to INT",
                JsonType.INT, (field.model as ObjectModel.Single).type
            )
        }
    }

    // ================================================================
    //  @see name()/name parity (Case 2)
    // ================================================================

    class Case2_SeeNameParity : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testSeeNameCallResolvesToPseudoField() = runBlocking {
            myFixture.addFileToProject("constant/NameConflict.java", NAME_CONFLICT_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/SeeNameCallDto.java", """
                package model;
                import constant.NameConflict;
                public class SeeNameCallDto {
                    /**
                     * @see NameConflict#name()
                     */
                    public int value;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.SeeNameCallDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["value"]!!
            // name() → pseudo-field → STRING, constant names
            assertEquals(JsonType.STRING, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf("ONE", "TWO", "THREE"),
                field.options!!.map { it.value }
            )
        }

        fun testSeeNameBareResolvesToInstanceField() = runBlocking {
            myFixture.addFileToProject("constant/NameConflict.java", NAME_CONFLICT_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/SeeNameBareDto.java", """
                package model;
                import constant.NameConflict;
                public class SeeNameBareDto {
                    /**
                     * @see NameConflict#name
                     */
                    public int value;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.SeeNameBareDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["value"]!!
            // bare name → instance field wins (issue #1383) → INT, field values
            assertEquals(JsonType.INT, (field.model as ObjectModel.Single).type)
            assertEquals(
                listOf(1, 2, 3),
                field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  Case 1 / Case 2 consistency
    // ================================================================

    class Case1Case2Consistency : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "enum.use.custom" to "code"
        )

        fun testSameEnumProducesIdenticalResultsCase1AndCase2() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            // Case 1: enum-typed field
            myFixture.addFileToProject(
                "model/Case1Dto.java", """
                package model;
                import constant.UserType;
                public class Case1Dto {
                    public UserType type;
                }
            """.trimIndent()
            )
            // Case 2: int field with @see UserType#code
            myFixture.addFileToProject(
                "model/Case2Dto.java", """
                package model;
                import constant.UserType;
                public class Case2Dto {
                    /**
                     * @see UserType#code
                     */
                    public int type;
                }
            """.trimIndent()
            )

            val case1Result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(findClass("model.Case1Dto")!!, option = JsonOption.ALL)
            val case2Result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(findClass("model.Case2Dto")!!, option = JsonOption.ALL)

            val case1Field = (case1Result as ObjectModel.Object).fields["type"]!!
            val case2Field = (case2Result as ObjectModel.Object).fields["type"]!!

            // Same JSON type
            assertEquals(
                "Case 1 and Case 2 should produce the same JSON type",
                (case1Field.model as ObjectModel.Single).type,
                (case2Field.model as ObjectModel.Single).type
            )
            // Same option values
            assertEquals(
                "Case 1 and Case 2 should produce the same option values",
                case1Field.options!!.map { it.value },
                case2Field.options!!.map { it.value }
            )
        }
    }

    // ================================================================
    //  @see with nonexistent member falls back to names
    // ================================================================

    class Case2_SeeNonexistentMember : EasyApiLightCodeInsightFixtureTestCase() {

        override fun createConfigReader() = TestConfigReader.empty(project)

        fun testSeeNonexistentMemberFallsBackToNames() = runBlocking {
            myFixture.addFileToProject("constant/UserType.java", USER_TYPE_ENUM.trimIndent())
            myFixture.addFileToProject(
                "model/NonexistentDto.java", """
                package model;
                import constant.UserType;
                public class NonexistentDto {
                    /**
                     * @see UserType#nonexistent
                     */
                    public int type;
                }
            """.trimIndent()
            )
            val psiClass = findClass("model.NonexistentDto")!!
            val result = DefaultPsiClassHelper.getInstance(project)
                .buildObjectModel(psiClass, option = JsonOption.ALL)

            val field = (result as ObjectModel.Object).fields["type"]!!
            // Nonexistent member → falls back to constant names
            assertEquals(
                listOf("GUEST", "ADMIN", "DEVELOPER"),
                field.options!!.map { it.value }
            )
        }
    }
}
