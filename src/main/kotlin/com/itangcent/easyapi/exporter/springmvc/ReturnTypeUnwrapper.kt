package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiType
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.TypeResolver

/**
 * Unwraps reactive and wrapper types from method return types.
 *
 * Spring MVC controllers often return wrapper types like:
 * - `ResponseEntity<T>` → unwrap to T
 * - `Mono<T>` → unwrap to T (reactive)
 * - `Flux<T>` → unwrap to T[] (reactive stream)
 * - `Optional<T>` → unwrap to T
 *
 * This utility extracts the inner type for API documentation purposes.
 */
object ReturnTypeUnwrapper {
    fun unwrap(type: PsiType?): ResolvedType {
        if (type == null) return ResolvedType.UnresolvedType("null")
        val resolved = TypeResolver.resolve(type)
        return unwrapResolved(resolved)
    }

    /**
     * Unwrap wrapper types (ResponseEntity, Mono, Optional, etc.) and return the inner PsiType.
     * This preserves the PsiType for building ObjectModel from the return type.
     */
    fun unwrapPsiType(type: PsiType?): PsiType? {
        if (type == null) return null
        val classType = type as? com.intellij.psi.PsiClassType ?: return type
        val psiClass = classType.resolve() ?: return type
        val qn = psiClass.qualifiedName ?: return type
        val args = classType.parameters
        if (args.isEmpty()) return type
        return when (qn) {
            "org.springframework.http.ResponseEntity" -> unwrapPsiType(args[0])
            "reactor.core.publisher.Mono" -> unwrapPsiType(args[0])
            "java.util.Optional" -> unwrapPsiType(args[0])
            "reactor.core.publisher.Flux" -> {
                // Flux<T> -> T[] conceptually, but we return the inner type
                // The caller should handle array wrapping if needed
                unwrapPsiType(args[0])
            }
            else -> type
        }
    }

    private fun unwrapResolved(type: ResolvedType): ResolvedType {
        if (type !is ResolvedType.ClassType) return type
        val qn = type.psiClass.qualifiedName ?: return type
        val args = type.typeArgs
        if (args.isEmpty()) return type
        return when (qn) {
            "org.springframework.http.ResponseEntity" -> unwrapResolved(args[0])
            "reactor.core.publisher.Mono" -> unwrapResolved(args[0])
            "reactor.core.publisher.Flux" -> ResolvedType.ArrayType(unwrapResolved(args[0]))
            "java.util.Optional" -> unwrapResolved(args[0])
            else -> type
        }
    }
}
