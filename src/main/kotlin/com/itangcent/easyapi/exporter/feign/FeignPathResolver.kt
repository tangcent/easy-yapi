package com.itangcent.easyapi.exporter.feign

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.psi.helper.AnnotationHelper

/**
 * Information extracted from a @FeignClient annotation.
 *
 * @param path The path prefix for all methods
 * @param url The base URL for the client
 * @param name The service name
 */
data class FeignClientInfo(
    val path: String? = null,
    val url: String? = null,
    val name: String? = null
)

/**
 * Resolves path and configuration from @FeignClient annotations.
 *
 * Extracts the following attributes:
 * - `path` or `value` - Path prefix for all methods
 * - `url` - Base URL for the client
 * - `name` or `value` - Service name
 *
 * ## Example
 * ```java
 * @FeignClient(name = "user-service", path = "/api/v1")
 * interface UserClient {
 *     @GetMapping("/users")
 *     List<User> getUsers();
 * }
 * // Base path: /api/v1
 * ```
 *
 * @see FeignClassExporter for usage in export process
 */
class FeignPathResolver(
    private val annotationHelper: AnnotationHelper
) {
    suspend fun resolve(psiClass: PsiClass): FeignClientInfo {
        val ann = read {
            annotationHelper.findAnnMap(psiClass, "org.springframework.cloud.openfeign.FeignClient")
        }.orEmpty()

        if (ann.isEmpty()) {
            return FeignClientInfo()
        }
        val path = (ann["path"] ?: ann["value"])?.toString()?.takeIf { it.isNotBlank() }
        val url = ann["url"]?.toString()?.takeIf { it.isNotBlank() }
        val name = (ann["name"] ?: ann["value"])?.toString()?.takeIf { it.isNotBlank() }
        return FeignClientInfo(path = path, url = url, name = name)
    }
}