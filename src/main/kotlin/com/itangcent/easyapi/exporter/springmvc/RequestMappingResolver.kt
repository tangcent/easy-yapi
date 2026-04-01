package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiAnnotationOwner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves request mappings from Spring MVC annotations.
 *
 * This class handles:
 * - Standard annotations (@RequestMapping, @GetMapping, @PostMapping, etc.)
 * - Custom meta-annotations (custom annotations annotated with standard mapping annotations)
 * - Class-level and method-level path combination
 * - Rule-based path prefixes
 *
 * ## Usage
 * ```kotlin
 * val resolver = RequestMappingResolver(annotationHelper, ruleEngine)
 * val mappings = resolver.resolve(psiClass, psiMethod)
 * mappings.forEach { mapping ->
 *     println("${mapping.method} ${mapping.path}")
 * }
 * ```
 *
 * @param annotationHelper Helper for reading annotation values
 * @param ruleEngine Rule engine for evaluating path prefixes
 */
class RequestMappingResolver(
    private val annotationHelper: AnnotationHelper,
    private val ruleEngine: RuleEngine
) {
    /**
     * Cache for custom annotation resolution.
     * Key: annotation FQN, Value: the resolved meta-mapping info from the annotation type, or null if not a mapping annotation.
     */
    private val customMappingCache = ConcurrentHashMap<String, MappingInfo?>()

    suspend fun resolve(psiClass: PsiClass, method: PsiMethod): List<ResolvedMapping> {
        val classMappings = extractMapping(psiClass, null)
        val methodMappings = extractMapping(method, method)
        val classPrefix = ruleEngine.evaluate(RuleKeys.CLASS_PREFIX_PATH, psiClass).orEmpty()
        val endpointPrefix = ruleEngine.evaluate(RuleKeys.ENDPOINT_PREFIX_PATH, method).orEmpty()

        val classPaths = if (classMappings.paths.isEmpty()) listOf("") else classMappings.paths
        val methodPaths = if (methodMappings.paths.isEmpty()) listOf("") else methodMappings.paths

        val results = ArrayList<ResolvedMapping>()
        val methods = if (methodMappings.methods.isNotEmpty()) methodMappings.methods else classMappings.methods
        val httpMethods = if (methods.isNotEmpty()) methods else listOf(HttpMethod.NO_METHOD)

        for (cp in classPaths) {
            for (mp in methodPaths) {
                val base = joinPaths(joinPaths(classPrefix, cp), endpointPrefix)
                val path = normalizePath(joinPaths(base, mp))
                for (hm in httpMethods) {
                    results.add(
                        ResolvedMapping(
                            path = path,
                            method = hm,
                            consumes = methodMappings.consumes.ifEmpty { classMappings.consumes },
                            produces = methodMappings.produces.ifEmpty { classMappings.produces },
                            headers = (classMappings.headers + methodMappings.headers).distinctBy { it.first.lowercase() }
                        )
                    )
                }
            }
        }
        return results.distinctBy { it.method.name + ":" + it.path + ":" + it.consumes.joinToString(",") + ":" + it.produces.joinToString(",") }
    }

    private suspend fun extractMapping(target: Any, method: PsiMethod?): MappingInfo {
        val direct = when (target) {
            is PsiClass -> {
                mappingFromRequestMapping(target)
            }
            is PsiMethod -> {
                mappingFromMethod(target)
            }
            else -> MappingInfo.EMPTY
        }
        return direct
    }

    private suspend fun mappingFromMethod(psiMethod: PsiMethod): MappingInfo {
        mappingFromGetLike(psiMethod, "org.springframework.web.bind.annotation.GetMapping", HttpMethod.GET)?.let { return it }
        mappingFromGetLike(psiMethod, "org.springframework.web.bind.annotation.PostMapping", HttpMethod.POST)?.let { return it }
        mappingFromGetLike(psiMethod, "org.springframework.web.bind.annotation.PutMapping", HttpMethod.PUT)?.let { return it }
        mappingFromGetLike(psiMethod, "org.springframework.web.bind.annotation.DeleteMapping", HttpMethod.DELETE)?.let { return it }
        mappingFromGetLike(psiMethod, "org.springframework.web.bind.annotation.PatchMapping", HttpMethod.PATCH)?.let { return it }
        val rm = mappingFromRequestMapping(psiMethod)
        if (rm != MappingInfo.EMPTY) return rm
        // Fall back to custom meta-annotation resolution
        return mappingFromCustomAnnotation(psiMethod)
    }

    private suspend fun mappingFromGetLike(psiMethod: PsiMethod, ann: String, method: HttpMethod): MappingInfo? {
        if (!annotationHelper.hasAnn(psiMethod, ann)) return null
        val map = annotationHelper.findAnnMap(psiMethod, ann).orEmpty()
        val paths = readPaths(map)
        val consumes = readStringList(map, "consumes")
        val produces = readStringList(map, "produces")
        val headers = parseHeaderConstraints(readStringList(map, "headers"))
        return MappingInfo(paths = paths, methods = listOf(method), consumes = consumes, produces = produces, headers = headers)
    }

    private suspend fun mappingFromRequestMapping(target: Any): MappingInfo {
        val has = when (target) {
            is PsiClass -> annotationHelper.hasAnn(target, "org.springframework.web.bind.annotation.RequestMapping")
            is PsiMethod -> annotationHelper.hasAnn(target, "org.springframework.web.bind.annotation.RequestMapping")
            else -> false
        }
        if (!has) return MappingInfo.EMPTY
        val map = when (target) {
            is PsiClass -> annotationHelper.findAnnMap(target, "org.springframework.web.bind.annotation.RequestMapping").orEmpty()
            is PsiMethod -> annotationHelper.findAnnMap(target, "org.springframework.web.bind.annotation.RequestMapping").orEmpty()
            else -> emptyMap()
        }
        val paths = readPaths(map)
        val methods = readHttpMethods(map, "method")
        val consumes = readStringList(map, "consumes")
        val produces = readStringList(map, "produces")
        val headers = parseHeaderConstraints(readStringList(map, "headers"))
        return MappingInfo(paths = paths, methods = methods, consumes = consumes, produces = produces, headers = headers)
    }

    /**
     * Resolve custom annotations that are meta-annotated with standard Spring request mapping annotations.
     *
     * For example:
     * ```java
     * @Target(ElementType.METHOD)
     * @Retention(RetentionPolicy.RUNTIME)
     * @RequestMapping(method = RequestMethod.GET)
     * public @interface CustomGet {
     *     String value() default "";
     * }
     * ```
     *
     * This method resolves the annotation type, checks if it carries a standard Spring mapping
     * annotation as a meta-annotation, extracts the meta-data, and merges it with the direct
     * annotation attributes.
     */
    private suspend fun mappingFromCustomAnnotation(psiMethod: PsiMethod): MappingInfo {
        val annotations = (psiMethod as? PsiAnnotationOwner)?.annotations
            ?: (psiMethod as? PsiModifierListOwner)?.annotations
            ?: return MappingInfo.EMPTY

        for (ann in annotations) {
            val annFqn = ann.qualifiedName ?: continue
            // Skip standard Spring annotations (already handled)
            if (annFqn in STANDARD_MAPPING_ANNOTATIONS) continue

            // Check cache first
            val cached = customMappingCache[annFqn]
            if (cached != null) {
                // Found a cached custom mapping — merge with direct annotation attributes
                return mergeCustomMapping(cached, annotationHelper.findAnnMap(psiMethod, annFqn).orEmpty())
            }
            if (customMappingCache.containsKey(annFqn)) {
                // Cached as null = not a mapping annotation
                continue
            }

            // Resolve the annotation type and check for meta-annotations
            val annotationType = ann.nameReferenceElement?.resolve() as? PsiClass ?: continue
            val metaMapping = resolveMetaMappingFromAnnotationType(annotationType)
            customMappingCache[annFqn] = metaMapping

            if (metaMapping != null) {
                return mergeCustomMapping(metaMapping, annotationHelper.findAnnMap(psiMethod, annFqn).orEmpty())
            }
        }

        return MappingInfo.EMPTY
    }

    /**
     * Check if an annotation type class has standard Spring mapping annotations as meta-annotations.
     * Returns the MappingInfo extracted from the meta-annotation, or null.
     */
    private suspend fun resolveMetaMappingFromAnnotationType(annotationType: PsiClass): MappingInfo? {
        // Check for @RequestMapping on the annotation type
        val rmMap = annotationHelper.findAnnMap(annotationType, "org.springframework.web.bind.annotation.RequestMapping")
        if (rmMap != null) {
            return MappingInfo(
                paths = readPaths(rmMap),
                methods = readHttpMethods(rmMap, "method"),
                consumes = readStringList(rmMap, "consumes"),
                produces = readStringList(rmMap, "produces"),
                headers = parseHeaderConstraints(readStringList(rmMap, "headers"))
            )
        }

        // Check for shortcut annotations (@GetMapping, @PostMapping, etc.)
        for ((ann, method) in SHORTCUT_MAPPING_ANNOTATIONS) {
            val map = annotationHelper.findAnnMap(annotationType, ann)
            if (map != null) {
                return MappingInfo(
                    paths = readPaths(map),
                    methods = listOf(method),
                    consumes = readStringList(map, "consumes"),
                    produces = readStringList(map, "produces"),
                    headers = parseHeaderConstraints(readStringList(map, "headers"))
                )
            }
        }

        return null
    }

    /**
     * Merge meta-annotation data with direct annotation attributes.
     * Direct attributes override meta-annotation defaults (like the legacy BridgeSpringRequestMappingResolver).
     */
    private fun mergeCustomMapping(metaMapping: MappingInfo, directAttrs: Map<String, Any?>): MappingInfo {
        val directPaths = readPaths(directAttrs)
        val directMethods = readHttpMethods(directAttrs, "method")
        val directConsumes = readStringList(directAttrs, "consumes")
        val directProduces = readStringList(directAttrs, "produces")
        val directHeaders = parseHeaderConstraints(readStringList(directAttrs, "headers"))

        return MappingInfo(
            paths = directPaths.ifEmpty { metaMapping.paths },
            methods = directMethods.ifEmpty { metaMapping.methods },
            consumes = directConsumes.ifEmpty { metaMapping.consumes },
            produces = directProduces.ifEmpty { metaMapping.produces },
            headers = (metaMapping.headers + directHeaders).distinctBy { it.first.lowercase() }
        )
    }

    private fun readPaths(map: Map<String, Any?>): List<String> {
        val v = map["path"] ?: map["value"]
        return when (v) {
            is String -> listOf(v)
            is List<*> -> v.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun readStringList(map: Map<String, Any?>, key: String): List<String> {
        val v = map[key]
        return when (v) {
            is String -> listOf(v)
            is List<*> -> v.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun readHttpMethods(map: Map<String, Any?>, key: String): List<HttpMethod> {
        val v = map[key] ?: return emptyList()
        return when (v) {
            is String -> listOfNotNull(HttpMethod.fromSpring(v.trim()))
            is List<*> -> v.mapNotNull {
                when (it) {
                    is String -> HttpMethod.fromSpring(it.trim())
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Parse header constraints from @RequestMapping(headers = {...}).
     * Supports "name=value" format. Skips negation ("!name", "name!=value").
     */
    private fun parseHeaderConstraints(headerStrings: List<String>): List<Pair<String, String>> {
        return headerStrings.mapNotNull { h ->
            when {
                h.startsWith("!") -> null
                h.contains("!=") -> null
                h.contains("=") -> {
                    val name = h.substringBefore("=").trim()
                    val value = h.substringAfter("=").trim()
                    name to value
                }
                else -> h.trim() to ""
            }
        }
    }

    private fun joinPaths(vararg parts: String): String {
        return parts.filter { it.isNotEmpty() }.joinToString("/") { it.trim().trim('/') }
    }

    private fun normalizePath(path: String): String {
        return "/" + path.trim('/')
    }

    companion object {
        private val STANDARD_MAPPING_ANNOTATIONS = setOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
        )

        private val SHORTCUT_MAPPING_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.GetMapping" to HttpMethod.GET,
            "org.springframework.web.bind.annotation.PostMapping" to HttpMethod.POST,
            "org.springframework.web.bind.annotation.PutMapping" to HttpMethod.PUT,
            "org.springframework.web.bind.annotation.DeleteMapping" to HttpMethod.DELETE,
            "org.springframework.web.bind.annotation.PatchMapping" to HttpMethod.PATCH
        )
    }
}