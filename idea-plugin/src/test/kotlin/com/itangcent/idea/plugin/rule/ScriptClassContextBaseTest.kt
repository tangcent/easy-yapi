package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptClassContext
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Base test case of [ScriptClassContext]
 */
abstract class ScriptClassContextBaseTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    internal lateinit var ruleParser: RuleParser

    internal lateinit var objectPsiClass: PsiClass
    internal lateinit var integerPsiClass: PsiClass
    internal lateinit var longPsiClass: PsiClass
    internal lateinit var collectionPsiClass: PsiClass
    internal lateinit var listPsiClass: PsiClass
    internal lateinit var mapPsiClass: PsiClass
    internal lateinit var hashMapPsiClass: PsiClass
    internal lateinit var modelPsiClass: PsiClass
    internal lateinit var resultPsiClass: PsiClass
    internal lateinit var iResultPsiClass: PsiClass
    internal lateinit var userCtrlPsiClass: PsiClass
    internal lateinit var commentDemoPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        objectPsiClass = loadSource(Object::class.java)!!
        loadSource(String::class.java)!!
        integerPsiClass = loadSource(java.lang.Integer::class)!!
        longPsiClass = loadSource(java.lang.Long::class)!!
        collectionPsiClass = loadSource(Collection::class.java)!!
        listPsiClass = loadSource(List::class.java)!!
        mapPsiClass = loadSource(Map::class.java)!!
        hashMapPsiClass = loadSource(HashMap::class.java)!!
        loadSource(LocalDate::class)
        loadSource(LocalDateTime::class)
        modelPsiClass = loadClass("model/Model.java")!!
        iResultPsiClass = loadClass("model/IResult.java")!!
        resultPsiClass = loadClass("model/Result.java")!!
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RestController.java")
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!
        commentDemoPsiClass = loadClass("model/CommentDemo.java")!!
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(RuleParser::class) { it.with(GroovyRuleParser::class) }
    }

    open fun PsiClass.asClassContext(): ScriptClassContext {
        return ruleParser.contextOf(this, this) as ScriptClassContext
    }

    open fun PsiClass.asScriptContext(): ScriptRuleParser.BaseScriptRuleContext {
        return ruleParser.contextOf(this, this) as ScriptRuleParser.BaseScriptRuleContext
    }

    //region tests of BaseScriptRuleContext

    fun testString() {
        assertEquals("java.lang.Object", objectPsiClass.asScriptContext().toString())
        assertEquals("java.lang.Integer", integerPsiClass.asScriptContext().toString())
        assertEquals("java.util.Collection", collectionPsiClass.asScriptContext().toString())
        assertEquals("com.itangcent.model.Model", modelPsiClass.asScriptContext().toString())
        assertEquals("com.itangcent.model.Result", resultPsiClass.asScriptContext().toString())
    }

    fun testName() {
        assertEquals("java.lang.Object", objectPsiClass.asScriptContext().name())
        assertEquals("java.lang.Integer", integerPsiClass.asScriptContext().name())
        assertEquals("java.util.Collection", collectionPsiClass.asScriptContext().name())
        assertEquals("com.itangcent.model.Model", modelPsiClass.asScriptContext().name())
        assertEquals("com.itangcent.model.Result", resultPsiClass.asScriptContext().name())
    }

    /**
     * it.hasAnn("annotation_name"):Boolean
     */
    fun testHasAnn() {
        assertFalse(objectPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
        assertFalse(integerPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
        assertFalse(collectionPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
        assertFalse(modelPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
        assertTrue(userCtrlPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
    }

    /**
     * it.ann("annotation_name"):String?
     */
    fun testAnn() {
        assertNull(objectPsiClass.asScriptContext().ann("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals("user", userCtrlPsiClass.asScriptContext().ann("org.springframework.web.bind.annotation.RequestMapping"))
    }

    /**
     * it.annMap("annotation_name"):Map<String, Any?>?
     */
    fun testAnnMap() {
        assertNull(objectPsiClass.asScriptContext().annMap("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals(mapOf("value" to "user"), userCtrlPsiClass.asScriptContext().annMap("org.springframework.web.bind.annotation.RequestMapping"))
    }

    /**
     * it.annMaps("annotation_name"):List<Map<String, Any?>>?
     */
    fun testAnnMaps() {
        assertNull(objectPsiClass.asScriptContext().annMap("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals(listOf(mapOf("value" to "user")), userCtrlPsiClass.asScriptContext().annMaps("org.springframework.web.bind.annotation.RequestMapping"))
    }

    /**
     * it.ann("annotation_name"):Any?
     */
    fun testAnnValue() {
        assertNull(objectPsiClass.asScriptContext().annValue("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals("user", userCtrlPsiClass.asScriptContext().annValue("org.springframework.web.bind.annotation.RequestMapping"))
    }

    /**
     * it.doc():String
     * it.doc("tag","subTag"):String?
     */
    fun testDoc() {
        assertNull(modelPsiClass.asScriptContext().doc())
        assertEquals("A demo class for test comments", commentDemoPsiClass.asScriptContext().doc())
        assertEquals("tangcent", commentDemoPsiClass.asScriptContext().doc("author"))
    }

    /**
     * it.docs("tag"):List<String>?
     */
    fun testDocs() {
        assertNull(modelPsiClass.asScriptContext().docs("author"))
        assertEquals(listOf("tangcent"), commentDemoPsiClass.asScriptContext().docs("author"))
    }

    /**
     * it.hasDoc("tag"):Boolean
     */
    fun testHasDoc() {
        assertFalse(modelPsiClass.asScriptContext().hasDoc("author"))
        assertTrue(commentDemoPsiClass.asScriptContext().hasDoc("author"))
    }

    fun testHasModifier() {
        assertFalse(modelPsiClass.asScriptContext().hasDoc("public"))
        assertTrue(commentDemoPsiClass.asScriptContext().hasModifier("public"))
    }

    fun testModifiers() {
        assertEquals(emptyList<String>(), modelPsiClass.asScriptContext().modifiers())
        assertEquals(listOf("public"), commentDemoPsiClass.asScriptContext().modifiers())
    }

    fun testSourceCode() {
        assertEquals("class Model {\n" +
                "\n" +
                "    /**\n" +
                "     * string field\n" +
                "     */\n" +
                "    @JsonProperty(\"s\")\n" +
                "    private String str;\n" +
                "\n" +
                "    /**\n" +
                "     * integer field\n" +
                "     */\n" +
                "    private Integer integer;\n" +
                "\n" +
                "    /**\n" +
                "     * stringList field\n" +
                "     */\n" +
                "    private List<String> stringList;\n" +
                "\n" +
                "    /**\n" +
                "     * integerArray field\n" +
                "     */\n" +
                "    private Integer[] integerArray;\n" +
                "\n" +
                "    public String getStr() {\n" +
                "        return str;\n" +
                "    }\n" +
                "\n" +
                "    public void setStr(String str) {\n" +
                "        this.str = str;\n" +
                "    }\n" +
                "\n" +
                "    public Integer getInteger() {\n" +
                "        return integer;\n" +
                "    }\n" +
                "\n" +
                "    public void setInteger(Integer integer) {\n" +
                "        this.integer = integer;\n" +
                "    }\n" +
                "\n" +
                "    public List<String> getStringList() {\n" +
                "        return stringList;\n" +
                "    }\n" +
                "\n" +
                "    public void setStringList(List<String> stringList) {\n" +
                "        this.stringList = stringList;\n" +
                "    }\n" +
                "\n" +
                "    public Integer[] getIntegerArray() {\n" +
                "        return integerArray;\n" +
                "    }\n" +
                "\n" +
                "    public void setIntegerArray(Integer[] integerArray) {\n" +
                "        this.integerArray = integerArray;\n" +
                "    }\n" +
                "\n" +
                "    public void setOnlySet(String onlySet) {\n" +
                "\n" +
                "    }\n" +
                "\n" +
                "    public String getOnlyGet() {\n" +
                "\n" +
                "    }\n" +
                "}", modelPsiClass.asScriptContext().sourceCode())
        assertEquals("/**\n" +
                " * A demo class for test comments\n" +
                " *\n" +
                " * @author tangcent\n" +
                " */\n" +
                "public class CommentDemo {\n" +
                "\n" +
                "    /**\n" +
                "     * single line\n" +
                "     *\n" +
                "     * @single\n" +
                "     * @desc low case of A\n" +
                "     */\n" +
                "    private String a;\n" +
                "\n" +
                "    /**\n" +
                "     * multi-line\n" +
                "     * second line\n" +
                "     *\n" +
                "     * @multi\n" +
                "     * @module x\n" +
                "     * x1 x2 x3\n" +
                "     * @module y\n" +
                "     */\n" +
                "    private String b;\n" +
                "\n" +
                "    /**\n" +
                "     * head line\n" +
                "     * second line\n" +
                "     * <pre>\n" +
                "     *     {\n" +
                "     *         \"a\":\"b\",\n" +
                "     *         \"c\":{\n" +
                "     *              \"x\":[\"y\"]\n" +
                "     *         }\n" +
                "     *     }\n" +
                "     * </pre>\n" +
                "     * see @{link somelink}\n" +
                "     * tail line\n" +
                "     */\n" +
                "    private String c;\n" +
                "\n" +
                "    /**\n" +
                "     * head line\n" +
                "     * second line\n" +
                "     * <pre>\n" +
                "     *\n" +
                "     *     {\n" +
                "     *         \"a\":\"b\",\n" +
                "     *         \"c\":{\n" +
                "     *              \"x\":[\"y\"]\n" +
                "     *         }\n" +
                "     *     }\n" +
                "     *\n" +
                "     * </pre>\n" +
                "     * <p>\n" +
                "     * see @{link somelink}\n" +
                "     * tail line\n" +
                "     */\n" +
                "    private String d;\n" +
                "\n" +
                "    private String e;//E is a mathematical constant approximately equal to 2.71828\n" +
                "\n" +
                "    /**\n" +
                "     * R, or r, is the eighteenth letter of the modern English alphabet and the ISO basic Latin alphabet.\n" +
                "     */\n" +
                "    private String r;//It's before s\n" +
                "\n" +
                "    /**\n" +
                "     * @param a A\n" +
                "     * @param b B\n" +
                "     */\n" +
                "    private String methodA(int a, int b) {\n" +
                "\n" +
                "    }\n" +
                "}", commentDemoPsiClass.asScriptContext().sourceCode())
    }

    fun testDefineCode() {
        assertEquals("class Model;", modelPsiClass.asScriptContext().defineCode())
        assertEquals("public class CommentDemo;", commentDemoPsiClass.asScriptContext().defineCode())
        assertEquals("public abstract interface IResult;", iResultPsiClass.asScriptContext().defineCode())
    }

    fun testContextType() {
        assertEquals("class", modelPsiClass.asScriptContext().contextType())
        assertEquals("class", commentDemoPsiClass.asScriptContext().contextType())
    }

    //endregion

    //region tests of ScriptClassContext

    fun testIsExtend() {
        assertTrue(collectionPsiClass.asClassContext().isExtend("java.util.Collection"))
        assertTrue(listPsiClass.asClassContext().isExtend("java.util.Collection"))
        assertFalse(collectionPsiClass.asClassContext().isExtend("java.util.List"))
        assertTrue(resultPsiClass.asClassContext().isExtend("com.itangcent.model.IResult"))
        assertFalse(iResultPsiClass.asClassContext().isExtend("com.itangcent.model.Result"))
        //todo: support any.isExtend(java.lang.Object) == true
        //assertFalse(modelPsiClass.asContext().isExtend("java.lang.Object"))
    }

    fun testIsMap() {
        assertFalse(objectPsiClass.asClassContext().isMap())
        assertFalse(integerPsiClass.asClassContext().isMap())
        assertFalse(longPsiClass.asClassContext().isMap())
        assertTrue(mapPsiClass.asClassContext().isMap())
        assertTrue(hashMapPsiClass.asClassContext().isMap())
        assertFalse(listPsiClass.asClassContext().isMap())
        assertFalse(iResultPsiClass.asClassContext().isMap())
        assertFalse(resultPsiClass.asClassContext().isMap())
        assertFalse(modelPsiClass.asClassContext().isMap())
    }

    fun testIsCollection() {
        assertFalse(objectPsiClass.asClassContext().isCollection())
        assertFalse(integerPsiClass.asClassContext().isCollection())
        assertFalse(longPsiClass.asClassContext().isCollection())
        assertFalse(mapPsiClass.asClassContext().isCollection())
        assertFalse(hashMapPsiClass.asClassContext().isCollection())
        assertTrue(collectionPsiClass.asClassContext().isCollection())
        assertTrue(listPsiClass.asClassContext().isCollection())
        assertFalse(iResultPsiClass.asClassContext().isCollection())
        assertFalse(resultPsiClass.asClassContext().isCollection())
        assertFalse(modelPsiClass.asClassContext().isCollection())
    }

    fun testIsArray() {
        assertFalse(objectPsiClass.asClassContext().isArray())
        assertFalse(integerPsiClass.asClassContext().isArray())
        assertFalse(longPsiClass.asClassContext().isArray())
        assertFalse(mapPsiClass.asClassContext().isArray())
        assertFalse(hashMapPsiClass.asClassContext().isArray())
        assertFalse(collectionPsiClass.asClassContext().isArray())
        assertFalse(listPsiClass.asClassContext().isArray())
        assertFalse(iResultPsiClass.asClassContext().isArray())
        assertFalse(modelPsiClass.asClassContext().isArray())
        assertFalse(resultPsiClass.asClassContext().isArray())
    }

    fun testIsNormalType() {
        assertTrue(objectPsiClass.asClassContext().isNormalType())
        assertTrue(integerPsiClass.asClassContext().isNormalType())
        assertTrue(longPsiClass.asClassContext().isNormalType())
        assertFalse(mapPsiClass.asClassContext().isNormalType())
        assertFalse(hashMapPsiClass.asClassContext().isNormalType())
        assertFalse(collectionPsiClass.asClassContext().isNormalType())
        assertFalse(listPsiClass.asClassContext().isNormalType())
        assertFalse(iResultPsiClass.asClassContext().isNormalType())
        assertFalse(modelPsiClass.asClassContext().isNormalType())
        assertFalse(resultPsiClass.asClassContext().isNormalType())
    }

    fun testFieldCnt() {
        assertEquals(0, objectPsiClass.asClassContext().fieldCnt())
        assertEquals(4, modelPsiClass.asClassContext().fieldCnt())
        assertEquals(3, resultPsiClass.asClassContext().fieldCnt())
        assertEquals(0, iResultPsiClass.asClassContext().fieldCnt())
    }

    fun testMethodCnt() {
        assertEquals(12, objectPsiClass.asClassContext().methodCnt())
        assertEquals(22, modelPsiClass.asClassContext().methodCnt())
        assertEquals(23, resultPsiClass.asClassContext().methodCnt())
        assertEquals(14, iResultPsiClass.asClassContext().methodCnt())
    }

    fun testToJson() {
        assertEquals("{}", objectPsiClass.asClassContext().toJson(true, true))

        assertEquals("{\n" +
                "  \"str\": \"\",\n" +
                "  \"integer\": 0,\n" +
                "  \"stringList\": [\n" +
                "    \"\"\n" +
                "  ],\n" +
                "  \"integerArray\": [\n" +
                "    0\n" +
                "  ],\n" +
                "  \"onlySet\": \"\",\n" +
                "  \"onlyGet\": \"\"\n" +
                "}", modelPsiClass.asClassContext().toJson(true, true))
        assertEquals("{\n" +
                "  \"str\": \"\",\n" +
                "  \"integer\": 0,\n" +
                "  \"stringList\": [\n" +
                "    \"\"\n" +
                "  ],\n" +
                "  \"integerArray\": [\n" +
                "    0\n" +
                "  ],\n" +
                "  \"onlyGet\": \"\"\n" +
                "}", modelPsiClass.asClassContext().toJson(true, false))
        assertEquals("{\n" +
                "  \"str\": \"\",\n" +
                "  \"integer\": 0,\n" +
                "  \"stringList\": [\n" +
                "    \"\"\n" +
                "  ],\n" +
                "  \"integerArray\": [\n" +
                "    0\n" +
                "  ],\n" +
                "  \"onlySet\": \"\"\n" +
                "}", modelPsiClass.asClassContext().toJson(false, true))
        assertEquals("{\n" +
                "  \"str\": \"\",\n" +
                "  \"integer\": 0,\n" +
                "  \"stringList\": [\n" +
                "    \"\"\n" +
                "  ],\n" +
                "  \"integerArray\": [\n" +
                "    0\n" +
                "  ]\n" +
                "}", modelPsiClass.asClassContext().toJson(false, false))

        assertEquals("{}", mapPsiClass.asClassContext().toJson(true, true))
        assertEquals("{}", mapPsiClass.asClassContext().toJson(true, false))
        assertEquals("{}", mapPsiClass.asClassContext().toJson(false, true))
        assertEquals("{}", mapPsiClass.asClassContext().toJson(false, false))

        assertEquals("[]", collectionPsiClass.asClassContext().toJson(true, true))
        assertEquals("[]", collectionPsiClass.asClassContext().toJson(true, false))
        assertEquals("[]", collectionPsiClass.asClassContext().toJson(false, true))
        assertEquals("[]", collectionPsiClass.asClassContext().toJson(false, false))

        assertEquals("{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {}\n" +
                "}", resultPsiClass.asClassContext().toJson(true, true))
        assertEquals("{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {}\n" +
                "}", resultPsiClass.asClassContext().toJson(true, false))
        assertEquals("{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {}\n" +
                "}", resultPsiClass.asClassContext().toJson(false, true))
        assertEquals("{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"\",\n" +
                "  \"data\": {}\n" +
                "}", resultPsiClass.asClassContext().toJson(false, false))

        assertEquals("{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"\"\n" +
                "}", iResultPsiClass.asClassContext().toJson(true, true))
        assertEquals("{\n" +
                "  \"code\": 0,\n" +
                "  \"msg\": \"\"\n" +
                "}", iResultPsiClass.asClassContext().toJson(true, false))
        assertEquals("{}", iResultPsiClass.asClassContext().toJson(false, true))
        assertEquals("{}", iResultPsiClass.asClassContext().toJson(false, false))
    }

    fun testToJson5() {
        assertEquals("{}", objectPsiClass.asClassContext().toJson5(true, true))

        assertEquals("{\n" +
                "    \"str\": \"\", //string field\n" +
                "    \"integer\": 0, //integer field\n" +
                "    \"stringList\": [ //stringList field\n" +
                "        \"\"\n" +
                "    ],\n" +
                "    \"integerArray\": [ //integerArray field\n" +
                "        0\n" +
                "    ],\n" +
                "    \"onlySet\": \"\",\n" +
                "    \"onlyGet\": \"\"\n" +
                "}", modelPsiClass.asClassContext().toJson5(true, true))
        assertEquals("{\n" +
                "    \"str\": \"\", //string field\n" +
                "    \"integer\": 0, //integer field\n" +
                "    \"stringList\": [ //stringList field\n" +
                "        \"\"\n" +
                "    ],\n" +
                "    \"integerArray\": [ //integerArray field\n" +
                "        0\n" +
                "    ],\n" +
                "    \"onlyGet\": \"\"\n" +
                "}", modelPsiClass.asClassContext().toJson5(true, false))
        assertEquals("{\n" +
                "    \"str\": \"\", //string field\n" +
                "    \"integer\": 0, //integer field\n" +
                "    \"stringList\": [ //stringList field\n" +
                "        \"\"\n" +
                "    ],\n" +
                "    \"integerArray\": [ //integerArray field\n" +
                "        0\n" +
                "    ],\n" +
                "    \"onlySet\": \"\"\n" +
                "}", modelPsiClass.asClassContext().toJson5(false, true))
        assertEquals("{\n" +
                "    \"str\": \"\", //string field\n" +
                "    \"integer\": 0, //integer field\n" +
                "    \"stringList\": [ //stringList field\n" +
                "        \"\"\n" +
                "    ],\n" +
                "    \"integerArray\": [ //integerArray field\n" +
                "        0\n" +
                "    ]\n" +
                "}", modelPsiClass.asClassContext().toJson5(false, false))

        assertEquals("{\n" +
                "    \"key\": null\n" +
                "}", mapPsiClass.asClassContext().toJson5(true, true))
        assertEquals("{\n" +
                "    \"key\": null\n" +
                "}", mapPsiClass.asClassContext().toJson5(true, false))
        assertEquals("{\n" +
                "    \"key\": null\n" +
                "}", mapPsiClass.asClassContext().toJson5(false, true))
        assertEquals("{\n" +
                "    \"key\": null\n" +
                "}", mapPsiClass.asClassContext().toJson5(false, false))

        assertEquals("[]", collectionPsiClass.asClassContext().toJson5(true, true))
        assertEquals("[]", collectionPsiClass.asClassContext().toJson5(true, false))
        assertEquals("[]", collectionPsiClass.asClassContext().toJson5(false, true))
        assertEquals("[]", collectionPsiClass.asClassContext().toJson5(false, false))

        assertEquals("{\n" +
                "    \"code\": 0, //response code\n" +
                "    \"msg\": \"\", //message\n" +
                "    \"data\": {} //response data\n" +
                "}", resultPsiClass.asClassContext().toJson5(true, true))
        assertEquals("{\n" +
                "    \"code\": 0, //response code\n" +
                "    \"msg\": \"\", //message\n" +
                "    \"data\": {} //response data\n" +
                "}", resultPsiClass.asClassContext().toJson5(true, false))
        assertEquals("{\n" +
                "    \"code\": 0, //response code\n" +
                "    \"msg\": \"\", //message\n" +
                "    \"data\": {} //response data\n" +
                "}", resultPsiClass.asClassContext().toJson5(false, true))
        assertEquals("{\n" +
                "    \"code\": 0, //response code\n" +
                "    \"msg\": \"\", //message\n" +
                "    \"data\": {} //response data\n" +
                "}", resultPsiClass.asClassContext().toJson5(false, false))

        assertEquals("{\n" +
                "    \"code\": 0,\n" +
                "    \"msg\": \"\"\n" +
                "}", iResultPsiClass.asClassContext().toJson5(true, true))
        assertEquals("{\n" +
                "    \"code\": 0,\n" +
                "    \"msg\": \"\"\n" +
                "}", iResultPsiClass.asClassContext().toJson5(true, false))
        assertEquals("{}", iResultPsiClass.asClassContext().toJson5(false, true))
        assertEquals("{}", iResultPsiClass.asClassContext().toJson5(false, false))
    }

    //endregion
}

