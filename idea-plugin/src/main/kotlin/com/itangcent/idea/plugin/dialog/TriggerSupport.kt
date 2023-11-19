package com.itangcent.idea.plugin.dialog

/**
 * @author tangcent
 */
class TriggerSupport {
    private var trigger: String? = null

    fun withTrigger(trigger: String, cancelIfTriggerNotMatch: Boolean = true, action: () -> Unit) {
        if (this.trigger != null && this.trigger != trigger) {
            if (cancelIfTriggerNotMatch) {
                return
            }
            action()
            return
        }
        this.trigger = trigger
        try {
            action()
        } finally {
            this.trigger = null
        }
    }

    fun getTrigger(): String? {
        return trigger
    }
}