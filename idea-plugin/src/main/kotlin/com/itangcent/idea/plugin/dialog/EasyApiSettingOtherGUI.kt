package com.itangcent.idea.plugin.dialog

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.common.utils.FileUtils
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SwingUtils
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class EasyApiSettingOtherGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null

    private var importButton: JButton? = null

    private var exportButton: JButton? = null

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun onCreate() {
        EasyIcons.Export.iconOnly(this.exportButton)
        EasyIcons.Import.iconOnly(this.importButton)
        SwingUtils.immersed(this.exportButton!!)
        SwingUtils.immersed(this.importButton!!)

        this.exportButton!!.addActionListener {
            export()
        }
        this.importButton!!.addActionListener {
            import()
        }
    }

    override fun readSettings(settings: Settings, from: Settings) {
    }

    private fun export() {
        val descriptor = FileSaverDescriptor(
            "Export Setting",
            "Choose directory to export setting to",
            "json"
        )
        descriptor.withHideIgnored(false)
        val chooser = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, this.getRootPanel()!!)
        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(EasyApiSettingGUI.setting_path)
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }
        val fileWrapper = chooser.save(toSelect, "setting.json")
        if (fileWrapper != null) {
            com.itangcent.intellij.util.FileUtils.forceSave(
                fileWrapper.file.path,
                GsonUtils.toJson(settingsInstance).toByteArray(kotlin.text.Charsets.UTF_8)
            )
        }
    }

    private fun import() {
        val descriptor = FileChooserDescriptorFactory
            .createSingleFileOrFolderDescriptor()
            .withTitle("Import Setting")
            .withDescription("Choose setting file")
            .withHideIgnored(false)
        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, this.getRootPanel()!!)
        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(EasyApiSettingGUI.setting_path)
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }
        val files = chooser.choose(null, toSelect)
        if (files.notNullOrEmpty()) {
            val virtualFile = files[0]
            val read = FileUtils.read(File(virtualFile.path), kotlin.text.Charsets.UTF_8)
            if (read.notNullOrEmpty()) {
                setSettings(GsonUtils.fromJson(read!!, Settings::class))
            }
        }
    }
}