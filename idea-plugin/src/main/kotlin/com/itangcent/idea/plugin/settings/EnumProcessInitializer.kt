package com.itangcent.idea.plugin.settings

import com.intellij.ide.util.PropertiesComponent
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.idea.plugin.Initializer
import com.itangcent.idea.plugin.settings.helper.RecommendConfigSettingsHelper
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.idea.swing.showChooseWithTipDialog
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.logger
import com.itangcent.intellij.logger.Logger

class EnumProcessInitializer : Initializer {
    override fun init() {
        val actionContext = ActionContext.getContext() ?: return
        actionContext.runAsync {
            try {
                val propertiesComponent = PropertiesComponent.getInstance()
                if (EventRecords.getRecord(EventRecords.ENUM_RESOLVE) > 0 && !propertiesComponent.getBoolean(
                        ENUM_RECOMMEND_ITEMS_CONFIRMED_KEY
                    )
                ) {
                    val selectedRecommendItem = actionContext.instance(MessagesHelper::class).showChooseWithTipDialog(
                        ENUM_RECOMMEND_ITEMS_MESSAGE,
                        ENUM_RECOMMEND_ITEMS,
                        { it.first }, { it.second })
                    if (selectedRecommendItem != null) {
                        val recommendConfigSettingsHelper = actionContext.instance(RecommendConfigSettingsHelper::class)
                        recommendConfigSettingsHelper.removeConfig(*ENUM_RECOMMEND_ITEMS
                            .filter { it != selectedRecommendItem }
                            .mapToTypedArray { it.first })
                        recommendConfigSettingsHelper.addConfig(selectedRecommendItem.first)
                    }
                    propertiesComponent.setValue(ENUM_RECOMMEND_ITEMS_CONFIRMED_KEY, true)
                }
            } catch (e: Exception) {
                actionContext.logger().traceError("error in enumRecommendItemsConfirmed.", e)
            }
        }
    }

    companion object {
        private const val ENUM_RECOMMEND_ITEMS_MESSAGE = "Select an enum converter you want to use"
        const val ENUM_RECOMMEND_ITEMS_CONFIRMED_KEY = "com.itangcent.enumRecommendItemsConfirmed"
        private val ENUM_RECOMMEND_ITEMS = listOf(
            "enum_auto_select_field_by_type" to "Auto map enum to a type matched field in it.\n" +
                    "----------------------------------\n" +
                    "public enum UserType {\n" +
                    "    /**\n" +
                    "     * who is not logged in\n" +
                    "     */\n" +
                    "    GUEST(30, \"unspecified\"),\n" +
                    "    /**\n" +
                    "     * system manager\n" +
                    "     */\n" +
                    "    ADMIN(1100, \"administrator\"),\n" +
                    "    /**\n" +
                    "     * developer that designs this app\n" +
                    "     */\n" +
                    "    DEVELOPER(1200, \"developer\");\n" +
                    "\n" +
                    "    private final Integer code;\n" +
                    "    private final String desc;\n" +
                    "\n" +
                    "    UserType(Integer code, String desc) {\n" +
                    "           ...\n" +
                    "    }\n" +
                    "}" +
                    "\n" +
                    "----------------------------------\n" +
                    "    /**\n" +
                    "     * @see com.itangcent.common.constant.UserType\n" +
                    "     */\n" +
                    "    private int type;\n" +
                    "===========>\n" +
                    "30: who is not logged in,\n" +
                    "200: system manager,\n" +
                    "1200: developer that designs this app\n" +
                    "----------------------------------\n" +
                    "    /**\n" +
                    "     * @see com.itangcent.common.constant.UserType\n" +
                    "     */\n" +
                    "    private String type;\n" +
                    "===========>\n" +
                    "unspecified: who is not logged in,\n" +
                    "administrator: system manager,\n" +
                    "developer: developer that designs this app\n" +
                    "----------------------------------",
            "enum_use_name" to "Map enum to it's name.\n" +
                    "----------------------------------\n" +
                    "public enum UserType {\n" +
                    "    /**\n" +
                    "     * who is not logged in\n" +
                    "     */\n" +
                    "    GUEST(30, \"unspecified\"),\n" +
                    "    /**\n" +
                    "     * system manager\n" +
                    "     */\n" +
                    "    ADMIN(1100, \"administrator\"),\n" +
                    "    /**\n" +
                    "     * developer that designs this app\n" +
                    "     */\n" +
                    "    DEVELOPER(1200, \"developer\");\n" +
                    "\n" +
                    "    private final Integer code;\n" +
                    "    private final String desc;\n" +
                    "\n" +
                    "    UserType(Integer code, String desc) {\n" +
                    "           ...\n" +
                    "    }\n" +
                    "}" +
                    "\n" +
                    "----------------------------------\n" +
                    "    /**\n" +
                    "     * @see com.itangcent.common.constant.UserType\n" +
                    "     */\n" +
                    "    private int type;\n" +
                    "===========>\n" +
                    "GUEST: who is not logged in,\n" +
                    "ADMIN: system manager,\n" +
                    "DEVELOPER: developer that designs this app\n" +
                    "----------------------------------",
            "enum_use_ordinal" to "Map enum to it's ordinal.\n" +
                    "----------------------------------\n" +
                    "public enum UserType {\n" +
                    "    /**\n" +
                    "     * who is not logged in\n" +
                    "     */\n" +
                    "    GUEST(30, \"unspecified\"),\n" +
                    "    /**\n" +
                    "     * system manager\n" +
                    "     */\n" +
                    "    ADMIN(1100, \"administrator\"),\n" +
                    "    /**\n" +
                    "     * developer that designs this app\n" +
                    "     */\n" +
                    "    DEVELOPER(1200, \"developer\");\n" +
                    "\n" +
                    "    private final Integer code;\n" +
                    "    private final String desc;\n" +
                    "\n" +
                    "    UserType(Integer code, String desc) {\n" +
                    "           ...\n" +
                    "    }\n" +
                    "}" +
                    "\n" +
                    "----------------------------------\n" +
                    "    /**\n" +
                    "     * @see com.itangcent.common.constant.UserType\n" +
                    "     */\n" +
                    "    private int type;\n" +
                    "===========>\n" +
                    "0: who is not logged in,\n" +
                    "1: system manager,\n" +
                    "2: developer that designs this app\n" +
                    "----------------------------------",
        )
    }
}