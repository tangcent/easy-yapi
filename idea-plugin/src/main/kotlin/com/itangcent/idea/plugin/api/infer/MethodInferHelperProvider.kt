package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton

/**
 * Provider for MethodInferHelper that uses the factory to get the appropriate implementation
 */
@Singleton
class MethodInferHelperProvider : Provider<MethodInferHelper> {

    @Inject
    private lateinit var methodInferHelperFactory: MethodInferHelperFactory

    override fun get(): MethodInferHelper {
        return methodInferHelperFactory.getMethodInferHelper()
    }
} 