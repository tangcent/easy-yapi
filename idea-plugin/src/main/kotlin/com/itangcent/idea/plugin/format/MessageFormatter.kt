package com.itangcent.idea.plugin.format

/**
 * Strategy interface that specifies a converter that can convert an object to a message that can be output.
 *
 * @author tangcent
 */
interface MessageFormatter {

    /**
     * Write an given object as a message.
     *
     * @param obj the object to write.
     * @param desc description of the object
     */
    fun format(obj: Any?, desc: String? = null): String
}