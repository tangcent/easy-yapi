package com.itangcent.utils

object EnumKit {
    /**
     * Safely converts a string to an enum constant, returning null if the name doesn't match any enum value
     * instead of throwing an IllegalArgumentException.
     *
     * @param name The name of the enum constant to return
     * @return The enum constant of the specified name, or null if not found
     */
    inline fun <reified T : Enum<T>> safeValueOf(name: String?): T? {
        if (name == null) return null
        return try {
            java.lang.Enum.valueOf(T::class.java, name)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}