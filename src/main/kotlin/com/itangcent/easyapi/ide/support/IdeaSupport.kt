package com.itangcent.easyapi.ide.support

import com.intellij.openapi.application.ApplicationInfo

/**
 * Utilities for accessing IDE version information.
 *
 * Used for version-specific behavior and compatibility checks.
 */
object IdeaSupport {
    /**
     * Returns the full IDE version string (e.g., "2023.1.3").
     */
    fun ideVersion(): String = ApplicationInfo.getInstance().fullVersion

    /**
     * Returns the IDE build number.
     */
    fun buildNumber(): String = ApplicationInfo.getInstance().build.asString()

    /**
     * Checks if the IDE version starts with the given prefix.
     *
     * @param prefix The version prefix to check (e.g., "2023")
     * @return true if the version starts with the prefix
     */
    fun isVersionAtLeast(prefix: String): Boolean {
        return ideVersion().startsWith(prefix)
    }
}

