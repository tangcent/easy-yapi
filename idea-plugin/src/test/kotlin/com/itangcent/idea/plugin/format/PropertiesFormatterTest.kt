package com.itangcent.idea.plugin.format

import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.mock.toUnixString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [PropertiesFormatter]
 */
internal class PropertiesFormatterTest : MessageFormatterTest() {

    override val formatClass get() = PropertiesFormatter::class

    @Test
    fun testFormat() {
        assertEquals(
            "#a string\n" +
                    "string=abc\n" +
                    "#a int\n" +
                    "#1 :ONE\n" +
                    "#2 :TWO\n" +
                    "int=1\n" +
                    "1=int\n" +
                    "null=\n" +
                    "array=def\n" +
                    "array=2\n" +
                    "#list\n" +
                    "\n" +
                    "list=ghi\n" +
                    "\n" +
                    "list=3\n" +
                    "\n" +
                    "list.x=1\n" +
                    "list.2=y\n" +
                    "\n" +
                    "#map\n" +
                    "#map\n" +
                    "#The value of the x axis\n" +
                    "#in map\n" +
                    "map.x=1\n" +
                    "map.2=y\n" +
                    "any=",
            messageFormatter.format(model).toUnixString()
        )
        assertEquals(
            "#test model\n" +
                    "#a string\n" +
                    "string=abc\n" +
                    "#a int\n" +
                    "#1 :ONE\n" +
                    "#2 :TWO\n" +
                    "int=1\n" +
                    "1=int\n" +
                    "null=\n" +
                    "array=def\n" +
                    "array=2\n" +
                    "#list\n" +
                    "\n" +
                    "list=ghi\n" +
                    "\n" +
                    "list=3\n" +
                    "\n" +
                    "list.x=1\n" +
                    "list.2=y\n" +
                    "\n" +
                    "#map\n" +
                    "#map\n" +
                    "#The value of the x axis\n" +
                    "#in map\n" +
                    "map.x=1\n" +
                    "map.2=y\n" +
                    "any=",
            messageFormatter.format(model, "test model").toUnixString()
        )

        assertEquals(
            "#multi lines\n" +
                    "#comments\n" +
                    "\n" +
                    "#!! unknown properties !!\n",
            messageFormatter.format(listOf<Any?>("str", Object()), "multi lines\ncomments").toUnixString()
        )
    }

    @Test
    fun testFormatWithPrefix() {
        assertEquals(
            "#a string\n" +
                    "prefix.string=abc\n" +
                    "#a int\n" +
                    "#1 :ONE\n" +
                    "#2 :TWO\n" +
                    "prefix.int=1\n" +
                    "prefix.1=int\n" +
                    "prefix.null=\n" +
                    "prefix.array=def\n" +
                    "prefix.array=2\n" +
                    "#list\n" +
                    "\n" +
                    "prefix.list=ghi\n" +
                    "\n" +
                    "prefix.list=3\n" +
                    "\n" +
                    "prefix.list.x=1\n" +
                    "prefix.list.2=y\n" +
                    "\n" +
                    "#map\n" +
                    "#map\n" +
                    "#The value of the x axis\n" +
                    "#in map\n" +
                    "prefix.map.x=1\n" +
                    "prefix.map.2=y\n" +
                    "prefix.any=",
            (messageFormatter as PropertiesFormatter).format(model, "prefix", null).toUnixString())
        assertEquals(
            "#test model\n" +
                    "#a string\n" +
                    "prefix.string=abc\n" +
                    "#a int\n" +
                    "#1 :ONE\n" +
                    "#2 :TWO\n" +
                    "prefix.int=1\n" +
                    "prefix.1=int\n" +
                    "prefix.null=\n" +
                    "prefix.array=def\n" +
                    "prefix.array=2\n" +
                    "#list\n" +
                    "\n" +
                    "prefix.list=ghi\n" +
                    "\n" +
                    "prefix.list=3\n" +
                    "\n" +
                    "prefix.list.x=1\n" +
                    "prefix.list.2=y\n" +
                    "\n" +
                    "#map\n" +
                    "#map\n" +
                    "#The value of the x axis\n" +
                    "#in map\n" +
                    "prefix.map.x=1\n" +
                    "prefix.map.2=y\n" +
                    "prefix.any=",
            (messageFormatter as PropertiesFormatter).format(model, "prefix", "test model").toUnixString())

        assertEquals(
            "#multi lines\n" +
                    "#comments\n" +
                    "\n" +
                    "prefix=str\n" +
                    "\n" +
                    "prefix=",
            (messageFormatter as PropertiesFormatter).format(listOf<Any?>("str", Object()),
                "prefix", "multi lines\ncomments").toUnixString()
        )
    }

    @Test
    fun testFormatWithPrefixInContext() {
        actionContext.cache(ClassExportRuleKeys.PROPERTIES_PREFIX.name(), "prefix")
        assertEquals(
            "#a string\n" +
                    "prefix.string=abc\n" +
                    "#a int\n" +
                    "#1 :ONE\n" +
                    "#2 :TWO\n" +
                    "prefix.int=1\n" +
                    "prefix.1=int\n" +
                    "prefix.null=\n" +
                    "prefix.array=def\n" +
                    "prefix.array=2\n" +
                    "#list\n" +
                    "\n" +
                    "prefix.list=ghi\n" +
                    "\n" +
                    "prefix.list=3\n" +
                    "\n" +
                    "prefix.list.x=1\n" +
                    "prefix.list.2=y\n" +
                    "\n" +
                    "#map\n" +
                    "#map\n" +
                    "#The value of the x axis\n" +
                    "#in map\n" +
                    "prefix.map.x=1\n" +
                    "prefix.map.2=y\n" +
                    "prefix.any=",
            messageFormatter.format(model).toUnixString()
        )
        assertEquals(
            "#test model\n" +
                    "#a string\n" +
                    "prefix.string=abc\n" +
                    "#a int\n" +
                    "#1 :ONE\n" +
                    "#2 :TWO\n" +
                    "prefix.int=1\n" +
                    "prefix.1=int\n" +
                    "prefix.null=\n" +
                    "prefix.array=def\n" +
                    "prefix.array=2\n" +
                    "#list\n" +
                    "\n" +
                    "prefix.list=ghi\n" +
                    "\n" +
                    "prefix.list=3\n" +
                    "\n" +
                    "prefix.list.x=1\n" +
                    "prefix.list.2=y\n" +
                    "\n" +
                    "#map\n" +
                    "#map\n" +
                    "#The value of the x axis\n" +
                    "#in map\n" +
                    "prefix.map.x=1\n" +
                    "prefix.map.2=y\n" +
                    "prefix.any=",
            messageFormatter.format(model, "test model").toUnixString()
        )

        assertEquals(
            "#multi lines\n" +
                    "#comments\n" +
                    "\n" +
                    "prefix=str\n" +
                    "\n" +
                    "prefix=",
            messageFormatter.format(listOf<Any?>("str", Object()), "multi lines\ncomments").toUnixString()
        )
    }
}