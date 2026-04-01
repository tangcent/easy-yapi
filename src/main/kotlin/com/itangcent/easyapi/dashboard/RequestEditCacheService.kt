package com.itangcent.easyapi.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.util.storage.DbBeanBinder
import com.itangcent.easyapi.util.storage.SqliteDataResourceHelper

/**
 * Project-level service for caching user edits to API endpoint requests.
 * 
 * This service persists modifications made by users to endpoint parameters,
 * headers, body, and other request settings. The cached edits are restored
 * when the user revisits an endpoint, providing a seamless editing experience.
 * 
 * Storage is backed by SQLite for reliable persistence across IDE sessions.
 */
@Service(Service.Level.PROJECT)
class RequestEditCacheService(private val project: Project) {

    /** Lazy-initialized SQLite helper for data persistence */
    private val sqliteHelper: SqliteDataResourceHelper by lazy {
        val cacheFile = ProjectCacheRepository.getInstance(project).resolve("request_edits.db")
        SqliteDataResourceHelper(cacheFile)
    }

    /** Data binder for serializing/deserializing cache objects */
    private val beanBinder: DbBeanBinder<RequestEditCache> by lazy {
        DbBeanBinder(
            sqliteHelper,
            "request_edit",
            { GsonUtils.toJson(it) },
            { GsonUtils.fromJson(it) }
        )
    }

    /**
     * Saves the edit cache for an endpoint.
     * 
     * @param endpoint The API endpoint being edited
     * @param cache The cache object containing user modifications
     * @param key The unique cache key for the endpoint
     */
    fun save(endpoint: ApiEndpoint, cache: RequestEditCache, key: String) {
        if (key.isNotBlank()) {
            beanBinder.save(key, cache.copy(key = key))
        }
    }

    /**
     * Loads the cached edits for an endpoint.
     * 
     * @param endpoint The API endpoint to load cache for
     * @param key The unique cache key for the endpoint
     * @return The cached edits, or null if not found
     */
    fun load(endpoint: ApiEndpoint, key: String): RequestEditCache? {
        if (key.isBlank()) return null
        return beanBinder.load(key)
    }

    /**
     * Deletes the cached edits for an endpoint.
     * 
     * @param key The unique cache key for the endpoint
     */
    fun delete(key: String) {
        if (key.isNotBlank()) {
            beanBinder.delete(key)
        }
    }

    /**
     * Creates a default cache object from an endpoint's definition.
     * Extracts headers, parameters, and body from the endpoint model.
     * 
     * @param endpoint The API endpoint to create cache from
     * @param key The unique cache key for the endpoint
     * @param host Optional host URL to use
     * @return A new RequestEditCache with default values
     */
    fun createDefaultCache(endpoint: ApiEndpoint, key: String, host: String? = null): RequestEditCache {
        return RequestEditCache(
            key = key,
            name = endpoint.name,
            path = endpoint.path,
            method = endpoint.method.name,
            host = host,
            headers = endpoint.headers.map { EditableKeyValue(it.name, it.value ?: it.example ?: "", it.description) } +
                endpoint.parameters
                    .filter { it.binding == ParameterBinding.Header }
                    .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            pathParams = endpoint.parameters
                .filter { it.binding == ParameterBinding.Path }
                .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            queryParams = endpoint.parameters
                .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }
                .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            formParams = endpoint.parameters
                .filter { it.binding == ParameterBinding.Form }
                .map { EditableKeyValue(it.name, it.defaultValue ?: it.example ?: "", it.description) },
            body = endpoint.body?.let { com.itangcent.easyapi.psi.model.ObjectModelJsonConverter.toJson(it) },
            contentType = endpoint.contentType
        )
    }

    /**
     * Generates a cache key for an endpoint based on its source location.
     * 
     * @return A unique key in the format "fully.qualified.ClassName#methodName"
     */
    private fun ApiEndpoint.cacheKey(): String {
        val method = sourceMethod ?: return ""
        val cls = sourceClass ?: method.containingClass ?: return ""
        return fullNameOfMember(cls, method)
    }

    /**
     * Creates a fully qualified name for a class method.
     * 
     * @param psiClass The containing class
     * @param psiMethod The method
     * @return A unique identifier string
     */
    private fun fullNameOfMember(psiClass: PsiClass, psiMethod: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: ""
        val methodName = psiMethod.name
        return "$className#$methodName"
    }

    /**
     * Returns the service instance for the given project.
     */
    companion object {
        fun getInstance(project: Project): RequestEditCacheService =
            project.getService(RequestEditCacheService::class.java)
    }
}
