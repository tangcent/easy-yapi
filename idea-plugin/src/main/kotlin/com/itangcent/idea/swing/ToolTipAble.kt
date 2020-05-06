package com.itangcent.idea.swing

/**
 * A component which has the text to display in a tool tip.
 */
interface ToolTipAble {

    /**
     * The text to display in a tool tip.
     * The text displays when the cursor lingers over the component.
     * If the text is [null],the tool tip is turned off for this component
     */
    fun toolTip(): String?
}

/**
 * For compatible only.
 */
typealias Tooltipable = ToolTipAble