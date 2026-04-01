package com.itangcent.easyapi.gap

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.core.EmptyMethodFilter
import com.itangcent.easyapi.exporter.feign.FeignClientRecognizer
import com.itangcent.easyapi.exporter.jaxrs.JaxRsContentTypeResolver
import com.itangcent.easyapi.exporter.jaxrs.JaxRsResourceRecognizer
import com.itangcent.easyapi.exporter.springmvc.ContentTypeResolver
import com.itangcent.easyapi.exporter.springmvc.SpringControllerRecognizer
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class PipelineComponentParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testSpringControllerRecognizerExists() = runTest {
        val ruleEngine = RuleEngine(actionContext, actionContext.instance(ConfigReader::class))
        val recognizer = SpringControllerRecognizer(ruleEngine)
        assertNotNull("SpringControllerRecognizer should exist", recognizer)
    }

    fun testJaxRsResourceRecognizerExists() = runTest {
        val ruleEngine = RuleEngine(actionContext, actionContext.instance(ConfigReader::class))
        val recognizer = JaxRsResourceRecognizer(ruleEngine)
        assertNotNull("JaxRsResourceRecognizer should exist", recognizer)
    }

    fun testFeignClientRecognizerExists() = runTest {
        val ruleEngine = RuleEngine(actionContext, actionContext.instance(ConfigReader::class))
        val recognizer = FeignClientRecognizer(ruleEngine)
        assertNotNull("FeignClientRecognizer should exist", recognizer)
    }

    fun testContentTypeResolverExists() = runTest {
        val annotationHelper = UnifiedAnnotationHelper()
        val ruleEngine = RuleEngine(actionContext, actionContext.instance(ConfigReader::class))
        val resolver = ContentTypeResolver(annotationHelper, ruleEngine)
        assertNotNull("ContentTypeResolver should exist", resolver)
    }

    fun testJaxRsContentTypeResolverExists() = runTest {
        val annotationHelper = UnifiedAnnotationHelper()
        val resolver = JaxRsContentTypeResolver(annotationHelper)
        assertNotNull("JaxRsContentTypeResolver should exist", resolver)
    }

    fun testMethodFilterExists() {
        val filter = EmptyMethodFilter()
        assertNotNull("EmptyMethodFilter should exist", filter)
    }
}
