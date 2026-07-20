package com.itangcent.easyapi.channel.yapi

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.channel.yapi.YapiExportMode
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import com.itangcent.easyapi.core.settings.update
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Settings panel for the YApi channel.
 *
 * Self-contained panel: YApi-specific fields are read from / written to the
 * [YapiSettings] module; `enableUrlTemplating` is read/written via
 * [ParsingOutputSettings]; `switchNotice` is read/written via [GeneralSettings].
 * All reads/writes go through [SettingBinder], so the [SettingsPanel]
 * type parameter is [Settings] (the passed argument is ignored).
 */
class YapiSettingsPanel(private val project: Project) : SettingsPanel<Settings> {
    private val yapiServer = JBTextField()
    private val yapiTokens = JBTextArea(5, 40)
    private val enableUrlTemplating = JBCheckBox("Enable URL templating", true)
    private val switchNotice = JBCheckBox("Switch notice", true)
    private val yapiExportModeCombo = ComboBox(YapiExportMode.entries.toTypedArray())
    private val yapiReqBodyJson5 = JBCheckBox("Request body JSON5")
    private val yapiResBodyJson5 = JBCheckBox("Response body JSON5")

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Yapi Server:", yapiServer)
        .addLabeledComponent("Tokens (module=token per line):", JScrollPane(yapiTokens))
        .addComponent(enableUrlTemplating)
        .addComponent(switchNotice)
        .addLabeledComponent("Export Mode:", yapiExportModeCombo)
        .addComponent(yapiReqBodyJson5)
        .addComponent(yapiResBodyJson5)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        val ys = project.settings<YapiSettings>()
        yapiServer.text = ys.yapiServer ?: ""
        yapiTokens.text = ys.yapiTokens ?: ""
        enableUrlTemplating.isSelected = project.settings<ParsingOutputSettings>().enableUrlTemplating
        switchNotice.isSelected = project.settings<GeneralSettings>().switchNotice
        yapiExportModeCombo.selectedItem = ys.yapiExportMode.let {
            runCatching { YapiExportMode.valueOf(it) }.getOrNull()
        } ?: YapiExportMode.ALWAYS_UPDATE
        yapiReqBodyJson5.isSelected = ys.yapiReqBodyJson5
        yapiResBodyJson5.isSelected = ys.yapiResBodyJson5
    }

    override fun applyTo(settings: Settings) {
        SettingBinder.getInstance(project).update(ParsingOutputSettings::class) {
            enableUrlTemplating = this@YapiSettingsPanel.enableUrlTemplating.isSelected
        }
        SettingBinder.getInstance(project).update(GeneralSettings::class) {
            switchNotice = this@YapiSettingsPanel.switchNotice.isSelected
        }
        val newServer = yapiServer.text.takeIf { it.isNotBlank() }
        val newTokens = yapiTokens.text.takeIf { it.isNotBlank() }
        val newExportMode =
            (yapiExportModeCombo.selectedItem as? YapiExportMode)?.name ?: YapiExportMode.ALWAYS_UPDATE.name
        val newReqBodyJson5 = yapiReqBodyJson5.isSelected
        val newResBodyJson5 = yapiResBodyJson5.isSelected
        SettingBinder.getInstance(project).update(YapiSettings::class) {
            yapiServer = newServer
            yapiTokens = newTokens
            yapiExportMode = newExportMode
            yapiReqBodyJson5 = newReqBodyJson5
            yapiResBodyJson5 = newResBodyJson5
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        val ys = project.settings<YapiSettings>()
        return yapiServer.text != (ys.yapiServer ?: "") ||
                yapiTokens.text != (ys.yapiTokens ?: "") ||
                enableUrlTemplating.isSelected != project.settings<ParsingOutputSettings>().enableUrlTemplating ||
                switchNotice.isSelected != project.settings<GeneralSettings>().switchNotice ||
                yapiExportModeCombo.selectedItem?.toString() != ys.yapiExportMode ||
                yapiReqBodyJson5.isSelected != ys.yapiReqBodyJson5 ||
                yapiResBodyJson5.isSelected != ys.yapiResBodyJson5
    }
}
