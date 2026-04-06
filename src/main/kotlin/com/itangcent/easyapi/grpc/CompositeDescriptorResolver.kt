package com.itangcent.easyapi.grpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.logging.IdeaLog

/**
 * A composite [DescriptorResolver] that chains multiple resolver strategies in priority order.
 *
 * This is a project-level service that attempts to resolve gRPC method descriptors
 * using a fallback chain of resolvers:
 * 1. [ProtoFileResolver] - resolves from .proto files in the project
 * 2. [StubClassResolver] - resolves from generated stub classes via PSI analysis
 * 3. [ServerReflectionResolver] - resolves via gRPC server reflection at runtime
 *
 * The first resolver to successfully return a descriptor wins, allowing flexible
 * descriptor resolution across different project configurations.
 *
 * @see DescriptorResolver
 * @see ProtoFileResolver
 * @see StubClassResolver
 * @see ServerReflectionResolver
 */
@Service(Service.Level.PROJECT)
class CompositeDescriptorResolver(private val project: Project) : DescriptorResolver, IdeaLog {

    private val resolvers: List<DescriptorResolver> = listOf(
        ProtoFileResolver(project),
        StubClassResolver(project),
        ServerReflectionResolver()
    )

    override suspend fun resolve(
        classLoader: ClassLoader,
        serviceName: String,
        methodName: String,
        channel: Any?,
        sourceMethod: com.intellij.psi.PsiMethod?
    ): ResolvedDescriptor? {
        for ((index, resolver) in resolvers.withIndex()) {
            val resolverName = resolver.javaClass.simpleName
            LOG.info("Trying $resolverName for $serviceName/$methodName")
            val result = resolver.resolve(classLoader, serviceName, methodName, channel, sourceMethod)
            if (result != null) {
                LOG.info("Descriptor resolved via ${result.source} for $serviceName/$methodName")
                return result
            }
            LOG.info("$resolverName returned null for $serviceName/$methodName")
        }
        LOG.info("All descriptor resolvers exhausted for $serviceName/$methodName")
        return null
    }

    fun invalidateCache() {
        (resolvers.firstOrNull { it is ProtoFileResolver } as? ProtoFileResolver)?.invalidateCache()
    }

    companion object {
        fun getInstance(project: Project): CompositeDescriptorResolver = project.service()
    }
}
