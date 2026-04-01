package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import java.awt.BorderLayout

class EasyApiSettingsConfigurable(private val project: com.intellij.openapi.project.Project) : Configurable {
    private var panel: JPanel? = null
    private var tabs: JTabbedPane? = null

    private val settingBinder: SettingBinder by lazy {
        SettingBinder.getInstance(project)
    }

    private val generalPanel = GeneralSettingsPanel(project)
    private val postmanPanel = PostmanSettingsPanel()
    private val httpPanel = HttpSettingsPanel()
    private val intelligentPanel = IntelligentSettingsPanel()
    private val recommendPanel = RecommendConfigPanel()
    private val remotePanel = RemoteConfigPanel()
    private val builtInPanel = BuiltInConfigPanel()
    private val otherPanel = OtherSettingsPanel()

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
                t.addTab("General", wrapNorth(generalPanel.component))
                t.addTab("Postman", wrapNorth(postmanPanel.component))
                t.addTab("HTTP", wrapNorth(httpPanel.component))
                t.addTab("Intelligent", wrapNorth(intelligentPanel.component))
                t.addTab("Recommend", recommendPanel.component)
                t.addTab("Remote", remotePanel.component)
                t.addTab("Built-in", builtInPanel.component)
                t.addTab("Other", otherPanel.component)
            }
            panel!!.add(tabs, BorderLayout.CENTER)
        }
        reset()
        return panel!!
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
            generalPanel, postmanPanel, httpPanel,
            intelligentPanel, recommendPanel, remotePanel, builtInPanel, otherPanel
        ).any { it.isModified(settings) }
    }

    /**
     * Applies all changes from the UI panels to settings.
     */
    override fun apply() {
        val settings = settingBinder.read()
        generalPanel.applyTo(settings)
        postmanPanel.applyTo(settings)
        httpPanel.applyTo(settings)
        intelligentPanel.applyTo(settings)
        recommendPanel.applyTo(settings)
        remotePanel.applyTo(settings)
        builtInPanel.applyTo(settings)
        otherPanel.applyTo(settings)
        settingBinder.save(settings)
    }

    /**
     * Resets all UI panels to the current settings values.
     */
    override fun reset() {
        val settings = settingBinder.read()
        generalPanel.resetFrom(settings)
        postmanPanel.resetFrom(settings)
        httpPanel.resetFrom(settings)
        intelligentPanel.resetFrom(settings)
        recommendPanel.resetFrom(settings)
        remotePanel.resetFrom(settings)
        builtInPanel.resetFrom(settings)
        otherPanel.resetFrom(settings)
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

class EasyApiRecommendConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("Recommend", { RecommendConfigPanel() }) { init { this.project = project } }

class EasyApiRemoteConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("Remote", { RemoteConfigPanel() }) { init { this.project = project } }

class EasyApiBuiltInConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("BuiltInConfig", { BuiltInConfigPanel() }) { init { this.project = project } }

class EasyApiOtherConfigurable(project: com.intellij.openapi.project.Project) : BaseEasyApiChildConfigurable("Other", { OtherSettingsPanel() }) { init { this.project = project } }
