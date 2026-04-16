package com.itangcent.easyapi.rule

import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.ConfigReloadListener
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*
import org.junit.Test

class RuleProviderGetInstanceTest : EasyApiLightCodeInsightFixtureTestCase() {
    @Test
    fun testGetInstance() {
        val instance = RuleProvider.getInstance(project)
        assertNotNull(instance)
        assertSame(instance, RuleProvider.getInstance(project))
    }
}

class RuleProviderEmptyKeyTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

    @Test
    fun testGetRulesReturnsEmptyForNonExistentKey() {
        val ruleProvider = RuleProvider.getInstance(project)
        val rules = ruleProvider.getRules(RuleKey.string("nonexistent.key"))

        assertTrue("Should return empty list for non-existent key", rules.isEmpty())
    }
}

class RuleProviderSingleRuleTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "api.name" to "Test API",
        "api.tag" to "tag1"
    )

    @Test
    fun testGetRulesReturnsRulesFromConfig() {
        val ruleProvider = RuleProvider.getInstance(project)
        val rules = ruleProvider.getRules(RuleKey.string("api.name"))

        assertEquals("Should return one rule", 1, rules.size)
        assertEquals("Test API", rules[0].expression)
        assertNull("Rule should have no filter", rules[0].filter)
    }
}

class RuleProviderMultipleRulesTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "api.tag" to "tag1",
        "api.tag" to "tag2",
        "api.tag" to "tag3"
    )

    @Test
    fun testGetRulesReturnsMultipleRules() {
        val ruleProvider = RuleProvider.getInstance(project)
        val rules = ruleProvider.getRules(RuleKey.string("api.tag"))

        assertEquals("Should return all rules", 3, rules.size)
        assertEquals("tag1", rules[0].expression)
        assertEquals("tag2", rules[1].expression)
        assertEquals("tag3", rules[2].expression)
    }
}

class RuleProviderIndexedFiltersTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "api.name" to "Base API",
        "api.name[true]" to "Filtered API",
        "api.name[false]" to "Filtered API 2"
    )

    @Test
    fun testGetRulesWithIndexedFilters() {
        val ruleProvider = RuleProvider.getInstance(project)
        val rules = ruleProvider.getRules(RuleKey.string("api.name"))

        assertEquals("Should return all rules including indexed", 3, rules.size)

        val baseRule = rules.find { it.filter == null }
        assertNotNull("Should have base rule", baseRule)
        assertEquals("Base API", baseRule?.expression)

        val trueFilterRule = rules.find { it.filter == "true" }
        assertNotNull("Should have rule with 'true' filter", trueFilterRule)
        assertEquals("Filtered API", trueFilterRule?.expression)

        val falseFilterRule = rules.find { it.filter == "false" }
        assertNotNull("Should have rule with 'false' filter", falseFilterRule)
        assertEquals("Filtered API 2", falseFilterRule?.expression)
    }
}

class RuleProviderAliasesTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "api.name" to "Primary Name",
        "method.name" to "Alias Name"
    )

    @Test
    fun testGetRulesWithAliases() {
        val ruleProvider = RuleProvider.getInstance(project)
        val keyWithAlias = RuleKey.string("api.name", aliases = listOf("method.name"))
        val rules = ruleProvider.getRules(keyWithAlias)

        assertEquals("Should return rules from primary key and alias", 2, rules.size)
        assertTrue("Should contain primary rule", rules.any { it.expression == "Primary Name" })
        assertTrue("Should contain alias rule", rules.any { it.expression == "Alias Name" })
    }
}

class RuleProviderCacheTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "api.name" to "Test API"
    )

    @Test
    fun testCacheIsUsedForRepeatedCalls() {
        val ruleProvider = RuleProvider.getInstance(project)

        val rules1 = ruleProvider.getRules(RuleKey.string("api.name"))
        val rules2 = ruleProvider.getRules(RuleKey.string("api.name"))

        assertEquals("Should return same rules on repeated calls", rules1, rules2)
    }
}

class RuleProviderMultipleKeysTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "api.name" to "Test API",
        "api.tag" to "tag1"
    )

    @Test
    fun testDifferentKeysCachedSeparately() {
        val ruleProvider = RuleProvider.getInstance(project)

        val nameRules = ruleProvider.getRules(RuleKey.string("api.name"))
        val tagRules = ruleProvider.getRules(RuleKey.string("api.tag"))

        assertEquals("Should have one name rule", 1, nameRules.size)
        assertEquals("Test API", nameRules[0].expression)

        assertEquals("Should have one tag rule", 1, tagRules.size)
        assertEquals("tag1", tagRules[0].expression)
    }
}

class RuleProviderConfigReloadTest : EasyApiLightCodeInsightFixtureTestCase() {
    private lateinit var configReader: TestConfigReader

    override fun createConfigReader(): ConfigReader {
        configReader = TestConfigReader.fromRules(project, "api.name" to "Initial API")
        return configReader
    }

    @Test
    fun testCacheClearedOnConfigReload() {
        val ruleProvider = RuleProvider.getInstance(project)

        val rules1 = ruleProvider.getRules(RuleKey.string("api.name"))
        assertEquals("Initial API", rules1[0].expression)

        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "api.name" to "Updated API")
        )

        project.messageBus.syncPublisher(ConfigReloadListener.TOPIC).onConfigReloaded()

        val rules2 = ruleProvider.getRules(RuleKey.string("api.name"))
        assertEquals("Updated API", rules2[0].expression)
    }
}

class RuleProviderRuleOrderTest : EasyApiLightCodeInsightFixtureTestCase() {
    override fun createConfigReader(): ConfigReader = TestConfigReader.fromRules(
        project,
        "json.rule.convert[#regex:org.springframework.http.ResponseEntity<(.*?)>]" to "\${1}",
        "json.rule.convert[org.springframework.http.ResponseEntity]" to "java.lang.Object",
        "json.rule.convert[#regex:org.springframework.web.context.request.async.DeferredResult<(.*?)>]" to "\${1}",
        "json.rule.convert[org.springframework.web.context.request.async.DeferredResult]" to "java.lang.Object"
    )

    @Test
    fun testRuleOrderIsPreserved() {
        val ruleProvider = RuleProvider.getInstance(project)
        val rules = ruleProvider.getRules(RuleKey.string("json.rule.convert"))

        assertEquals("Should return all 4 rules", 4, rules.size)

        assertEquals(
            "First rule should be regex for ResponseEntity",
            "#regex:org.springframework.http.ResponseEntity<(.*?)>", rules[0].filter
        )
        assertEquals("\${1}", rules[0].expression)

        assertEquals(
            "Second rule should be raw ResponseEntity",
            "org.springframework.http.ResponseEntity", rules[1].filter
        )
        assertEquals("java.lang.Object", rules[1].expression)

        assertEquals(
            "Third rule should be regex for DeferredResult",
            "#regex:org.springframework.web.context.request.async.DeferredResult<(.*?)>", rules[2].filter
        )
        assertEquals("\${1}", rules[2].expression)

        assertEquals(
            "Fourth rule should be raw DeferredResult",
            "org.springframework.web.context.request.async.DeferredResult", rules[3].filter
        )
        assertEquals("java.lang.Object", rules[3].expression)
    }
}
