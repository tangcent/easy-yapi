package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.intellij.openapi.actionSystem.AnActionEvent
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

    fun testCreateContributor() {
        val factory = ApiSearchEverywhereContributorFactory()
        val presentation = Presentation()
        val dataContext = DataContext { dataId ->
            when (dataId) {
                CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }
        val event = AnActionEvent.createFromDataContext("test", presentation, dataContext)
        val contributor = factory.createContributor(event)
        assertNotNull("Contributor should not be null", contributor)
        assertTrue("Should be ApiSearchEverywhereContributor", contributor is ApiSearchEverywhereContributor)
    }
}
