package com.itangcent.idea.utils

import com.itangcent.intellij.context.ActionContext
import java.awt.Component
import java.awt.Dialog
import java.awt.Toolkit
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.TableColumn
import javax.swing.tree.*


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

        component.setLocation(
            scmSize.width / 2 - width / 2,
            scmSize.height / 2 - height / 2
        )
    }

}

fun MouseEvent?.isDoubleClick(): Boolean {
    if (this == null || this.isConsumed) return false

    if (this.isPopupTrigger) return false
    if (this.clickCount == 1 && this.isControlDown) {
        return true
    } else if (this.clickCount == 2 && this.button == MouseEvent.BUTTON1) {
        return true
    }
    return false
}

fun JTable.findColumn(index: Int): TableColumn? {
    return this.getColumn(this.getColumnName(index))
}

fun TreeModel.reload() {
    (this as? DefaultTreeModel)?.reload()
}

fun TreeModel.reload(node: TreeNode) {
    (this as? DefaultTreeModel)?.reload(node)
}

fun TreeModel.clear(node: TreeNode) {
    if (node !is DefaultMutableTreeNode) return
    if (node.childCount > 0) {
        node.removeAllChildren()
        this.reload(node)
    }
}

fun TreeModel.clear() {
    (this.root as? TreeNode)?.let { this.clear(it) }
}

fun TreeModel.remove(node: TreeNode) {
    if (node !is DefaultMutableTreeNode) return
    node.removeFromParent()
    this.reload(node)
}
