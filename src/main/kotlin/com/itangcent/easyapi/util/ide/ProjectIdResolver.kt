package com.itangcent.easyapi.util.ide

import com.intellij.psi.PsiClass

/**
 * Resolves Maven coordinates (groupId, artifactId, version) from a PSI class
 * using a specific build tool integration (Maven, Gradle, etc.).
 *
 * Implementations use reflection to access optional IntelliJ plugin APIs,
 * avoiding compile-time dependencies on plugins that may not be installed.
 */
interface ProjectIdResolver {

    /**
     * Checks whether the required plugin classes are available at runtime.
     * This is checked lazily once and cached to avoid repeated class loading.
     */
    val available: Boolean

    /**
     * Resolves Maven coordinates for the given PSI class.
     *
     * @param psiClass The class to resolve coordinates for
     * @return MavenIdData with coordinates, or null if not resolvable
     */
    fun resolve(psiClass: PsiClass): MavenIdData?
}
