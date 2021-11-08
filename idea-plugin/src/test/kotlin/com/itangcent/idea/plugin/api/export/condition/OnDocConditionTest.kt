package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [OnDocCondition]
 */
internal class OnDocConditionTest : AdvancedContextTest() {
    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bindInstance(ExportDoc::class, ExportDoc.of("doc1", "doc2"))
    }

    @Test
    fun matches() {
        val onDocCondition = OnDocCondition()
        assertTrue(onDocCondition.matches(actionContext, ConditionOnDoc1::class))
        assertTrue(onDocCondition.matches(actionContext, ConditionOnDoc2::class))
        assertFalse(onDocCondition.matches(actionContext, ConditionOnDoc3::class))
        assertTrue(onDocCondition.matches(actionContext, ConditionOnDoc1Or2::class))
        assertTrue(onDocCondition.matches(actionContext, ConditionOnDoc2Or3::class))
        assertFalse(onDocCondition.matches(actionContext, ConditionOnDoc3Or4::class))
    }

    @ConditionOnDoc("doc1")
    class ConditionOnDoc1

    @ConditionOnDoc("doc2")
    class ConditionOnDoc2

    @ConditionOnDoc("doc3")
    class ConditionOnDoc3

    @ConditionOnDoc("doc1", "doc2")
    class ConditionOnDoc1Or2

    @ConditionOnDoc("doc2", "doc3")
    class ConditionOnDoc2Or3

    @ConditionOnDoc("doc3", "doc4")
    class ConditionOnDoc3Or4
}