package com.itangcent.easyapi.framework.grpc

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.MetaAnnotationResolver
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine

/**
 * Recognizes gRPC service implementation classes.
 *
 * A class is considered a gRPC service if it:
 * - Extends [io.grpc.BindableService] directly or through a generated ImplBase superclass
 * - Is annotated with @GrpcService (from grpc-spring-boot-starter) or a meta-annotation thereof
 *
 * Supports meta-annotation resolution (e.g., a custom annotation annotated with @GrpcService).
 */
class GrpcServiceRecognizer(
    private val ruleEngine: RuleEngine? = null
) : ApiClassRecognizer {

    override val frameworkName: String = "gRPC"

    override val targetAnnotations: Set<String> = GRPC_SERVICE_ANNOTATIONS

    override val enabledByDefault: Boolean = true

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        // Step 1: Check rule engine override
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_GRPC, psiClass) == true) return true

        // Step 2: Check if class extends io.grpc.BindableService
        if (extendsBindableService(psiClass)) return true

        // Step 3: Check for @GrpcService or meta-annotations
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, GRPC_SERVICE_ANNOTATIONS)
    }

    /**
     * Per-class fast-path used by line-marker providers.
     *
     * Returns true if the class is recognized as a gRPC service *without*
     * consulting the rule engine. This preserves the pre-patch line-marker
     * behavior where rule-engine-driven `class.is.grpc = true` overrides did
     * NOT cause a class to be marked as a gRPC service by the line marker
     * (only by the recognizer/exporter pipeline via [isApiClass]).
     *
     * The logic here mirrors the pre-patch `looksLikeGrpcService` helper in
     * `ApiMethodLineMarkerProvider` — annotation check OR BindableService walk.
     */
    override fun matchesClass(psiClass: PsiClass): Boolean {
        if (MetaAnnotationResolver.hasMetaAnnotationSync(psiClass, GRPC_SERVICE_ANNOTATIONS)) return true
        return extendsBindableService(psiClass)
    }

    /**
     * Convenience method — equivalent to [isApiClass].
     */
    suspend fun isGrpcService(psiClass: PsiClass): Boolean = isApiClass(psiClass)

    companion object {
        // Only actual annotation types — BindableService is an interface and must NOT be here
        // (AnnotatedElementsSearch requires annotation types, not interfaces)
        val GRPC_SERVICE_ANNOTATIONS = setOf(
            "net.devh.boot.grpc.server.service.GrpcService"
        )

        const val BINDABLE_SERVICE_FQN = "io.grpc.BindableService"

        /**
         * Checks whether [psiClass] extends [io.grpc.BindableService] directly
         * or through a generated ImplBase superclass anywhere in the hierarchy.
         *
         * gRPC generated code: `XxxGrpc.XxxImplBase extends AbstractStub implements BindableService`
         * The ImplBase may not directly implement BindableService — it can be several levels up.
         * We walk the full supertype hierarchy to find it.
         */
        fun extendsBindableService(psiClass: PsiClass): Boolean {
            return readSync { walkSupers(psiClass, mutableSetOf()) }
        }

        private fun walkSupers(cls: PsiClass, visited: MutableSet<String>): Boolean {
            val fqn = cls.qualifiedName ?: cls.name ?: return false
            if (!visited.add(fqn)) return false // cycle guard
            for (superClass in cls.supers) {
                val superFqn = superClass.qualifiedName ?: continue
                if (superFqn == BINDABLE_SERVICE_FQN) return true
                // Also match any class whose name ends with "ImplBase" — these are always gRPC stubs
                if (superClass.name?.endsWith("ImplBase") == true) return true
                if (walkSupers(superClass, visited)) return true
            }
            return false
        }
    }
}
