package com.itangcent.idea.plugin.api.export.condition

import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [OnChannelCondition]
 */
internal class OnChannelConditionTest : AdvancedContextTest() {
    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bindInstance(ExportChannel::class, ExportChannel.of("channel1"))
    }

    @Test
    fun matches() {
        val onChannelCondition = OnChannelCondition()
        assertTrue(onChannelCondition.matches(actionContext, ConditionOnChannel1::class))
        assertFalse(onChannelCondition.matches(actionContext, ConditionOnChannel2::class))
        assertFalse(onChannelCondition.matches(actionContext, ConditionOnChannel3::class))
        assertTrue(onChannelCondition.matches(actionContext, ConditionOnChannel1And2::class))
        assertFalse(onChannelCondition.matches(actionContext, ConditionOnChannel2And3::class))
    }

    @ConditionOnChannel("channel1")
    class ConditionOnChannel1

    @ConditionOnChannel("channel2")
    class ConditionOnChannel2

    @ConditionOnChannel("channel3")
    class ConditionOnChannel3

    @ConditionOnChannel("channel1", "channel2")
    class ConditionOnChannel1And2

    @ConditionOnChannel("channel2", "channel3")
    class ConditionOnChannel2And3
}