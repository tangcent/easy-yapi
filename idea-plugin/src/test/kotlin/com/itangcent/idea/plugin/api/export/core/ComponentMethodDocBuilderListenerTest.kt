package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.itangcent.common.model.MethodDoc
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.FakeExportContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [ComponentMethodDocBuilderListener]
 */
internal class ComponentMethodDocBuilderListenerTest : AdvancedContextTest() {

    @Inject
    private lateinit var methodDocBuilderListener: MethodDocBuilderListener

    private lateinit var methodDoc: MethodDoc

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MethodDocBuilderListener::class) {
            it.with(ComponentMethodDocBuilderListener::class).singleton()
        }
        builder.bindInstance(
            "AVAILABLE_METHOD_DOC_BUILDER_LISTENER",
            arrayOf<Any>(DefaultMethodDocBuilderListener::class)
        )
    }

    @BeforeEach
    fun init() {
        methodDoc = MethodDoc()
    }

    @Test
    fun testSetName() {
        methodDocBuilderListener.setName(
            FakeExportContext.INSTANCE,
            methodDoc, "test"
        )
        assertEquals("test", methodDoc.name)
    }

    @Test
    fun testAppendDesc() {
        methodDocBuilderListener.appendDesc(
            FakeExportContext.INSTANCE,
            methodDoc, "abc"
        )
        assertEquals("abc", methodDoc.desc)
        methodDocBuilderListener.appendDesc(
            FakeExportContext.INSTANCE,
            methodDoc, "def"
        )
        assertEquals("abcdef", methodDoc.desc)
    }

    @Test
    fun testAddParam() {
        methodDocBuilderListener.addParam(
            FakeExportContext.INSTANCE,
            methodDoc, "token", "123", "token for auth", true
        )
        methodDoc.params!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
            assertEquals(true, it.required)
        }
    }

    @Test
    fun testSetRet() {
        methodDocBuilderListener.setRet(
            FakeExportContext.INSTANCE,
            methodDoc, "ret"
        )
        assertEquals("ret", methodDoc.ret)
    }

    @Test
    fun testAppendRetDesc() {
        methodDocBuilderListener.appendRetDesc(
            FakeExportContext.INSTANCE,
            methodDoc, "abc"
        )
        assertEquals("abc", methodDoc.retDesc)
        methodDocBuilderListener.appendRetDesc(
            FakeExportContext.INSTANCE,
            methodDoc, "def"
        )
        assertEquals("abc\ndef", methodDoc.retDesc)
    }
}