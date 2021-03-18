package com.itangcent.idea.utils

import com.itangcent.common.model.*
import com.itangcent.common.utils.Extensible
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Test case of [com.itangcent.idea.utils.ExtensibleKit]
 */
internal class ExtensibleKitKtTest {

    @ParameterizedTest
    @ValueSource(classes = [Doc::class, FormParam::class,
        Header::class, MethodDoc::class,
        Param::class, PathParam::class,
        Request::class, Response::class
    ])
    fun testSetExts(cls: Class<Extensible>) {
        val extensible = cls.newInstance() as Extensible//{}
        extensible.setExts(mapOf("a" to 1, "b" to 2))
        kotlin.test.assertEquals(mapOf("a" to 1, "b" to 2), extensible.exts())
    }

}