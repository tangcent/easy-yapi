package com.itangcent.idea.utils

import com.itangcent.intellij.context.ActionContext
import java.awt.Component
import java.awt.Dialog
import java.awt.Toolkit
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.TableColumn
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath


object SwingUtils {

    fun focus(apiCallDialog: Dialog) {
        ActionContext.getContext()!!.runInSwingUI {
            apiCallDialog.requestFocus()
        }
    }

    fun expandOrCollapseNode(tree: JTree, expanded: Boolean) {
        val node = tree.model.root as DefaultMutableTreeNode
        expandOrCollapseNode(tree, node, expanded)
    }

    fun expandOrCollapseNode(tree: JTree, node: DefaultMutableTreeNode, expanded: Boolean) {

        for (treeNode in node.children()) {
            expandOrCollapseNode(tree, treeNode as DefaultMutableTreeNode, expanded)
        }

        if (!expanded && node.isRoot) {
            return
        }
        val path = TreePath(node.path)
        if (expanded) {
            tree.expandPath(path)
        } else {
            tree.collapsePath(path)
        }
    }

    fun underLine(component: JComponent) {
//        component.isOpaque = true
        component.border = BorderFactory.createMatteBorder(0, 0, 1, 0, component.foreground)
        component.background = component.parent.background
    }

    fun immersed(component: JComponent) {
//        component.isOpaque = true
        component.border = BorderFactory.createMatteBorder(0, 0, 0, 0, component.foreground)
        component.background = component.parent.background
    }

    fun centerWindow(component: Component) {
        val toolkit = Toolkit.getDefaultToolkit()
        val scmSize = toolkit.screenSize
        val width = component.width
        val height = component.height

        component.setLocation(scmSize.width / 2 - width / 2,
                scmSize.height / 2 - height / 2)
    }

}


fun JTable.findColumn(index: Int): TableColumn? {
    return this.getColumn(this.getColumnName(index))
}