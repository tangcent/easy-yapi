package com.itangcent.idea.plugin.json

import com.itangcent.mock.toUnixString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [Json5Formatter]
 */
internal class Json5FormatterTest : JsonFormatterTest() {

    override val formatClass get() = Json5Formatter::class

    @Test
    fun testFormat() {
        assertEquals(
            "{\n" +
                    "    \"string\": \"abc\", //a string\n" +
                    "    /**\n" +
                    "     * a int\n" +
                    "     * 1 :ONE\n" +
                    "     * 2 :TWO\n" +
                    "     */\n" +
                    "    \"int\": 1,\n" +
                    "    \"1\": \"int\",\n" +
                    "    \"null\": null,\n" +
                    "    \"array\": [\n" +
                    "        \"def\",\n" +
                    "        2,\n" +
                    "        {}\n" +
                    "    ],\n" +
                    "    \"list\": [ //list\n" +
                    "        \"ghi\",\n" +
                    "        3,\n" +
                    "        {\n" +
                    "            \"x\": 1,\n" +
                    "            \"2\": \"y\"\n" +
                    "        },\n" +
                    "        []\n" +
                    "    ],\n" +
                    "    /**\n" +
                    "     * map\n" +
                    "     * map\n" +
                    "     */\n" +
                    "    \"map\": {\n" +
                    "        /**\n" +
                    "         * The value of the x axis\n" +
                    "         * in map\n" +
                    "         */\n" +
                    "        \"x\": 1,\n" +
                    "        \"2\": \"y\",\n" +
                    "        \"empty\": []\n" +
                    "    },\n" +
                    "    \"any\": {},\n" +
                    "}",
            jsonFormatter.format(model).toUnixString()
        )
        assertEquals(
            "{ //test model\n" +
                    "    \"string\": \"abc\", //a string\n" +
                    "    /**\n" +
                    "     * a int\n" +
                    "     * 1 :ONE\n" +
                    "     * 2 :TWO\n" +
                    "     */\n" +
                    "    \"int\": 1,\n" +
                    "    \"1\": \"int\",\n" +
                    "    \"null\": null,\n" +
                    "    \"array\": [\n" +
                    "        \"def\",\n" +
                    "        2,\n" +
                    "        {}\n" +
                    "    ],\n" +
                    "    \"list\": [ //list\n" +
                    "        \"ghi\",\n" +
                    "        3,\n" +
                    "        {\n" +
                    "            \"x\": 1,\n" +
                    "            \"2\": \"y\"\n" +
                    "        },\n" +
                    "        []\n" +
                    "    ],\n" +
                    "    /**\n" +
                    "     * map\n" +
                    "     * map\n" +
                    "     */\n" +
                    "    \"map\": {\n" +
                    "        /**\n" +
                    "         * The value of the x axis\n" +
                    "         * in map\n" +
                    "         */\n" +
                    "        \"x\": 1,\n" +
                    "        \"2\": \"y\",\n" +
                    "        \"empty\": []\n" +
                    "    },\n" +
                    "    \"any\": {},\n" +
                    "}",
            jsonFormatter.format(model, "test model").toUnixString()
        )

        assertEquals(
            "/**\n" +
                    " * multi lines\n" +
                    " * comments\n" +
                    " */\n" +
                    "[\n" +
                    "    \"str\",\n" +
                    "    {}\n" +
                    "]",
            jsonFormatter.format(listOf<Any?>("str", Object()), "multi lines\ncomments").toUnixString()
        )
    }
}