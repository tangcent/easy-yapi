package com.itangcent.demo

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.ResourceUtils
import com.itangcent.common.utils.appendln
import com.itangcent.idea.plugin.api.export.core.requestOnly
import com.itangcent.idea.plugin.api.export.yapi.YapiFormatter
import com.itangcent.idea.plugin.api.export.yapi.YapiSpringClassExporterBaseTest
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.mock.ImmutableSystemProvider
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.test.ResultLoader
import com.itangcent.test.TimeZoneKit.STANDARD_TIME
import org.junit.jupiter.api.condition.OS

/**
 * Test case of [javax.validation(grouped)]
 */
internal class ValidationTest : YapiSpringClassExporterBaseTest() {

    @Inject
    private lateinit var yapiFormatter: YapiFormatter

    internal lateinit var validationCtrlPsiClass: PsiClass

    internal lateinit var validationDemoDtoPsiClass: PsiClass

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
        loadSource(java.lang.Class::class)
        loadClass("validation/Validated.java")
        loadClass("validation/AssertFalse.java")
        loadClass("validation/AssertTrue.java")
        loadClass("validation/DecimalMax.java")
        loadClass("validation/DecimalMin.java")
        loadClass("validation/Digits.java")
        loadClass("validation/Email.java")
        loadClass("validation/Max.java")
        loadClass("validation/Min.java")
        loadClass("validation/Negative.java")
        loadClass("validation/NegativeOrZero.java")
        loadClass("validation/NotBlank.java")
        loadClass("validation/NotEmpty.java")
        loadClass("validation/NotNull.java")
        loadClass("validation/Null.java")
        loadClass("validation/Pattern.java")
        loadClass("validation/Positive.java")
        loadClass("validation/PositiveOrZero.java")
        loadClass("validation/Size.java")
        validationDemoDtoPsiClass = loadClass("model/ValidationDemoDto.java")!!
        validationCtrlPsiClass = loadClass("api/ValidationCtrl.java")!!
    }

    override fun customConfig(): String {
        return super.customConfig()
            .appendln(ResourceUtils.readResource("config/javax.validation.config"))!!
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(STANDARD_TIME))
        }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
    }

    override fun shouldRunTest(): Boolean {
        return !OS.WINDOWS.isCurrentOs
    }

    /**
     * use json-schema by default
     */
    fun testDoc2Item() {
        val requests = ArrayList<Request>()
        actionContext.withBoundary {
            classExporter.export(validationCtrlPsiClass, requestOnly {
                requests.add(it)
            })
        }
        assertEquals(
            ResultLoader.load(),
            GsonUtils.prettyJson(yapiFormatter.doc2Items(requests[0]))
        )
    }
}