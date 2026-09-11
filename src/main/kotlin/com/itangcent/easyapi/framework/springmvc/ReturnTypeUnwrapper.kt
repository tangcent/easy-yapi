package com.itangcent.easyapi.framework.springmvc

import com.intellij.psi.PsiType
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.psi.type.TypeResolver

/**
 * Unwraps reactive and wrapper types from method return types.
 *
 * Spring MVC controllers often return wrapper types like:
 * - `ResponseEntity<T>` → unwrap to T
 * - `Mono<T>` → unwrap to T (reactive)
 * - `Flux<T>` → unwrap to `T[]` (reactive stream)
 * - `Optional<T>` → unwrap to T
 *
 * This utility extracts the inner type for API documentation purposes.
 *
 * A second, `PsiType`-based variant (`unwrapPsiType`) used to live here. It had no production
 * caller and disagreed with [unwrap] on `Flux` — returning the element type instead of an
 * array — so callers had two different answers for the same question. It was removed rather
 * than aligned, since [unwrap] is the one on the export path.
 */
object ReturnTypeUnwrapper {
    fun unwrap(type: PsiType?): ResolvedType {
        if (type == null) return ResolvedType.UnresolvedType("null")
        val resolved = TypeResolver.resolve(type)
        return unwrapResolved(resolved)
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
