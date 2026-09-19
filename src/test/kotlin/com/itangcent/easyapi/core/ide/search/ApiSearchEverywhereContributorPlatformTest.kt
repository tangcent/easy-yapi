package com.itangcent.easyapi.core.ide.search

import com.itangcent.easyapi.core.export.*
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Assert.*
import org.mockito.Mockito

class ApiSearchEverywhereContributorPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var contributor: ApiSearchEverywhereContributor

    override fun setUp() {
        super.setUp()
        contributor = ApiSearchEverywhereContributor(project)
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testGetSearchProviderId() {
        assertEquals(
            "com.itangcent.easyapi.search.apis",
            contributor.searchProviderId
        )
    }

    fun testGetGroupName() {
        assertEquals("APIs", contributor.groupName)
    }

    fun testGetSortWeight() {
        assertEquals(180, contributor.sortWeight)
    }

    fun testIsShownInSeparateTab() {
        assertTrue(
            "Endpoints must get their own Search Everywhere tab; the platform " +
                "default is false, which would bury them in the All tab",
            contributor.isShownInSeparateTab()
        )
    }

    fun testShowInFindResults() {
        assertTrue(contributor.showInFindResults())
    }

    fun testIsDumbAware() {
        assertTrue(contributor.isDumbAware)
    }

    fun testIsEmptyPatternSupported() {
        assertTrue(contributor.isEmptyPatternSupported)
    }

    fun testGetElementsRenderer() {
        val renderer = contributor.elementsRenderer
        assertNotNull("Renderer should not be null", renderer)
        assertTrue("Renderer should be ApiSearchResultRenderer", renderer is ApiSearchResultRenderer)
    }

    fun testGetDataForItemWithPsiElement() {
        val endpoint = ApiEndpoint(
            name = "test",
            metadata = httpMetadata(path = "/test", method = HttpMethod.GET)
        )
        val result = contributor.getDataForItem(endpoint, CommonDataKeys.PSI_ELEMENT.name)
        assertNull(result)
    }

    fun testGetDataForItemWithWrongDataId() {
        val endpoint = ApiEndpoint(
            name = "test",
            metadata = httpMetadata(path = "/test", method = HttpMethod.GET)
        )
        val result = contributor.getDataForItem(endpoint, "unknown.data.id")
        assertNull(result)
    }

    fun testProcessSelectedItemWithNoSource() {
        val endpoint = ApiEndpoint(
            name = "test",
            metadata = httpMetadata(path = "/test", method = HttpMethod.GET)
        )
        val result = contributor.processSelectedItem(endpoint, 0, "test")
        assertFalse("Should return false for endpoint without source", result)
    }

    fun testFetchElementsWithBlankPattern() {
        val indicator = Mockito.mock(com.intellij.openapi.progress.ProgressIndicator::class.java)
        val results = mutableListOf<ApiEndpoint>()
        val consumer = com.intellij.util.Processor<ApiEndpoint> { results.add(it); true }
        contributor.fetchElements("", indicator, consumer)
        assertTrue("Should return no results for blank pattern", results.isEmpty())
    }

    fun testFetchElementsWithWhitespacePattern() {
        val indicator = Mockito.mock(com.intellij.openapi.progress.ProgressIndicator::class.java)
        val results = mutableListOf<ApiEndpoint>()
        val consumer = com.intellij.util.Processor<ApiEndpoint> { results.add(it); true }
        contributor.fetchElements("   ", indicator, consumer)
        assertTrue("Should return no results for whitespace pattern", results.isEmpty())
    }

    fun testContributorIdConstant() {
        assertEquals(
            "com.itangcent.easyapi.search.apis",
            ApiSearchEverywhereContributor.CONTRIBUTOR_ID
        )
    }
}

class ApiSearchEverywhereContributorFactoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun tearDown() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
            searchEverywhereEnabled = true
        }
        super.tearDown()
    }

    fun testCreateContributor() {
        val factory = ApiSearchEverywhereContributorFactory()
        val presentation = Presentation()
        val dataContext = DataContext { dataId ->
            when (dataId) {
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }
        val event = AnActionEvent.createEvent(dataContext, presentation, "test", ActionUiKind.NONE, null)
        val contributor = factory.createContributor(event)
        assertNotNull("Contributor should not be null", contributor)
        assertTrue("Should be ApiSearchEverywhereContributor", contributor is ApiSearchEverywhereContributor)
    }

    fun testAvailableWhileTheFeatureIsEnabled() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
            searchEverywhereEnabled = true
        }

        assertTrue(
            "The platform should be offered the contributor while the feature is effective",
            ApiSearchEverywhereContributorFactory().isAvailable(project)
        )
    }

    fun testUnavailableWhenTheFeatureIsDisabled() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
            searchEverywhereEnabled = false
        }

        assertFalse(
            "FeatureStateService should report the feature as ineffective",
            FeatureStateService.getInstance(project).isEffective(CoreFeatureIds.SEARCH_EVERYWHERE)
        )
        assertFalse(
            "A disabled feature must keep the contributor out of Search Everywhere entirely",
            ApiSearchEverywhereContributorFactory().isAvailable(project)
        )
    }

    fun testAvailableWhileApiScanningIsOff() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
            searchEverywhereEnabled = true
        }

        assertTrue(
            "The contributor only reads the retained index, which the Dashboard can refill while " +
                "scanning is off, so this toggle must stay independent of API scanning",
            ApiSearchEverywhereContributorFactory().isAvailable(project)
        )
    }

    fun testAvailabilityReadsTheInjectedSeam() {
        assertFalse(ApiSearchEverywhereContributorFactory { false }.isAvailable(project))
        assertTrue(ApiSearchEverywhereContributorFactory { true }.isAvailable(project))
    }
}
