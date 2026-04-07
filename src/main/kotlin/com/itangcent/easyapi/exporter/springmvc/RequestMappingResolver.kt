package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiAnnotationOwner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.type.ResolvedMethod
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.allSuperClasses
import com.itangcent.easyapi.psi.type.superMethods
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
     * Key: annotation FQN, Value: the resolved meta-mapping info from the annotation type.
     * Uses NOT_A_MAPPING_ANNOTATION sentinel to cache "checked but not a mapping annotation".
     */
    private val customMappingCache = ConcurrentHashMap<String, MappingInfo>()

    /**
     * Resolves request mappings for a [ResolvedMethod].
     *
     * This is the preferred entry point for exporters using the ResolvedType API.
     * It tries the method itself first, then walks [ResolvedMethod.superMethods] until
     * a mapping is found. Class-level mappings are resolved from the [ResolvedMethod.ownerClassType]
     * and its supertypes via [ResolvedType.ClassType.superClasses].
     *
     * This correctly handles all inheritance cases including custom meta-annotations
     * on super methods and class-level @RequestMapping on supertypes.
     *
     * @param resolvedMethod The resolved method to find mappings for
     * @return The resolved mappings, or empty if no mapping found in the chain
     */
    suspend fun resolve(resolvedMethod: ResolvedMethod): List<ResolvedMapping> {
        val classType = resolvedMethod.ownerClassType
            ?: return emptyList()
        val psiClass = classType.psiClass

        // Resolve method-level mapping: try the method itself, then walk super methods
        val methodMappings = extractMethodMapping(resolvedMethod)

        // If no method-level mapping found at all, skip (no endpoint to export)
        if (methodMappings == MappingInfo.EMPTY) return emptyList()

        // Resolve class-level mapping: try the class itself, then walk supertypes
        val classMappings = extractClassMapping(classType)

        val method = resolvedMethod.psiMethod
        val classPrefix = ruleEngine.evaluate(RuleKeys.CLASS_PREFIX_PATH, psiClass).orEmpty()
        val endpointPrefix = ruleEngine.evaluate(RuleKeys.ENDPOINT_PREFIX_PATH, method).orEmpty()

        val classPaths = classMappings.paths.ifEmpty { listOf("") }
        val methodPaths = methodMappings.paths.ifEmpty { listOf("") }

        val results = ArrayList<ResolvedMapping>()
        val methods = methodMappings.methods.ifEmpty { classMappings.methods }
        val httpMethods = methods.ifEmpty { listOf(HttpMethod.NO_METHOD) }

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
        return results.distinctBy {
            it.method.name + ":" + it.path + ":" + it.consumes.joinToString(",") + ":" + it.produces.joinToString(
                ","
            )
        }
    }

    /**
     * Extracts class-level mapping from the ClassType, walking supertypes if needed.
     */
    private suspend fun extractClassMapping(classType: ResolvedType.ClassType): MappingInfo {
        val mapping = mappingFromRequestMapping(classType.psiClass)
        if (mapping != MappingInfo.EMPTY) return mapping
        for (superClassType in classType.allSuperClasses()) {
            val superMapping = mappingFromRequestMapping(superClassType.psiClass)
            if (superMapping != MappingInfo.EMPTY) return superMapping
        }
        return MappingInfo.EMPTY
    }

    /**
     * Extracts method-level mapping from the ResolvedMethod, walking super methods if needed.
     * Supports standard annotations, @RequestMapping, and custom meta-annotations.
     */
    private suspend fun extractMethodMapping(resolvedMethod: ResolvedMethod): MappingInfo {
        val mapping = mappingFromMethod(resolvedMethod.psiMethod)
        if (mapping != MappingInfo.EMPTY) return mapping
        for (superMethod in resolvedMethod.superMethods()) {
            val superMapping = mappingFromMethod(superMethod.psiMethod)
            if (superMapping != MappingInfo.EMPTY) return superMapping
        }
        return MappingInfo.EMPTY
    }

    private suspend fun mappingFromMethod(psiMethod: PsiMethod): MappingInfo {
        for ((ann, method) in SHORTCUT_MAPPING_ANNOTATIONS) {
            mappingFromGetLike(psiMethod, ann, method)?.let { return it }
        }
        val rm = mappingFromRequestMapping(psiMethod)
        if (rm != MappingInfo.EMPTY) return rm
        return mappingFromCustomAnnotation(psiMethod)
    }

    private suspend fun mappingFromGetLike(psiMethod: PsiMethod, ann: String, method: HttpMethod): MappingInfo? {
        if (!annotationHelper.hasAnn(psiMethod, ann)) return null
        val map = annotationHelper.findAnnMap(psiMethod, ann).orEmpty()
        val paths = readPaths(map)
        val consumes = readStringList(map, "consumes")
        val produces = readStringList(map, "produces")
        val headers = parseHeaderConstraints(readStringList(map, "headers"))
        return MappingInfo(
            paths = paths,
            methods = listOf(method),
            consumes = consumes,
            produces = produces,
            headers = headers
        )
    }

    private suspend fun mappingFromRequestMapping(target: Any): MappingInfo {
        val has = when (target) {
            is PsiClass -> annotationHelper.hasAnn(target, "org.springframework.web.bind.annotation.RequestMapping")
            is PsiMethod -> annotationHelper.hasAnn(target, "org.springframework.web.bind.annotation.RequestMapping")
            else -> false
        }
        if (!has) return MappingInfo.EMPTY
        val map = when (target) {
            is PsiClass -> annotationHelper.findAnnMap(target, "org.springframework.web.bind.annotation.RequestMapping")
                .orEmpty()

            is PsiMethod -> annotationHelper.findAnnMap(
                target,
                "org.springframework.web.bind.annotation.RequestMapping"
            ).orEmpty()

            else -> emptyMap()
        }
        val paths = readPaths(map)
        val methods = readHttpMethods(map, "method")
        val consumes = readStringList(map, "consumes")
        val produces = readStringList(map, "produces")
        val headers = parseHeaderConstraints(readStringList(map, "headers"))
        return MappingInfo(
            paths = paths,
            methods = methods,
            consumes = consumes,
            produces = produces,
            headers = headers
        )
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
                // Found a cached result
                if (cached === NOT_A_MAPPING_ANNOTATION) {
                    // Cached as "not a mapping annotation"
                    continue
                }
                // Found a cached custom mapping — merge with direct annotation attributes
                return mergeCustomMapping(cached, annotationHelper.findAnnMap(psiMethod, annFqn).orEmpty())
            }

            // Resolve the annotation type and check for meta-annotations
            val annotationType = ann.nameReferenceElement?.resolve() as? PsiClass ?: continue
            val metaMapping = resolveMetaMappingFromAnnotationType(annotationType)

            if (metaMapping != null) {
                customMappingCache[annFqn] = metaMapping
                return mergeCustomMapping(metaMapping, annotationHelper.findAnnMap(psiMethod, annFqn).orEmpty())
            } else {
                // Cache that this annotation is NOT a mapping annotation
                customMappingCache[annFqn] = NOT_A_MAPPING_ANNOTATION
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
        val rmMap =
            annotationHelper.findAnnMap(annotationType, "org.springframework.web.bind.annotation.RequestMapping")
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

        private val NOT_A_MAPPING_ANNOTATION = MappingInfo(
            paths = listOf("__NOT_A_MAPPING_ANNOTATION__"),
            methods = emptyList(),
            consumes = emptyList(),
            produces = emptyList(),
            headers = emptyList()
        )
    }
}