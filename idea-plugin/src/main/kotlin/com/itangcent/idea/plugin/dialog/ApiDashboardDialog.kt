package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ApiDashboardDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var projectApiTree: JTree? = null
    private var postmanApiTree: JTree? = null
    private var projectApiPanel: JPanel? = null
    private var postmanPanel: JPanel? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    var actionContext: ActionContext? = null

    @Inject
    var project: Project? = null

    init {
        setContentPane(contentPane)
        isModal = true
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        initProjectApiModule()
    }

    private fun initProjectApiModule() {

        actionContext!!.runInReadUI {

            val moduleManager = ModuleManager.getInstance(project!!)
            val treeNode = DefaultMutableTreeNode()
//            var moduleNodeMap: HashMap<Module, DefaultMutableTreeNode> = HashMap()
            val modules = moduleManager.sortedModules.reversed()

            for (module in modules) {
                treeNode.add(DefaultMutableTreeNode(ModuleApiNodeData(module)))
            }

            actionContext!!.runInSwingUI {
                projectApiTree!!.model = DefaultTreeModel(treeNode, true)
            }
        }
    }

    class ModuleApiNodeData {
        var module: Module

        constructor(module: Module) {
            this.module = module
        }

        override fun toString(): String {
            return module.name
        }
    }

}
