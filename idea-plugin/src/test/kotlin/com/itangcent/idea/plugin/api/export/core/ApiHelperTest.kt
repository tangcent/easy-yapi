package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [ApiHelper]
 */
internal class ApiHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var apiHelper: ApiHelper

    @Inject
    private lateinit var duckHelper: DuckTypeHelper

    internal lateinit var nameCtrlPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.String::class)
        loadSource(java.lang.Integer::class)
        loadSource(java.lang.Long::class)
        loadSource(Collection::class)
        loadSource(Map::class)
        loadSource(List::class)
        loadSource(LinkedList::class)
        loadSource(LocalDate::class)
        loadSource(LocalDateTime::class)
        loadSource(HashMap::class)
        loadSource(java.lang.Deprecated::class)
        loadFile("annotation/Public.java")
        loadFile("constant/UserType.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("api/BaseController.java")
        nameCtrlPsiClass = loadClass("api/NameCtrl.java")!!
    }

    override fun customConfig(): String? {
        return super.customConfig() + "\n" +
                "api.name=#name\n" +
                "method.doc[@com.itangcent.common.annotation.Public]=public api"
    }

    fun testNameOfApi() {
        assertEquals("nothing", apiHelper.nameOfApi(nameCtrlPsiClass.methods[0]))
        assertEquals("say hello", apiHelper.nameOfApi(nameCtrlPsiClass.methods[1]))
        assertEquals("say hello", apiHelper.nameOfApi(nameCtrlPsiClass.methods[2]))
        assertEquals("say hello", apiHelper.nameOfApi(nameCtrlPsiClass.methods[3]))
    }

    fun testNameAndAttrOfApi() {
        assertEquals(
            "nothing" to null,
            apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[0])
        )
        assertEquals(
            "say hello" to "public api",
            apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[1])
        )
        assertEquals(
            "say hello" to "not update anything\n" +
                    "just say hello\n" +
                    "public api",
            apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[2])
        )
        assertEquals(
            "say hello" to "not update anything\n" +
                    "just say hello\n" +
                    "public api",
            apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[3])
        )

        apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[0],
            {
                assertEquals("nothing", it)
            }, {
                assertEquals("not update anything", it)
            })

        apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[1],
            {
                assertEquals("say hello", it)
            }, {
                assertEquals("public api", it)
            })

        apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[2],
            {
                assertEquals("say hello", it)
            }, {
                assertEquals(
                    "not update anything\n" +
                            "just say hello\n" +
                            "public api", it
                )
            })

        apiHelper.nameAndAttrOfApi(duckHelper.explicit(nameCtrlPsiClass).methods()[3],
            {
                assertEquals("say hello", it)
            }, {
                assertEquals("not update anything\n" +
                        "just say hello\n" +
                        "public api", it)
            })

        apiHelper.nameAndAttrOfApi(nameCtrlPsiClass.methods[0],
            {
                assertEquals("nothing", it)
            }, {
                assertEquals("not update anything", it)
            })
        apiHelper.nameAndAttrOfApi(nameCtrlPsiClass.methods[1],
            {
                assertEquals("say hello", it)
            }, {
                assertEquals("public api", it)
            })
        apiHelper.nameAndAttrOfApi(nameCtrlPsiClass.methods[2],
            {
                assertEquals("say hello", it)
            }, {
                assertEquals(
                    "not update anything\n" +
                            "just say hello\n" +
                            "public api", it
                )
            })
        apiHelper.nameAndAttrOfApi(nameCtrlPsiClass.methods[3],
            {
                assertEquals("say hello", it)
            }, {
                assertEquals("not update anything\n" +
                        "just say hello\n" +
                        "public api", it)
            })
    }
}