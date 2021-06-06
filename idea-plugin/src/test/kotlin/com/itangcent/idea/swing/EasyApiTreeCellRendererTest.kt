package com.itangcent.idea.swing

import com.itangcent.idea.icons.EasyIcons
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Test case of [EasyApiTreeCellRenderer]
 */
internal class EasyApiTreeCellRendererTest {

    private lateinit var renderer: EasyApiTreeCellRenderer
    private lateinit var tree: JTree

    private lateinit var leafIcon: Icon
    private lateinit var openIcon: Icon
    private lateinit var closedIcon: Icon

    private val render = { value: Any, expanded: Boolean, leaf: Boolean ->
        renderer.getTreeCellRendererComponent(
            tree,
            value,
            true,
            expanded,
            leaf,
            0,
            false
        )
    }

    @BeforeEach
    fun setUp() {
        renderer = EasyApiTreeCellRenderer()
        tree = JTree()
        tree.font = Font("font", 20, 1)
        tree.background = Color.MAGENTA

        leafIcon = renderer.leafIcon
        openIcon = renderer.openIcon
        closedIcon = renderer.closedIcon
    }

    @Test
    fun getTreeCellRendererComponent() {
        assertSame(
            renderer,
            renderer.getTreeCellRendererComponent(
                tree, "value", false, false,
                false, 0, false
            )
        )
    }

    @Test
    fun getTreeCellRendererComponentWithString() {
        //value is "value"
        val value = RandomStringUtils.random(10)
        render(value, true, false)
        assertSame(openIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(value, false, false)
        assertSame(closedIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(value, true, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(value, false, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithTreeNodeString() {
        //value is DefaultMutableTreeNode("value")
        val value = RandomStringUtils.random(10)
        render(DefaultMutableTreeNode(value), true, false)
        assertSame(openIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(value), false, false)
        assertSame(closedIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(value), true, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(value), false, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithIconCustomizedIcon() {
        //value is IconCustomizedSupported(icon)
        val module = EasyIcons.Module!!
        render(IconCustomizedSupported(module), true, false)
        assertSame(module, renderer.icon)
        assertNull(renderer.toolTipText)

        render(IconCustomizedSupported(module), false, false)
        assertSame(module, renderer.icon)
        assertNull(renderer.toolTipText)

        render(IconCustomizedSupported(module), true, true)
        assertSame(module, renderer.icon)
        assertNull(renderer.toolTipText)

        render(IconCustomizedSupported(module), false, true)
        assertSame(module, renderer.icon)
        assertNull(renderer.toolTipText)

    }

    @Test
    fun getTreeCellRendererComponentWithIconCustomizedNull() {
        //value is IconCustomizedSupported(null)
        render(IconCustomizedSupported(null), true, false)
        assertSame(openIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(IconCustomizedSupported(null), false, false)
        assertSame(closedIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(IconCustomizedSupported(null), true, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(IconCustomizedSupported(null), false, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithTreeNodeIconCustomizedIcon() {
        //value is DefaultMutableTreeNode(IconCustomizedSupported(icon))
        val moduleGroup = EasyIcons.ModuleGroup!!
        render(DefaultMutableTreeNode(IconCustomizedSupported(moduleGroup)), true, false)
        assertSame(moduleGroup, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(IconCustomizedSupported(moduleGroup)), false, false)
        assertSame(moduleGroup, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(IconCustomizedSupported(moduleGroup)), true, true)
        assertSame(moduleGroup, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(IconCustomizedSupported(moduleGroup)), false, true)
        assertSame(moduleGroup, renderer.icon)
        assertNull(renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithTreeNodeIconCustomizedNull() {
        //value is DefaultMutableTreeNode(IconCustomizedSupported(null))
        render(DefaultMutableTreeNode(IconCustomizedSupported(null)), true, false)
        assertSame(openIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(IconCustomizedSupported(null)), false, false)
        assertSame(closedIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(IconCustomizedSupported(null)), true, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(DefaultMutableTreeNode(IconCustomizedSupported(null)), false, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

    }

    @Test
    fun getTreeCellRendererComponentWithErrorIconCustomized() {

        //value is ErrorIconCustomizedSupported
        render(ErrorIconCustomizedSupported(IllegalArgumentException()), true, false)
        assertSame(openIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(ErrorIconCustomizedSupported(IllegalStateException()), false, false)
        assertSame(closedIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(ErrorIconCustomizedSupported(IndexOutOfBoundsException()), true, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(ErrorIconCustomizedSupported(NullPointerException()), false, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithToolTipAbleSupported() {
        //value is ToolTipAbleSupported
        val toolTip = RandomStringUtils.random(10)
        render(ToolTipAbleSupported(toolTip), true, false)
        assertSame(openIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(ToolTipAbleSupported(toolTip), false, false)
        assertSame(closedIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(ToolTipAbleSupported(toolTip), true, true)
        assertSame(leafIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(ToolTipAbleSupported(toolTip), false, true)
        assertSame(leafIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithTreeNodeToolTipAble() {
        val toolTip = RandomStringUtils.random(10)
        //value is DefaultMutableTreeNode(ToolTipAbleSupported)
        render(DefaultMutableTreeNode(ToolTipAbleSupported(toolTip)), true, false)
        assertSame(openIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(DefaultMutableTreeNode(ToolTipAbleSupported(toolTip)), false, false)
        assertSame(closedIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(DefaultMutableTreeNode(ToolTipAbleSupported(toolTip)), true, true)
        assertSame(leafIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(DefaultMutableTreeNode(ToolTipAbleSupported(toolTip)), false, true)
        assertSame(leafIcon, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithErrorToolTipAble() {
        //value is ErrorToolTipAbleSupported
        render(ErrorToolTipAbleSupported(IllegalArgumentException()), true, false)
        assertSame(openIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(ErrorToolTipAbleSupported(IllegalStateException()), false, false)
        assertSame(closedIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(ErrorToolTipAbleSupported(IndexOutOfBoundsException()), true, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)

        render(ErrorToolTipAbleSupported(NullPointerException()), false, true)
        assertSame(leafIcon, renderer.icon)
        assertNull(renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithUltimateSupported() {
        //value is UltimateSupported
        val ok = EasyIcons.OK!!
        val toolTip = RandomStringUtils.random(10)
        render(UltimateSupported(ok, toolTip), true, false)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(UltimateSupported(ok, toolTip), false, false)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(UltimateSupported(ok, toolTip), true, true)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(UltimateSupported(ok, toolTip), false, true)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)
    }

    @Test
    fun getTreeCellRendererComponentWithTreeNodeUltimateSupported() {
        val ok = EasyIcons.OK!!
        val toolTip = RandomStringUtils.random(10)
        //value is DefaultMutableTreeNode(UltimateSupported)
        render(DefaultMutableTreeNode(UltimateSupported(ok, toolTip)), true, false)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(DefaultMutableTreeNode(UltimateSupported(ok, toolTip)), false, false)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(DefaultMutableTreeNode(UltimateSupported(ok, toolTip)), true, true)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)

        render(DefaultMutableTreeNode(UltimateSupported(ok, toolTip)), false, true)
        assertSame(ok, renderer.icon)
        assertSame(toolTip, renderer.toolTipText)
    }

    private class IconCustomizedSupported(private val icon: Icon?) : IconCustomized {
        override fun icon(): Icon? {
            return icon
        }
    }

    private class ErrorIconCustomizedSupported(private val e: Throwable) : IconCustomized {
        override fun icon(): Icon {
            throw e
        }
    }

    private class ToolTipAbleSupported(private val toolTip: String?) : ToolTipAble {
        override fun toolTip(): String? {
            return toolTip
        }
    }

    private class ErrorToolTipAbleSupported(private val e: Throwable) : ToolTipAble {
        override fun toolTip(): String {
            throw e
        }
    }

    private class UltimateSupported(private val icon: Icon, private val toolTip: String) : IconCustomized, ToolTipAble {

        override fun icon(): Icon {
            return icon
        }

        override fun toolTip(): String {
            return toolTip
        }
    }
}