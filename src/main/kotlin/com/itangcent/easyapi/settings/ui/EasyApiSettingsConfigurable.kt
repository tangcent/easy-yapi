package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.itangcent.easyapi.settings.SettingBinder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane

class EasyApiSettingsConfigurable(private val project: com.intellij.openapi.project.Project) : Configurable {
    private var panel: JPanel? = null
    private var tabs: JTabbedPane? = null

    private val settingBinder: SettingBinder by lazy {
        SettingBinder.getInstance(project)
    }

    private val generalPanel = GeneralSettingsPanel(project)
    private val postmanPanel = PostmanSettingsPanel()
    private val yapiPanel = YapiSettingsPanel()
    private val httpPanel = HttpSettingsPanel()
    private val intelligentPanel = IntelligentSettingsPanel()
    private val extensionPanel = ExtensionConfigPanel()
    private val remotePanel = RemoteConfigPanel()
    private val builtInPanel = BuiltInConfigPanel()
    private val otherPanel = OtherSettingsPanel()
    private val grpcPanel = GrpcSettingsPanel(project)
    private val environmentPanel = EnvironmentSettingsPanel(project)

    companion object {
        private var initialTab: String? = null

        fun selectTab(tabName: String) {
            initialTab = tabName
        }

        const val TAB_GENERAL = "General"
        const val TAB_POSTMAN = "Postman"
        const val TAB_HTTP = "HTTP"
        const val TAB_INTELLIGENT = "Intelligent"
        const val TAB_EXTENSIONS = "Extensions"
        const val TAB_REMOTE = "Remote"
        const val TAB_BUILT_IN = "Built-in"
        const val TAB_OTHER = "Other"
        const val TAB_GRPC = "gRPC"
        const val TAB_ENVIRONMENT = "Environments"
    }

    /**
     * Returns the display name for the settings dialog.
     */
    override fun getDisplayName(): String = "EasyApi"

    /**
     * Creates the settings UI component with tabbed panels.
     */
    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(BorderLayout())
            tabs = JTabbedPane().also { t ->
                t.addTab(TAB_GENERAL, wrapNorth(generalPanel.component))
                t.addTab(TAB_POSTMAN, wrapNorth(postmanPanel.component))
                t.addTab("Yapi", wrapNorth(yapiPanel.component))
                t.addTab(TAB_HTTP, wrapNorth(httpPanel.component))
                t.addTab(TAB_INTELLIGENT, wrapNorth(intelligentPanel.component))
                t.addTab(TAB_EXTENSIONS, extensionPanel.component)
                t.addTab(TAB_REMOTE, remotePanel.component)
                t.addTab(TAB_BUILT_IN, builtInPanel.component)
                t.addTab(TAB_OTHER, otherPanel.component)
                t.addTab(TAB_GRPC, wrapNorth(grpcPanel.component))
                t.addTab(TAB_ENVIRONMENT, environmentPanel.component)
            }
            panel!!.add(tabs, BorderLayout.CENTER)
        }
        reset()
        selectInitialTab()
        return panel!!
    }

    private fun selectInitialTab() {
        val tabName = initialTab
        if (tabName != null && tabs != null) {
            for (i in 0 until tabs!!.tabCount) {
                if (tabs!!.getTitleAt(i) == tabName) {
                    tabs!!.selectedIndex = i
                    break
                }
            }
            initialTab = null
        }
    }

    /**
     * Wraps a form panel so it stays at the top-left and doesn't stretch
     * across very wide windows.
     */
    private fun wrapNorth(component: JComponent): JComponent {
        component.maximumSize = java.awt.Dimension(600, component.maximumSize.height)
        val row = javax.swing.Box.createHorizontalBox().apply {
            add(component)
            add(javax.swing.Box.createHorizontalGlue())
        }
        return JPanel(BorderLayout()).apply {
            add(row, BorderLayout.NORTH)
        }
    }

    /**
     * Checks if any settings have been modified.
     */
    override fun isModified(): Boolean {
        val settings = settingBinder.read()
        return listOf(
            generalPanel, postmanPanel, yapiPanel, httpPanel,
            intelligentPanel, extensionPanel, remotePanel, builtInPanel, otherPanel, grpcPanel, environmentPanel
        ).any { it.isModified(settings) }
    }

    /**
     * Applies all changes from the UI panels to settings.
     */
    override fun apply() {
        val settings = settingBinder.read()
        generalPanel.applyTo(settings)
        postmanPanel.applyTo(settings)
        yapiPanel.applyTo(settings)
        httpPanel.applyTo(settings)
        intelligentPanel.applyTo(settings)
        extensionPanel.applyTo(settings)
        remotePanel.applyTo(settings)
        builtInPanel.applyTo(settings)
        otherPanel.applyTo(settings)
        grpcPanel.applyTo(settings)
        environmentPanel.applyTo(settings)
        settingBinder.save(settings)
    }

    /**
     * Resets all UI panels to the current settings values.
     */
    override fun reset() {
        val settings = settingBinder.read()
        generalPanel.resetFrom(settings)
        postmanPanel.resetFrom(settings)
        yapiPanel.resetFrom(settings)
        httpPanel.resetFrom(settings)
        intelligentPanel.resetFrom(settings)
        extensionPanel.resetFrom(settings)
        remotePanel.resetFrom(settings)
        builtInPanel.resetFrom(settings)
        otherPanel.resetFrom(settings)
        grpcPanel.resetFrom(settings)
        environmentPanel.resetFrom(settings)
    }

    override fun disposeUIResources() {
        panel = null
        tabs = null
    }
}

abstract class BaseEasyApiChildConfigurable(
    private val displayName: String,
    private val panelFactory: () -> SettingsPanel
) : Configurable {
    private var panelContainer: JPanel? = null
    private val panel: SettingsPanel by lazy { panelFactory() }

    protected var project: com.intellij.openapi.project.Project? = null

    protected val settingBinder: SettingBinder? by lazy {
        project?.let { SettingBinder.getInstance(it) }
            ?: ProjectManager.getInstance().openProjects.firstOrNull()?.let { SettingBinder.getInstance(it) }
    }

    override fun getDisplayName(): String = displayName

    override fun createComponent(): JComponent {
        if (panelContainer == null) {
            panelContainer = JPanel(BorderLayout())
            panelContainer!!.add(panel.component, BorderLayout.CENTER)
        }
        reset()
        return panelContainer!!
    }

    override fun isModified(): Boolean {
        val settings = settingBinder?.read() ?: return false
        return panel.isModified(settings)
    }

    override fun apply() {
        val binder = settingBinder ?: return
        val settings = binder.read()
        panel.applyTo(settings)
        binder.save(settings)
    }

    override fun reset() {
        panel.resetFrom(settingBinder?.read())
    }

    override fun disposeUIResources() {
        panelContainer = null
    }
}

class EasyApiExtensionConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("Extensions", { ExtensionConfigPanel() }) { init { this.project = project } }

class EasyApiRemoteConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("Remote", { RemoteConfigPanel() }) { init { this.project = project } }

class EasyApiBuiltInConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("BuiltInConfig", { BuiltInConfigPanel() }) { init { this.project = project } }

class EasyApiOtherConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("Other", { OtherSettingsPanel() }) { init { this.project = project } }
