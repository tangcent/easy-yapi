package com.itangcent.idea.psi

import com.google.inject.Singleton
import com.itangcent.common.spi.SetupAble
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.withProvider
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.spi.SpiSingleBeanProvider


@Singleton
class PsiClassHelperProvider : SpiSingleBeanProvider<PsiClassHelper>()

class PsiClassHelperProviderSupport : SetupAble {

    /**
     * Initializes and binds the interceptor to AIService implementations
     */
    override fun init() {
        ActionContext.addDefaultInject { builder ->
            builder.bind(PsiClassHelper::class) {
                it.withProvider(PsiClassHelperProvider::class)
            }
        }
    }
}