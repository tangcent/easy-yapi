package com.itangcent.easyapi.core.rule

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Tests for [RuleDryRunValidator] — the proposal-time execution of every
 * `groovy:` value against representative PSI contexts.
 *
 * The fixture project supplies one class, so every context kind
 * (class/method/field/parameter) has a representative element. Which exact
 * element is chosen is irrelevant: scripts here either work on any context
 * of their kind or fail on all of them.
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass).
 */
class RuleDryRunValidatorTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addClass(
            """
            package com.test;
            public class SampleService {
                private String name;
                public String greet(String who) { return who; }
            }
            """.trimIndent()
        )
    }

    fun testValidScriptPassesDryRun() = runTest {
        val review = RuleDryRunValidator.dryRun("class.name=groovy:it.name()", project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    /**
     * The exact regression this pass covers: `it.static` resolves through the
     * boolean is-getter, which was missing from method contexts — the dry
     * run must accept it now that
     * [com.itangcent.easyapi.core.rule.context.MethodContext.isStatic] exists.
     */
    fun testItStaticOnMethodAndClassContextsPasses() = runTest {
        // `api.name` is evaluated against both method and class contexts.
        val review = RuleDryRunValidator.dryRun("api.name=groovy:it.static", project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    fun testNonCompilingScriptIsAnError() = runTest {
        val review = RuleDryRunValidator.dryRun("class.name=groovy:it.name(((", project)

        assertFalse("errors: ${review.errors}", review.ok)
        assertTrue(
            "errors: ${review.errors}",
            review.errors.any { it.contains("does not compile") }
        )
    }

    fun testMissingContextApiIsAnError() = runTest {
        val review = RuleDryRunValidator.dryRun(
            "class.name=groovy:it.nonExistentContextApi123()",
            project
        )

        assertFalse("errors: ${review.errors}", review.ok)
        assertTrue(
            "errors: ${review.errors}",
            review.errors.any { it.contains("context API that does not exist") }
        )
    }

    fun testOtherExceptionIsOnlyAWarning() = runTest {
        // NPE is classified RUNTIME: often an artifact of the representative
        // element, so it must not block the proposal.
        val review = RuleDryRunValidator.dryRun("class.name=groovy:null.foo()", project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue(
            "warnings: ${review.warnings}",
            review.warnings.any { it.contains("null-safety") }
        )
    }

    /**
     * An API miss on only some context kinds is a warning, not an error: a
     * script may legitimately target one kind (`it.throwsExceptions()` is
     * method-only, `ignore` is evaluated against all four kinds).
     */
    fun testApiMissOnSomeKindsOnlyIsAWarning() = runTest {
        val review = RuleDryRunValidator.dryRun("ignore=groovy:it.throwsExceptions()", project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue(
            "warnings: ${review.warnings}",
            review.warnings.any { it.contains("contextType") }
        )
    }

    fun testTypeContextKeysAreSkipped() = runTest {
        // `json.rule.convert` needs a resolved type no representative element
        // can supply — a dry run would fail spuriously, so the key is skipped.
        val review = RuleDryRunValidator.dryRun(
            "json.rule.convert=groovy:it.nonExistentContextApi123()",
            project
        )

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    fun testExtraBindingKeysAreSkipped() = runTest {
        // `export.after` runs with extra bindings the dry run cannot supply.
        val review = RuleDryRunValidator.dryRun(
            "export.after=groovy:it.nonExistentContextApi123()",
            project
        )

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    fun testGroovyFilterIsDryRun() = runTest {
        val content = "class.name[groovy:it.nonExistentContextApi123()]=whatever"
        val review = RuleDryRunValidator.dryRun(content, project)

        assertFalse("errors: ${review.errors}", review.ok)
        assertTrue(
            "errors: ${review.errors}",
            review.errors.any { it.contains("filter") && it.contains("class.name") }
        )
    }

    fun testMultiLineGroovyBlockIsDryRun() = runTest {
        val content = """
            class.name=groovy:```
            return it.name()
            ```
        """.trimIndent()
        val review = RuleDryRunValidator.dryRun(content, project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    fun testNonGroovyMultiLineBlockIsSkipped() = runTest {
        val content = """
            markdown.template=```
            {{api.name}}
            ```
        """.trimIndent()
        val review = RuleDryRunValidator.dryRun(content, project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    fun testContentWithoutGroovyIsClean() = runTest {
        val content = """
            # plain rules only
            api.name=My API
            ignore=false
        """.trimIndent()
        val review = RuleDryRunValidator.dryRun(content, project)

        assertTrue("errors: ${review.errors}", review.errors.isEmpty())
        assertTrue("warnings: ${review.warnings}", review.warnings.isEmpty())
    }

    /**
     * The merged entry point: static errors and dry-run errors both reach
     * the caller via the aggregate [CompositeRuleValidator], and an
     * infrastructure failure of the dry run never blocks a statically-clean
     * proposal on its own.
     */
    fun testReviewWithDryRunMergesBothPasses() = runTest {
        val content = """
            unknown.rule.key=value
            class.name=groovy:it.nonExistentContextApi123()
        """.trimIndent()
        val review = CompositeRuleValidator.defaultPipeline().validate(content, project)

        assertFalse(review.ok)
        assertTrue(
            "errors: ${review.errors}",
            review.errors.any { it.contains("unknown rule key") && it.contains("unknown.rule.key") }
        )
        assertTrue(
            "errors: ${review.errors}",
            review.errors.any { it.contains("context API that does not exist") }
        )
    }
}
