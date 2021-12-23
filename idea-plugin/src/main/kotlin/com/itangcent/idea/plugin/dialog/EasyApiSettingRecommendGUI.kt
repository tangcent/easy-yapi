package com.itangcent.idea.plugin.dialog;

import com.intellij.ui.CheckBoxList
import com.itangcent.common.utils.truncate
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class EasyApiSettingRecommendGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null

    private var recommendConfigList: CheckBoxList<String>? = null

    private var previewTextArea: JTextArea? = null

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun onCreate() {

        recommendConfigList!!.setItems(RecommendConfigLoader.codes().toList())
        {
            it.padEnd(30) + "    " +
                    RecommendConfigLoader[it]?.truncate(100)
                        ?.replace("\n", "    ")
        }

        this.recommendConfigList!!.setCheckBoxListListener { index, value ->
            val code = RecommendConfigLoader[index]
            val settings = this.settingsInstance!!
            if (value) {
                settings.recommendConfigs = RecommendConfigLoader.addSelectedConfig(settings.recommendConfigs, code)
            } else {
                settings.recommendConfigs = RecommendConfigLoader.removeSelectedConfig(settings.recommendConfigs, code)
            }
            computePreviewTextArea()
        }
    }

    override fun setSettings(settings: Settings) {

        super.setSettings(settings)

        RecommendConfigLoader.selectedCodes(settings.recommendConfigs).forEach {
            this.recommendConfigList!!.setItemSelected(it, true)
        }

        computePreviewTextArea()
    }

    private fun computePreviewTextArea() {
        this.previewTextArea!!.text =
            RecommendConfigLoader.buildRecommendConfig(
                settingsInstance?.recommendConfigs ?: "",
                "\n#${"-".repeat(20)}\n"
            )
    }

    override fun readSettings(settings: Settings) {
        settings.recommendConfigs = settingsInstance?.recommendConfigs ?: ""
    }
}
