package com.itangcent.idea.swing

import sun.swing.DefaultLookup
import java.awt.*
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicGraphicsUtils
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

class EasyApiTreeCellRenderer : JLabel(), TreeCellRenderer {

    /** Last tree the renderer was painted in.  */
    private var tree: JTree? = null

    /** Is the value currently selected.  */
    protected var selected: Boolean = false
    /** True if has focus.  */
    protected var hasFocus: Boolean = false
    /** True if draws focus border around icon as well.  */
    private var drawsFocusBorderAroundIcon: Boolean = false
    /** If true, a dashed line is drawn as the focus indicator.  */
    private var drawDashedFocusIndicator: Boolean = false

    // If drawDashedFocusIndicator is true, the following are used.
    /**
     * Background color of the tree.
     */
    private var treeBGColor: Color? = null
    /**
     * Color to draw the focus indicator in, determined from the background.
     * color.
     */
    private var focusBGColor: Color? = null

    // Icons
    /** Icon used to show non-leaf nodes that aren't expanded.  */
    @Transient
    protected var _closedIcon: Icon? = null

    /** Icon used to show leaf nodes.  */
    @Transient
    protected var _leafIcon: Icon? = null

    /** Icon used to show non-leaf nodes that are expanded.  */
    @Transient
    protected var _openIcon: Icon? = null

    // Colors
    /** Color to use for the foreground for selected nodes.  */
    protected var _textSelectionColor: Color? = null

    /** Color to use for the foreground for non-selected nodes.  */
    protected var _textNonSelectionColor: Color? = null

    /** Color to use for the background when a node is selected.  */
    protected var _backgroundSelectionColor: Color? = null

    /** Color to use for the background when the node isn't selected.  */
    protected var _backgroundNonSelectionColor: Color? = null

    /** Color to use for the focus indicator when the node has focus.  */
    protected var _borderSelectionColor: Color? = null

    private var isDropCell: Boolean = false
    private var fillBackground: Boolean = false

    /**
     * Set to true after the constructor has run.
     */
    private var inited: Boolean = false

    /**
     * Creates a `DefaultTreeCellRenderer`. Icons and text color are
     * determined from the `UIManager`.
     */
    fun DefaultTreeCellRenderer() {
        inited = true
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.7
     */
    override fun updateUI() {
        super.updateUI()
        // To avoid invoking new methods from the constructor, the
        // inited field is first checked. If inited is false, the constructor
        // has not run and there is no point in checking the value. As
        // all look and feels have a non-null value for these properties,
        // a null value means the developer has specifically set it to
        // null. As such, if the value is null, this does not reset the
        // value.
        if (!inited || getLeafIcon() is UIResource) {
            setLeafIcon(DefaultLookup.getIcon(this, ui, "Tree.leafIcon"))
        }
        if (!inited || getClosedIcon() is UIResource) {
            setClosedIcon(DefaultLookup.getIcon(this, ui, "Tree.closedIcon"))
        }
        if (!inited || getOpenIcon() is UIManager) {
            setOpenIcon(DefaultLookup.getIcon(this, ui, "Tree.openIcon"))
        }
        if (!inited || getTextSelectionColor() is UIResource) {
            setTextSelectionColor(
                    DefaultLookup.getColor(this, ui, "Tree.selectionForeground"))
        }
        if (!inited || getTextNonSelectionColor() is UIResource) {
            setTextNonSelectionColor(
                    DefaultLookup.getColor(this, ui, "Tree.textForeground"))
        }
        if (!inited || getBackgroundSelectionColor() is UIResource) {
            setBackgroundSelectionColor(
                    DefaultLookup.getColor(this, ui, "Tree.selectionBackground"))
        }
        if (!inited || getBackgroundNonSelectionColor() is UIResource) {
            setBackgroundNonSelectionColor(
                    DefaultLookup.getColor(this, ui, "Tree.textBackground"))
        }
        if (!inited || getBorderSelectionColor() is UIResource) {
            setBorderSelectionColor(
                    DefaultLookup.getColor(this, ui, "Tree.selectionBorderColor"))
        }
        drawsFocusBorderAroundIcon = DefaultLookup.getBoolean(
                this, ui, "Tree.drawsFocusBorderAroundIcon", false)
        drawDashedFocusIndicator = DefaultLookup.getBoolean(
                this, ui, "Tree.drawDashedFocusIndicator", false)

        fillBackground = DefaultLookup.getBoolean(this, ui, "Tree.rendererFillBackground", true)
        val margins = DefaultLookup.getInsets(this, ui, "Tree.rendererMargins")
        if (margins != null) {
            border = EmptyBorder(margins.top, margins.left,
                    margins.bottom, margins.right)
        }

        name = "Tree.cellRenderer"
    }


    /**
     * Returns the default icon, for the current laf, that is used to
     * represent non-leaf nodes that are expanded.
     */
    fun getDefaultOpenIcon(): Icon {
        return DefaultLookup.getIcon(this, ui, "Tree.openIcon")
    }

    /**
     * Returns the default icon, for the current laf, that is used to
     * represent non-leaf nodes that are not expanded.
     */
    fun getDefaultClosedIcon(): Icon {
        return DefaultLookup.getIcon(this, ui, "Tree.closedIcon")
    }

    /**
     * Returns the default icon, for the current laf, that is used to
     * represent leaf nodes.
     */
    fun getDefaultLeafIcon(): Icon {
        return DefaultLookup.getIcon(this, ui, "Tree.leafIcon")
    }

    /**
     * Sets the icon used to represent non-leaf nodes that are expanded.
     */
    fun setOpenIcon(newIcon: Icon?) {
        if (newIcon != null) {
            _openIcon = newIcon
        }
    }

    /**
     * Returns the icon used to represent non-leaf nodes that are expanded.
     */
    fun getOpenIcon(): Icon? {
        return _openIcon
    }

    /**
     * Sets the icon used to represent non-leaf nodes that are not expanded.
     */
    fun setClosedIcon(newIcon: Icon?) {
        if (newIcon != null) {
            _closedIcon = newIcon
        }
    }

    /**
     * Returns the icon used to represent non-leaf nodes that are not
     * expanded.
     */
    fun getClosedIcon(): Icon? {
        return _closedIcon
    }

    /**
     * Sets the icon used to represent leaf nodes.
     */
    fun setLeafIcon(newIcon: Icon?) {
        if (newIcon != null) {
            _leafIcon = newIcon
        }
    }

    /**
     * Returns the icon used to represent leaf nodes.
     */
    fun getLeafIcon(): Icon? {
        return _leafIcon
    }

    /**
     * Sets the color the text is drawn with when the node is selected.
     */
    fun setTextSelectionColor(newColor: Color) {
        _textSelectionColor = newColor
    }

    /**
     * Returns the color the text is drawn with when the node is selected.
     */
    fun getTextSelectionColor(): Color? {
        return _textSelectionColor
    }

    /**
     * Sets the color the text is drawn with when the node isn't selected.
     */
    fun setTextNonSelectionColor(newColor: Color) {
        _textNonSelectionColor = newColor
    }

    /**
     * Returns the color the text is drawn with when the node isn't selected.
     */
    fun getTextNonSelectionColor(): Color? {
        return _textNonSelectionColor
    }

    /**
     * Sets the color to use for the background if node is selected.
     */
    fun setBackgroundSelectionColor(newColor: Color) {
        _backgroundSelectionColor = newColor
    }


    /**
     * Returns the color to use for the background if node is selected.
     */
    fun getBackgroundSelectionColor(): Color? {
        return _backgroundSelectionColor
    }

    /**
     * Sets the background color to be used for non selected nodes.
     */
    fun setBackgroundNonSelectionColor(newColor: Color) {
        _backgroundNonSelectionColor = newColor
    }

    /**
     * Returns the background color to be used for non selected nodes.
     */
    fun getBackgroundNonSelectionColor(): Color? {
        return _backgroundNonSelectionColor
    }

    /**
     * Sets the color to use for the border.
     */
    fun setBorderSelectionColor(newColor: Color) {
        _borderSelectionColor = newColor
    }

    /**
     * Returns the color the border is drawn.
     */
    fun getBorderSelectionColor(): Color? {
        return _borderSelectionColor
    }

    /**
     * Subclassed to map `FontUIResource`s to null. If
     * `font` is null, or a `FontUIResource`, this
     * has the effect of letting the font of the JTree show
     * through. On the other hand, if `font` is non-null, and not
     * a `FontUIResource`, the font becomes `font`.
     */
    override fun setFont(font: Font?) {
        var _font = font
        if (_font is FontUIResource)
            _font = null
        super.setFont(_font)
    }

    /**
     * Gets the font of this component.
     * @return this component's font; if a font has not been set
     * for this component, the font of its parent is returned
     */
    override fun getFont(): Font? {
        var font: Font? = super.getFont()

        if (font == null && tree != null) {
            // Strive to return a non-null value, otherwise the html support
            // will typically pick up the wrong font in certain situations.
            font = tree!!.font
        }
        return font
    }

    /**
     * Subclassed to map `ColorUIResource`s to null. If
     * `color` is null, or a `ColorUIResource`, this
     * has the effect of letting the background color of the JTree show
     * through. On the other hand, if `color` is non-null, and not
     * a `ColorUIResource`, the background becomes
     * `color`.
     */
    override fun setBackground(color: Color?) {
        var _color = color
        if (_color is ColorUIResource)
            _color = null
        super.setBackground(_color)
    }

    /**
     * Configures the renderer based on the passed in components.
     * The value is set from messaging the tree with
     * `convertValueToText`, which ultimately invokes
     * `toString` on `value`.
     * The foreground color is set based on the selection and the icon
     * is set based on the `leaf` and `expanded`
     * parameters.
     */
    override fun getTreeCellRendererComponent(tree: JTree, value: Any,
                                              sel: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean, row: Int,
                                              hasFocus: Boolean): Component {
        val stringValue = tree.convertValueToText(value, sel,
                expanded, leaf, row, hasFocus)

        this.tree = tree
        this.hasFocus = hasFocus
        text = stringValue

        val fg: Color?
        isDropCell = false

        val dropLocation = tree.dropLocation
        if (dropLocation != null
                && dropLocation.childIndex == -1
                && tree.getRowForPath(dropLocation.path) == row) {

            val col = DefaultLookup.getColor(this, ui, "Tree.dropCellForeground")
            if (col != null) {
                fg = col
            } else {
                fg = getTextSelectionColor()
            }

            isDropCell = true
        } else if (sel) {
            fg = getTextSelectionColor()
        } else {
            fg = getTextNonSelectionColor()
        }

        foreground = fg

        var icon: Icon? = findIcon(value, expanded, leaf)

        if (!tree.isEnabled) {
            isEnabled = false
            val laf = UIManager.getLookAndFeel()
            val disabledIcon = laf.getDisabledIcon(tree, icon)
            if (disabledIcon != null) icon = disabledIcon
            setDisabledIcon(icon)
        } else {
            isEnabled = true
            setIcon(icon)
        }

        findTooltips(value)?.let { toolTipText = it }

        componentOrientation = tree.componentOrientation

        selected = sel

        return this
    }


    private fun findIcon(value: Any,
                         expanded: Boolean,
                         leaf: Boolean): Icon? {

        var icon: Icon? = null

        if (value is DefaultMutableTreeNode) {
            val userObject = value.userObject
            if (userObject is IconCustomized) {
                icon = userObject.icon()
            }
        }

        if (icon == null && value is IconCustomized) {
            icon = value.icon()
        }

        if (icon == null) {
            icon = when {
                leaf -> getLeafIcon()
                expanded -> getOpenIcon()
                else -> getClosedIcon()
            }
        }

        return icon
    }

    private fun findTooltips(value: Any): String? {
        var tooltip: String? = null

        if (value is DefaultMutableTreeNode) {
            val userObject = value.userObject
            if (userObject is Tooltipable) {
                tooltip = userObject.toolTip()
            }
        }

        if (tooltip == null && value is Tooltipable) {
            tooltip = value.toolTip()
        }

        return tooltip
    }

    /**
     * Paints the value.  The background is filled based on selected.
     */
    override fun paint(g: Graphics?) {
        var bColor: Color?

        if (isDropCell) {
            bColor = DefaultLookup.getColor(this, ui, "Tree.dropCellBackground")
            if (bColor == null) {
                bColor = getBackgroundSelectionColor()
            }
        } else if (selected) {
            bColor = getBackgroundSelectionColor()
        } else {
            bColor = getBackgroundNonSelectionColor()
            if (bColor == null) {
                bColor = background
            }
        }

        var imageOffset = -1
        if (bColor != null && fillBackground) {
            imageOffset = getLabelStart()
            g!!.color = bColor
            if (componentOrientation.isLeftToRight) {
                g.fillRect(imageOffset, 0, width - imageOffset,
                        height)
            } else {
                g.fillRect(0, 0, width - imageOffset,
                        height)
            }
        }

        if (hasFocus) {
            if (drawsFocusBorderAroundIcon) {
                imageOffset = 0
            } else if (imageOffset == -1) {
                imageOffset = getLabelStart()
            }
            if (componentOrientation.isLeftToRight) {
                paintFocus(g!!, imageOffset, 0, width - imageOffset,
                        height, bColor)
            } else {
                paintFocus(g!!, 0, 0, width - imageOffset, height, bColor)
            }
        }
        super.paint(g)
    }

    private fun paintFocus(g: Graphics, x: Int, y: Int, w: Int, h: Int, notColor: Color?) {
        val bsColor = getBorderSelectionColor()

        if (bsColor != null && (selected || !drawDashedFocusIndicator)) {
            g.color = bsColor
            g.drawRect(x, y, w - 1, h - 1)
        }
        if (drawDashedFocusIndicator && notColor != null) {
            if (treeBGColor !== notColor) {
                treeBGColor = notColor
                focusBGColor = Color(notColor.rgb.inv())
            }
            g.color = focusBGColor
            BasicGraphicsUtils.drawDashedRect(g, x, y, w, h)
        }
    }

    private fun getLabelStart(): Int {
        val currentI = icon
        return if (currentI != null && text != null) {
            currentI.iconWidth + Math.max(0, iconTextGap - 1)
        } else 0
    }

    /**
     * Overrides `JComponent.getPreferredSize` to
     * return slightly wider preferred size value.
     */
    override fun getPreferredSize(): Dimension? {
        var retDimension: Dimension? = super.getPreferredSize()

        if (retDimension != null)
            retDimension = Dimension(retDimension.width + 3,
                    retDimension.height)
        return retDimension
    }

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun validate() {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     *
     * @since 1.5
     */
    override fun invalidate() {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun revalidate() {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun repaint(tm: Long, x: Int, y: Int, width: Int, height: Int) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun repaint(r: Rectangle) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     *
     * @since 1.5
     */
    override fun repaint() {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Any?, newValue: Any?) {
        // Strings get interned...
        if (propertyName === "text" || ((propertyName === "font" || propertyName === "foreground")
                        && oldValue !== newValue
                        && getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) != null)) {

            super.firePropertyChange(propertyName, oldValue, newValue)
        }
    }

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Byte, newValue: Byte) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Char, newValue: Char) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Short, newValue: Short) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Int, newValue: Int) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Long, newValue: Long) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Float, newValue: Float) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Double, newValue: Double) {}

    /**
     * Overridden for performance reasons.
     * See the [Implementation Note](#override)
     * for more information.
     */
    override fun firePropertyChange(propertyName: String, oldValue: Boolean, newValue: Boolean) {}
}