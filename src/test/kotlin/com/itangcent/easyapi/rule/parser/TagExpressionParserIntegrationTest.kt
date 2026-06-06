package com.itangcent.easyapi.rule.parser

import com.intellij.testFramework.registerServiceInstance
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.StringRuleMode
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Test

/**
 * Integration tests for [TagExpressionParser] verifying that `#tag` expressions
 * correctly collect all `@tag` values when used with [StringRuleMode.MERGE_DISTINCT].
 *
 * See: https://github.com/tangcent/easy-yapi/issues/1360
 */
class TagExpressionParserIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

    // ── MERGE_DISTINCT: should return all @tag values ─────────────

    @Test
    fun testMergeDistinctCapturesAllTags() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/MultiTagMethod.java", """
            package com.test.tag;
            /**
             * A method with multiple tags.
             * @tag user
             * @tag admin
             */
            public class MultiTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.MultiTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT),
            psiClass
        )
        assertEquals("user\nadmin", result)
    }

    @Test
    fun testMergeDistinctDeduplicatesTags() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/DuplicateTagMethod.java", """
            package com.test.tag;
            /**
             * A method with duplicate tags.
             * @tag user
             * @tag admin
             * @tag user
             */
            public class DuplicateTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.DuplicateTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT),
            psiClass
        )
        assertEquals("user\nadmin", result)
    }

    @Test
    fun testMergeDistinctWithSingleTag() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/SingleTagMethod.java", """
            package com.test.tag;
            /**
             * A method with a single tag.
             * @tag user
             */
            public class SingleTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.SingleTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT),
            psiClass
        )
        assertEquals("user", result)
    }

    @Test
    fun testMergeDistinctWithNoTag() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/NoTagMethod.java", """
            package com.test.tag;
            /**
             * A method with no tags.
             */
            public class NoTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.NoTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT),
            psiClass
        )
        assertNull(result)
    }

    // ── SINGLE mode: should return only the first @tag value ──────

    @Test
    fun testSingleModeReturnsFirstTagOnly() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/SingleModeTagMethod.java", """
            package com.test.tag;
            /**
             * A method with multiple tags.
             * @tag user
             * @tag admin
             */
            public class SingleModeTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.SingleModeTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.SINGLE),
            psiClass
        )
        assertEquals("user", result)
    }

    // ── MERGE mode: should return all @tag values ─────────────────

    @Test
    fun testMergeModeCapturesAllTags() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/MergeModeTagMethod.java", """
            package com.test.tag;
            /**
             * A method with multiple tags.
             * @tag user
             * @tag admin
             */
            public class MergeModeTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.MergeModeTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE),
            psiClass
        )
        assertEquals("user\nadmin", result)
    }

    // ── Combined: #tag with literal fallback ───────────────────────

    @Test
    fun testTagExpressionCombinedWithLiteralValue() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.tag" to "default-tag",
                "api.tag" to "#tag"
            )
        )

        loadFile("tag/CombinedTagMethod.java", """
            package com.test.tag;
            /**
             * A method with tags.
             * @tag custom
             */
            public class CombinedTagMethod {}
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.CombinedTagMethod")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT),
            psiClass
        )
        assertEquals("default-tag\ncustom", result)
    }

    // ── Method-level tags ──────────────────────────────────────────

    @Test
    fun testMergeDistinctCapturesAllMethodTags() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.tag" to "#tag")
        )

        loadFile("tag/MethodTagClass.java", """
            package com.test.tag;
            public class MethodTagClass {
                /**
                 * Get user info.
                 * @tag user
                 * @tag read
                 */
                public void getUser() {}
            }
        """.trimIndent())

        val ruleEngine = RuleEngine.getInstance(project)
        val psiClass = findClass("com.test.tag.MethodTagClass")!!
        val method = findMethod(psiClass, "getUser")!!

        val result = ruleEngine.evaluate(
            RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT),
            method
        )
        assertEquals("user\nread", result)
    }
}
