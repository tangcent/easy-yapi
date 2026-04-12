package com.itangcent.easyapi.exporter.grpc

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.exporter.core.ApiClassRecognizer
import com.itangcent.easyapi.exporter.core.MetaAnnotationResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

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

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        // Step 1: Check rule engine override
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_GRPC, psiClass) == true) return true

        // Step 2: Check if class extends io.grpc.BindableService
        if (extendsBindableService(psiClass)) return true

        // Step 3: Check for @GrpcService or meta-annotations
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, GRPC_SERVICE_ANNOTATIONS)
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
