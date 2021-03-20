package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.itangcent.common.model.MethodDoc
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [DefaultMethodDocHelper]
 */
internal class DefaultMethodDocHelperTest : AdvancedContextTest() {

    @Inject
    private lateinit var methodDocHelper: MethodDocHelper

    private lateinit var methodDoc: MethodDoc

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MethodDocHelper::class) { it.with(DefaultMethodDocHelper::class) }
    }

    @BeforeEach
    fun init() {
        methodDoc = MethodDoc()
    }


    @Test
    fun testSetName() {
        methodDocHelper.setName(methodDoc, "test")
        assertEquals("test", methodDoc.name)
    }

    @Test
    fun testAppendDesc() {
        methodDocHelper.appendDesc(methodDoc, "abc")
        assertEquals("abc", methodDoc.desc)
        methodDocHelper.appendDesc(methodDoc, "def")
        assertEquals("abcdef", methodDoc.desc)
    }

    @Test
    fun testAddParam() {
        methodDocHelper.addParam(methodDoc, "token", "123", "token for auth", true)
        methodDoc.params!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
            assertEquals(true, it.required)
        }
    }

    @Test
    fun testSetRet() {
        methodDocHelper.setRet(methodDoc, "ret")
        assertEquals("ret", methodDoc.ret)
    }

    @Test
    fun testAppendRetDesc() {
        methodDocHelper.appendRetDesc(methodDoc, "abc")
        assertEquals("abc", methodDoc.retDesc)
        methodDocHelper.appendRetDesc(methodDoc, "def")
        assertEquals("abc\ndef", methodDoc.retDesc)
    }
}