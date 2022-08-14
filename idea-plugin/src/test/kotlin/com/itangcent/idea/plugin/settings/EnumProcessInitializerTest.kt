package com.itangcent.idea.plugin.settings

import com.google.inject.Inject
import com.intellij.ide.util.PropertiesComponent
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Test case of [EnumProcessInitializer]
 */
class EnumProcessInitializerTest : PluginContextLightCodeInsightFixtureTestCase() {


    @Inject
    private lateinit var enumProcessInitializer: EnumProcessInitializer

    @Inject
    private lateinit var settingBinder: SettingBinder

    private val settings = Settings()

    private var selectedEnumOption: Pair<String, String>? = null

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }

        //mock MessagesHelper

        val messagesHelper = mock<MessagesHelper>()

        messagesHelper.stub {
            this.on(
                messagesHelper.showChooseWithTipDialog<Any>(
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.any(),
                    Mockito.any()
                )
            ).then {
                if (selectedEnumOption?.first == "error") {
                    throw RuntimeException("error")
                }
                it.getArgument<(Any?) -> Unit>(4)(selectedEnumOption)
            }
        }
        builder.bindInstance(MessagesHelper::class, messagesHelper)
    }

    fun testInitWithNoRecord() {
        selectedEnumOption = null
        enumProcessInitializer.init()
        settings.recommendConfigs = ""
        val recommendConfigs = settingBinder.read().recommendConfigs.split(",")
        assertFalse(recommendConfigs.contains("enum_auto_select_field_by_type"))
        assertFalse(recommendConfigs.contains("enum_use_name"))
        assertFalse(recommendConfigs.contains("enum_use_ordinal"))
    }

    fun testInitWithError() {
        selectedEnumOption = "error" to ""
        PropertiesComponent.getInstance().setValue(EnumProcessInitializer.ENUM_RECOMMEND_ITEMS_CONFIRMED_KEY, false)
        EventRecords.record(EventRecords.ENUM_RESOLVE)
        settings.recommendConfigs = ""
        enumProcessInitializer.init()
        val recommendConfigs = settingBinder.read().recommendConfigs.split(",")
        assertFalse(recommendConfigs.contains("enum_auto_select_field_by_type"))
        assertFalse(recommendConfigs.contains("enum_use_name"))
        assertFalse(recommendConfigs.contains("enum_use_ordinal"))
    }


    fun testInitWithCancelSelect() {
        selectedEnumOption = null
        PropertiesComponent.getInstance().setValue(EnumProcessInitializer.ENUM_RECOMMEND_ITEMS_CONFIRMED_KEY, false)
        EventRecords.record(EventRecords.ENUM_RESOLVE)
        settings.recommendConfigs = ""
        enumProcessInitializer.init()
        val recommendConfigs = settingBinder.read().recommendConfigs.split(",")
        assertFalse(recommendConfigs.contains("enum_auto_select_field_by_type"))
        assertFalse(recommendConfigs.contains("enum_use_name"))
        assertFalse(recommendConfigs.contains("enum_use_ordinal"))
    }

    fun testInitWithSelectEnumAutoSelectFieldByType() {
        selectedEnumOption = "enum_auto_select_field_by_type" to ""
        PropertiesComponent.getInstance().setValue(EnumProcessInitializer.ENUM_RECOMMEND_ITEMS_CONFIRMED_KEY, false)
        EventRecords.record(EventRecords.ENUM_RESOLVE)
        settings.recommendConfigs = ""
        run {
            enumProcessInitializer.init()
            val recommendConfigs = settingBinder.read().recommendConfigs.split(",")
            assertTrue(recommendConfigs.contains("enum_auto_select_field_by_type"))
            assertFalse(recommendConfigs.contains("enum_use_name"))
            assertFalse(recommendConfigs.contains("enum_use_ordinal"))
        }
        run {
            selectedEnumOption = "enum_use_name" to ""
            //will not init again
            enumProcessInitializer.init()
            val recommendConfigs = settingBinder.read().recommendConfigs.split(",")
            assertTrue(recommendConfigs.contains("enum_auto_select_field_by_type"))
            assertFalse(recommendConfigs.contains("enum_use_name"))
            assertFalse(recommendConfigs.contains("enum_use_ordinal"))
        }
    }
}