package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.ui.Messages
import com.intellij.util.containers.stream
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.setting.SettingManager
import com.itangcent.intellij.setting.TokenSetting
import java.awt.event.*
import javax.swing.*
import kotlin.streams.toList

class TokenSettingDialog : JDialog() {
    private var contentPane: JPanel? = null

    private var hostList: JList<*>? = null
    private var hostTextField: JTextField? = null
    private var tokenTextArea: JTextArea? = null

    private var saveButton: JButton? = null
    private var removeButton: JButton? = null
    private var hosts: MutableList<String?>? = null

    private var selectedGitSetting: TokenSetting? = null

    @Inject
    val settingManager: SettingManager? = null

    @Inject
    val actionContext: ActionContext? = null

    init {

        setContentPane(contentPane)
        isModal = true

        // call onCancel() when cross is clicked
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        // call onCancel() on ESCAPE
        contentPane!!.registerKeyboardAction({ onCancel() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

        hostList!!.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                //select host
                hostList?.selectedIndex?.let { selectHost(it) }
            }
        })

        saveButton!!.addActionListener { saveSetting() }
        removeButton!!.addActionListener { removeSetting() }

        setLocationRelativeTo(owner)
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()
        refreshHost()
    }

    private fun refreshHost() {
        val settings = settingManager!!.tokenSettings
        hosts = settings.stream()
                .map { setting -> setting.host }
                .toList()
                .toMutableList()
        hosts!!.add("-new-")
        val hostModel = DefaultComboBoxModel(hosts!!.toTypedArray())
        hostList?.model = hostModel
        hostList?.selectedIndex = 0
        selectHost(0)
    }

    private fun selectHost(index: Int) {
        val maxSelectionIndex = hosts!!.size - 1
        if (index == -1 || index == maxSelectionIndex) {
            selectedGitSetting = null

            hostTextField!!.text = "host"
            tokenTextArea!!.text = "private token"
        } else {
            val host = hosts!![index]
            selectedGitSetting = settingManager!!.getSetting(host)

            hostTextField!!.text = host
            tokenTextArea!!.text = selectedGitSetting!!.privateToken
        }
    }

    private fun saveSetting() {

        var host = hostTextField!!.text
        if (org.apache.commons.lang3.StringUtils.isBlank(host)) {
            Messages.showMessageDialog(this, "Host should not be empty",
                    "Error", Messages.getErrorIcon())
            return
        }
        host = host.trim { it <= ' ' }
        if (host.endsWith("/")) {
            host = host.substring(0, host.length - 1)
        }

        //TODO:check host with regex

        var token = tokenTextArea!!.text
        if (org.apache.commons.lang3.StringUtils.isBlank(token)) {
            Messages.showMessageDialog(this, "Token should not be empty",
                    "Error", Messages.getErrorIcon())
            return
        }
        token = token.trim { it <= ' ' }

        if (selectedGitSetting != null && selectedGitSetting!!.host != host) {
            selectedGitSetting!!.privateToken = null
            settingManager!!.saveSetting(selectedGitSetting!!)
        }

        val gitSetting = TokenSetting()
        gitSetting.host = host
        gitSetting.privateToken = token
        settingManager!!.saveSetting(gitSetting)

        refreshHost()
    }

    private fun removeSetting() {
        if (selectedGitSetting == null) {
            Messages.showMessageDialog(this, "No host be selected",
                    "Error", Messages.getErrorIcon())
            return
        }

        selectedGitSetting!!.privateToken = null
        settingManager!!.saveSetting(selectedGitSetting!!)
        refreshHost()
    }

    private fun onCancel() {
        dispose()
        actionContext!!.unHold()
    }
}
