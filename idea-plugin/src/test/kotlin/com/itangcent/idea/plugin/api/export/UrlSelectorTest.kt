package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.PsiElement
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys.PATH_MULTI
import com.itangcent.idea.plugin.api.export.core.ResolveMultiPath
import com.itangcent.intellij.config.rule.Rule
import com.itangcent.intellij.config.rule.RuleLookUp
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.BaseContextTest
import com.itangcent.test.mock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test for [com.itangcent.idea.plugin.api.export.UrlSelector]
 */

abstract class BaseUrlSelectorAllTest : BaseContextTest() {

    @Inject
    protected lateinit var urlSelector: UrlSelector

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.mock<RuleLookUp> {
            on(
                it.lookUp(
                    PATH_MULTI.name(),
                    PATH_MULTI.mode().targetType() as KClass<Any>
                )
            ) doReturn listOf(
                StringRule.of { mode().name }
            ) as List<Rule<Any>>
        }
    }

    abstract fun mode(): ResolveMultiPath
}

class UrlSelectorAllTest : BaseUrlSelectorAllTest() {
    override fun mode(): ResolveMultiPath = ResolveMultiPath.ALL

    @Test
    fun `select urls`() {
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = null
        }))
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = URL.nil()
        }))
        assertEquals(URL.of("/single"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/single")
        }))
        assertEquals(URL.of("/short", "/middle", "/long-long"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/short", "/middle", "/long-long")
            resource = mock<PsiElement> {}
        }))
    }
}

class UrlSelectorFirstTest : BaseUrlSelectorAllTest() {
    override fun mode(): ResolveMultiPath = ResolveMultiPath.FIRST

    @Test
    fun `select urls`() {
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = null
        }))
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = URL.nil()
        }))
        assertEquals(URL.of("/single"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/single")
        }))
        assertEquals(URL.of("/short"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/short", "/middle", "/long-long")
            resource = mock<PsiElement> {}
        }))
    }
}

class UrlSelectorLastTest : BaseUrlSelectorAllTest() {
    override fun mode(): ResolveMultiPath = ResolveMultiPath.LAST

    @Test
    fun `select urls`() {
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = null
        }))
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = URL.nil()
        }))
        assertEquals(URL.of("/single"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/single")
        }))
        assertEquals(URL.of("/long-long"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/short", "/middle", "/long-long")
            resource = mock<PsiElement> {}
        }))
    }
}

class UrlSelectorShortestTest : BaseUrlSelectorAllTest() {
    override fun mode(): ResolveMultiPath = ResolveMultiPath.SHORTEST

    @Test
    fun `select urls`() {
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = null
        }))
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = URL.nil()
        }))
        assertEquals(URL.of("/single"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/single")
        }))
        assertEquals(URL.of("/short"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/short", "/middle", "/long-long")
            resource = mock<PsiElement> {}
        }))
    }
}

class UrlSelectorLongestTest : BaseUrlSelectorAllTest() {
    override fun mode(): ResolveMultiPath = ResolveMultiPath.LONGEST

    @Test
    fun `select urls`() {
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = null
        }))
        assertEquals(URL.nil(), urlSelector.selectUrls(Request().apply {
            path = URL.nil()
        }))
        assertEquals(URL.of("/single"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/single")
        }))
        assertEquals(URL.of("/long-long"), urlSelector.selectUrls(Request().apply {
            path = URL.of("/short", "/middle", "/long-long")
            resource = mock<PsiElement> {}
        }))
    }
}