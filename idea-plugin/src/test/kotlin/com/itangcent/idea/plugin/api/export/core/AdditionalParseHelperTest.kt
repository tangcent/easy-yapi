package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.BaseContextTest
import kotlin.reflect.KClass

/**
 * Test case with [AdditionalParseHelper]
 */
abstract class AdditionalParseHelperTest : BaseContextTest() {

    @Inject
    protected lateinit var additionalParseHelper: AdditionalParseHelper

    abstract val additionalParseHelperClass: KClass<out AdditionalParseHelper>

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(AdditionalParseHelper::class) { it.with(additionalParseHelperClass) }
    }
}