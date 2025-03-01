package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.xml.ApplicationSettingsSupport
import com.itangcent.idea.plugin.settings.xml.ProjectSettingsSupport
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.utils.WaitHelper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import javax.swing.JComponent
import kotlin.test.*

/**
 * Test case of [AbstractEasyApiConfigurableTest]
 */
internal class AbstractEasyApiConfigurableTest {

    @Test
    fun testAbstractEasyApiConfigurable() {
        val easyApiConfigurable = FakeEasyApiConfigurable()
        try {
            assertNotNull(easyApiConfigurable.createComponent())
            val easyApiSettingGUI = easyApiConfigurable.getGUI()

            easyApiSettingGUI.waitInit()
            assertFalse(easyApiConfigurable.isModified)
            assertTrue(easyApiSettingGUI.onCreateCalled())

            easyApiSettingGUI.updateSettings { it.yapiServer = "http://127.0.0.1:3000" }
            assertTrue(easyApiConfigurable.isModified)
            assertEquals("http://127.0.0.1:3000", easyApiSettingGUI.getSettings().yapiServer)
            assertNull(easyApiConfigurable.getSettings().yapiServer)

            easyApiConfigurable.reset()
            assertFalse(easyApiConfigurable.isModified)
            assertNull(easyApiSettingGUI.getSettings().yapiServer)
            assertNull(easyApiConfigurable.getSettings().yapiServer)

            easyApiSettingGUI.updateSettings { it.yapiServer = "http://127.0.0.1:3000" }
            assertTrue(easyApiConfigurable.isModified)
            assertEquals("http://127.0.0.1:3000", easyApiSettingGUI.getSettings().yapiServer)
            assertNull(easyApiConfigurable.getSettings().yapiServer)

            easyApiConfigurable.apply()
            assertFalse(easyApiConfigurable.isModified)
            assertEquals("http://127.0.0.1:3000", easyApiSettingGUI.getSettings().yapiServer)
            assertEquals("http://127.0.0.1:3000", easyApiConfigurable.getSettings().yapiServer)
        } finally {
            easyApiConfigurable.disposeUIResources()
        }
    }
}

class FakeEasyApiConfigurable : AbstractEasyApiConfigurable(mock()) {
    private val setting: Settings = Settings()

    private val fakeEasyApiSettingGUI = FakeEasyApiSettingGUI()

    fun getGUI(): FakeEasyApiSettingGUI {
        return fakeEasyApiSettingGUI
    }

    fun getSettings(): Settings {
        return setting
    }

    override fun createGUI(): EasyApiSettingGUI {
        return fakeEasyApiSettingGUI
    }

    override fun getDisplayName(): String {
        return "FakeConfigurable"
    }

    override fun getId(): String {
        return "easyyapi.FakeConfigurable"
    }

    override fun afterBuildActionContext(builder: ActionContextBuilder) {
        super.afterBuildActionContext(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(setting)) }
    }
}

class FakeEasyApiSettingGUI : AbstractEasyApiSettingGUI() {

    private var onCreateCalled = false

    fun updateSettings(action: (Settings) -> Unit) {
        this.settingsInstance?.let { action(it) }
    }

    override fun getRootPanel(): JComponent {
        return mock()
    }

    fun waitInit() {
        WaitHelper.waitUtil(10000) {
            this.settingsInstance != null
        }
    }

    override fun onCreate() {
        this.onCreateCalled = true
    }

    fun onCreateCalled(): Boolean {
        return this.onCreateCalled
    }

    override fun readSettings(settings: Settings) {
        settingsInstance!!.copyTo(settings as ProjectSettingsSupport)
        settingsInstance!!.copyTo(settings as ApplicationSettingsSupport)
    }
}