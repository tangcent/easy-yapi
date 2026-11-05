package com.itangcent.easyapi.core.export.aspect

import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.kotlin.mock

/**
 * Verifies the description/title de-duplication from issue #1487.
 *
 * The pure [stripDuplicatedTitle] cases need no project; the enablement and
 * dispatch cases use the real `apiParseAspect` extension point and the fixture
 * project's [com.itangcent.easyapi.core.settings.SettingBinder].
 */
class DescriptionTitleDedupAspectTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        // APPLICATION-scope settings survive across fixture projects in one JVM,
        // so reset the flag before every test to keep them order-independent.
        settingBinder.update(ParsingOutputSettings::class) {
            removeDuplicateTitleFromDescription = true
        }
    }

    private fun endpoint(name: String?, description: String?) = ApiEndpoint(
        name = name,
        description = description,
        metadata = HttpMetadata("/test", HttpMethod.GET)
    )

    private fun context(endpoint: ApiEndpoint) = EndpointParseContext(
        endpoint = endpoint,
        frameworkName = "SpringMVC",
        resolvedMethod = ResolvedMethod("m", mock<PsiMethod>())
    )

    // --- pure function ---

    fun testFirstLineEqualToNameIsRemoved() {
        val endpoint = endpoint(
            "Multi-line method doc",
            "Multi-line method doc\nthat describes the method"
        )

        endpoint.stripDuplicatedTitle()

        assertEquals("that describes the method", endpoint.description)
    }

    fun testRemainingLinesKeepTheirIndentation() {
        val endpoint = endpoint("Title", "Title\n    indented code line")

        endpoint.stripDuplicatedTitle()

        assertEquals("    indented code line", endpoint.description)
    }

    fun testFirstLineOnlyContainingNameIsKept() {
        val endpoint = endpoint("Title", "Title: partial match is not the same line\nbody")

        endpoint.stripDuplicatedTitle()

        assertEquals("Title: partial match is not the same line\nbody", endpoint.description)
    }

    fun testOnlyTitleLineResultsInEmptyDescription() {
        val endpoint = endpoint("Title", "Title")

        endpoint.stripDuplicatedTitle()

        assertEquals("", endpoint.description)
    }

    fun testLeadingBlankLinesAreSkippedBeforeComparing() {
        val endpoint = endpoint("Title", "\n\nTitle\nbody")

        endpoint.stripDuplicatedTitle()

        assertEquals("body", endpoint.description)
    }

    fun testNameComparisonIgnoresSurroundingWhitespace() {
        val endpoint = endpoint("  Title  ", "  Title  \nbody")

        endpoint.stripDuplicatedTitle()

        assertEquals("body", endpoint.description)
    }

    fun testNullOrBlankValuesAreUnchanged() {
        val noName = endpoint(null, "Title\nbody")
        noName.stripDuplicatedTitle()
        assertEquals("Title\nbody", noName.description)

        val blankName = endpoint("   ", "Title\nbody")
        blankName.stripDuplicatedTitle()
        assertEquals("Title\nbody", blankName.description)

        val blankDescription = endpoint("Title", "   ")
        blankDescription.stripDuplicatedTitle()
        assertEquals("   ", blankDescription.description)
    }

    // --- aspect ---

    fun testAspectMutatesEndpointInPlace() = runTest {
        val endpoint = endpoint("Title", "Title\nbody")
        val aspect = DescriptionTitleDedupAspect(project)

        aspect.afterEndpoint(context(endpoint))

        assertEquals("The same instance must carry the stripped description", "body", endpoint.description)
    }

    fun testAspectOrderIsAfterStandardRules() {
        assertEquals(100, DescriptionTitleDedupAspect(project).order)
    }

    fun testAspectIsEnabledByDefault() {
        assertTrue(DescriptionTitleDedupAspect(project).isEnabled())
    }

    fun testAspectIsDisabledWhenSettingIsOff() {
        try {
            settingBinder.update(ParsingOutputSettings::class) {
                removeDuplicateTitleFromDescription = false
            }

            assertFalse(DescriptionTitleDedupAspect(project).isEnabled())
        } finally {
            settingBinder.update(ParsingOutputSettings::class) {
                removeDuplicateTitleFromDescription = true
            }
        }
    }

    fun testDispatchStripsDuplicateTitleByDefault() = runTest {
        val endpoint = endpoint("Title", "Title\nbody")

        ApiParseAspects.getInstance(project).afterEndpoint(context(endpoint))

        assertEquals("body", endpoint.description)
    }

    fun testDispatchLeavesDescriptionAloneWhenSettingIsOff() = runTest {
        val endpoint = endpoint("Title", "Title\nbody")
        try {
            settingBinder.update(ParsingOutputSettings::class) {
                removeDuplicateTitleFromDescription = false
            }

            ApiParseAspects.getInstance(project).afterEndpoint(context(endpoint))

            assertEquals("Title\nbody", endpoint.description)
        } finally {
            settingBinder.update(ParsingOutputSettings::class) {
                removeDuplicateTitleFromDescription = true
            }
        }
    }
}
