package com.itangcent.intellij.config

import com.google.inject.Inject
import com.intellij.openapi.module.Module
import com.itangcent.common.utils.ResourceUtils
import com.itangcent.common.utils.forceMkdirParent
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.utils.Initializable
import org.mockito.Mockito
import java.io.File
import kotlin.reflect.KClass

/**
 * Test case of [AutoSearchConfigReader]
 */
internal abstract class ConfigProviderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var configProvider: ConfigProvider

    protected abstract val configProviderClass: KClass<out ConfigProvider>

    override fun afterBind(actionContext: ActionContext) {
        super.afterBind(actionContext)
        //load configs from resource to tempDir as files in module
        for (file in loadConfigs()) {
            File("$tempDir/$file")
                .also { it.forceMkdirParent() }
                .also { it.createNewFile() }
                .writeBytes(ResourceUtils.findResource(file)!!.readBytes())
        }
        (configProvider as? Initializable)?.init()
    }

    protected open fun loadConfigs(): Array<String> {
        return arrayOf(
            "config/.easy.api.config",
            "config/.easy.api.yml",
            "config/.easy.api.yaml",
            "config/a/.easy.api.config",
            "config/a/.easy.api.yml",
            "config/a/.easy.api.yaml",
        )
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ConfigProvider::class) { it.with(this.configProviderClass) }

        //mock mockContextSwitchListener
        val mockModule = Mockito.mock(Module::class.java)
        Mockito.`when`(mockModule.moduleFilePath).thenReturn("$tempDir/config/a/a.iml")
        val mockContextSwitchListener = Mockito.mock(ContextSwitchListener::class.java)
        Mockito.`when`(mockContextSwitchListener.getModule()).thenReturn(mockModule)
        builder.bind(ContextSwitchListener::class.java) { it.toInstance(mockContextSwitchListener) }
    }

    fun resourceId(fileName: String): String {
        var dir = tempDir.toString().replace(File.separator, "/")
        if (!dir.startsWith('/')) {
            dir = "/$dir"
        }
        return "Resource:file:$dir/$fileName"
    }
}