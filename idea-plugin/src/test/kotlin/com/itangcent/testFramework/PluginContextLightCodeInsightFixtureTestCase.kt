package com.itangcent.testFramework

import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.ConstantModuleHelper
import com.itangcent.mock.EmptyMessagesHelper
import com.itangcent.mock.SettingBinderAdaptor


abstract class PluginContextLightCodeInsightFixtureTestCase : ContextLightCodeInsightFixtureTestCase() {

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        builder.bind(SettingBinder::class) { it.with(SettingBinderAdaptor::class) }
        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
        builder.bindInstance("plugin.name", "easy_api")
        builder.bind(ModuleHelper::class) { it.toInstance(ConstantModuleHelper.INSTANCE) }
        builder.bind(MessagesHelper::class) { it.with(EmptyMessagesHelper::class).singleton() }
    }
}