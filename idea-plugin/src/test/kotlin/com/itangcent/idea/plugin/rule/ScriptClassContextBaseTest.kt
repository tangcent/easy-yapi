package com.itangcent.idea.plugin.rule

import com.intellij.psi.PsiClass
import com.itangcent.common.kit.toJson
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.rule.ScriptRuleParser.ScriptClassContext
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Base test case of [ScriptClassContext]
 */
abstract class ScriptClassContextBaseTest : PluginContextLightCodeInsightFixtureTestCase() {

    internal val ruleParser: RuleParser by lazy {
        actionContext.instance(GroovyRuleParser::class)
    }

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
    internal lateinit var addPsiClass: PsiClass
    internal lateinit var updatePsiClass: PsiClass
    internal lateinit var validationGroupedDemoDtoPsiClass: PsiClass
    internal lateinit var userTypePsiClass: PsiClass
    internal lateinit var userInfoPsiClass: PsiClass

    override fun beforeBind() {
        super.beforeBind()
        loadFile("annotation/JsonProperty.java")!!
        loadFile("annotation/JsonIgnore.java")!!
        objectPsiClass = loadSource(Object::class.java)!!
        loadSource(java.lang.String::class.java)!!
        loadSource(java.lang.Number::class.java)!!
        loadSource(java.lang.Comparable::class.java)!!
        integerPsiClass = loadSource(java.lang.Integer::class)!!
        longPsiClass = loadSource(java.lang.Long::class)!!
        loadSource(java.lang.Iterable::class)
        collectionPsiClass = loadSource(Collection::class.java)!!
        listPsiClass = loadSource(List::class.java)!!
        mapPsiClass = loadSource(Map::class.java)!!
        loadSource(java.util.AbstractMap::class)
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
        loadClass("validation/NotEmpty.java")
        loadClass("validation/NotNull.java")
        addPsiClass = loadClass("constant/Add.java")!!
        updatePsiClass = loadClass("constant/Update.java")!!
        validationGroupedDemoDtoPsiClass = loadClass("model/ValidationGroupedDemoDto.java")!!
        userTypePsiClass = loadClass("constant/UserType.java")!!
        userInfoPsiClass = loadClass("model/UserInfo.java")!!
    }

    override fun customConfig(): String? {
        return "json.rule.field.name=@com.fasterxml.jackson.annotation.JsonProperty#value\n" +
                "field.required=@javax.validation.constraints.NotBlank\n" +
                "field.required=@javax.validation.constraints.NotNull\n" +
                "field.default.value=#default\n" +
                "field.mock=#mock\n" +
                "field.demo=#demo\n" +
                "field.ignore=@com.fasterxml.jackson.annotation.JsonIgnore#value\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n" +
                "json.additional.field[com.itangcent.model.UserInfo]={\"name\":\"label\",\"defaultValue\":\"genius\",\"type\":\"java.lang.String\",\"desc\":\"label of the user\",\"required\":true,\"mock\":\"@string\",\"demo\":\"genius\",\"advanced\":\"some\"}\n" +
                "json.additional.field[com.itangcent.model.UserInfo#name]={\"name\":\"firstName\",\"defaultValue\":\"tang\",\"type\":\"java.lang.String\",\"desc\":\"a family name\",\"required\":false,\"mock\":\"@string\",\"demo\":\"tang\",\"advanced\":\"some\"}\n" +
                "json.additional.field[com.itangcent.model.UserInfo#age]={\"name\":\"order\",\"defaultValue\":\"12\",\"type\":\"int\",\"desc\":\"order of the age in family\",\"required\":true,\"mock\":\"@int\",\"demo\":\"12\",\"advanced\":\"some\"}\n" +
                "field.order=#order\n" +
                "field.order.with=groovy:```\n" +
                "    def aDefineClass = a.defineClass()\n" +
                "    def bDefineClass = b.defineClass()\n" +
                "    if(aDefineClass==bDefineClass){\n" +
                "        return 0\n" +
                "    }else if(aDefineClass.isExtend(bDefineClass.name())){\n" +
                "        return 1\n" +
                "    }else{\n" +
                "        return -1\n" +
                "    }\n" +
                "```\n" +
                "field.doc=groovy:```\n" +
                "if(!it.isEnumField()){\n" +
                "    return\n" +
                "}\n" +
                "return it.asEnumField().getParam(\"desc\")\n" +
                "```"
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
        assertFalse(
            collectionPsiClass.asScriptContext()
                .hasAnn("org.springframework.web.bind.annotation.RestController")
        )
        assertFalse(modelPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
        assertTrue(userCtrlPsiClass.asScriptContext().hasAnn("org.springframework.web.bind.annotation.RestController"))
    }

    /**
     * it.ann("annotation_name"):String?
     */
    fun testAnn() {
        assertNull(objectPsiClass.asScriptContext().ann("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals(
            "user",
            userCtrlPsiClass.asScriptContext().ann("org.springframework.web.bind.annotation.RequestMapping")
        )
    }

    /**
     * it.annMap("annotation_name"):Map<String, Any?>?
     */
    fun testAnnMap() {
        assertNull(objectPsiClass.asScriptContext().annMap("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals(
            mapOf("value" to "user"),
            userCtrlPsiClass.asScriptContext().annMap("org.springframework.web.bind.annotation.RequestMapping")
        )
        assertEquals(
            mapOf("groups" to listOf(addPsiClass.asScriptContext())),
            validationGroupedDemoDtoPsiClass.asClassContext()
                .fields()[0].annMap("javax.validation.constraints.NotNull")
        )
        assertEquals(
            mapOf("groups" to updatePsiClass.asScriptContext()),
            validationGroupedDemoDtoPsiClass.asClassContext()
                .fields()[1].annMap("javax.validation.constraints.NotEmpty")
        )
    }

    /**
     * it.annMaps("annotation_name"):List<Map<String, Any?>>?
     */
    fun testAnnMaps() {
        assertNull(objectPsiClass.asScriptContext().annMap("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals(
            listOf(mapOf("value" to "user")),
            userCtrlPsiClass.asScriptContext().annMaps("org.springframework.web.bind.annotation.RequestMapping")
        )
    }

    /**
     * it.ann("annotation_name"):Any?
     */
    fun testAnnValue() {
        assertNull(objectPsiClass.asScriptContext().annValue("org.springframework.web.bind.annotation.RequestMapping"))
        assertEquals(
            "user",
            userCtrlPsiClass.asScriptContext().annValue("org.springframework.web.bind.annotation.RequestMapping")
        )
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
        assertEquals(
            "class Model {\n" +
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
                    "    /**\n" +
                    "     * @order 100\n" +
                    "     */\n" +
                    "    private String shouldBeLast;\n" +
                    "\n" +
                    "    /**\n" +
                    "     * @order 0\n" +
                    "     */\n" +
                    "    private String shouldBeFirst;\n" +
                    "\n" +
                    "\n" +
                    "    @JsonIgnore\n" +
                    "    private String shouldIgnore;\n" +
                    "\n" +
                    "    private String shouldIgnoreByGetter;\n" +
                    "\n" +
                    "    private String shouldIgnoreBySetter;\n" +
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
                    "\n" +
                    "    public String getShouldIgnore() {\n" +
                    "        return shouldIgnore;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void setShouldIgnore(String shouldIgnore) {\n" +
                    "        this.shouldIgnore = shouldIgnore;\n" +
                    "    }\n" +
                    "\n" +
                    "    @JsonIgnore\n" +
                    "    public String getShouldIgnoreByGetter() {\n" +
                    "        return shouldIgnoreByGetter;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void setShouldIgnoreByGetter(String shouldIgnoreByGetter) {\n" +
                    "        this.shouldIgnoreByGetter = shouldIgnoreByGetter;\n" +
                    "    }\n" +
                    "\n" +
                    "    public String getShouldIgnoreBySetter() {\n" +
                    "        return shouldIgnoreBySetter;\n" +
                    "    }\n" +
                    "\n" +
                    "    @JsonIgnore\n" +
                    "    public void setShouldIgnoreBySetter(String shouldIgnoreBySetter) {\n" +
                    "        this.shouldIgnoreBySetter = shouldIgnoreBySetter;\n" +
                    "    }\n" +
                    "}", modelPsiClass.asScriptContext().sourceCode()
        )
        assertEquals(
            "/**\n" +
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
                    "}", commentDemoPsiClass.asScriptContext().sourceCode()
        )
    }

    fun testDefineCode() {
        assertEquals("class Model;", modelPsiClass.asScriptContext().defineCode())
        assertEquals("public class CommentDemo;", commentDemoPsiClass.asScriptContext().defineCode())
        assertEquals("public interface IResult;", iResultPsiClass.asScriptContext().defineCode())
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

    fun testIsPrimitive() {
        assertFalse(objectPsiClass.asClassContext().isPrimitive())
        assertFalse(integerPsiClass.asClassContext().isPrimitive())
        assertFalse(longPsiClass.asClassContext().isPrimitive())
        assertFalse(mapPsiClass.asClassContext().isPrimitive())
        assertFalse(hashMapPsiClass.asClassContext().isPrimitive())
        assertFalse(collectionPsiClass.asClassContext().isPrimitive())
        assertFalse(listPsiClass.asClassContext().isPrimitive())
        assertFalse(iResultPsiClass.asClassContext().isPrimitive())
        assertFalse(modelPsiClass.asClassContext().isPrimitive())
        assertFalse(resultPsiClass.asClassContext().isPrimitive())
    }

    fun testIsPrimitiveWrapper() {
        assertFalse(objectPsiClass.asClassContext().isPrimitiveWrapper())
        assertTrue(integerPsiClass.asClassContext().isPrimitiveWrapper())
        assertTrue(longPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(mapPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(hashMapPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(collectionPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(listPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(iResultPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(modelPsiClass.asClassContext().isPrimitiveWrapper())
        assertFalse(resultPsiClass.asClassContext().isPrimitiveWrapper())
    }

    fun testFieldCnt() {
        assertEquals(0, objectPsiClass.asClassContext().fieldCnt())
        assertEquals(9, modelPsiClass.asClassContext().fieldCnt())
        assertEquals(3, resultPsiClass.asClassContext().fieldCnt())
        assertEquals(0, iResultPsiClass.asClassContext().fieldCnt())
    }

    fun testMethodCnt() {
        assertEquals(12, objectPsiClass.asClassContext().methodCnt())
        assertEquals(28, modelPsiClass.asClassContext().methodCnt())
        assertEquals(25, resultPsiClass.asClassContext().methodCnt())
        assertEquals(14, iResultPsiClass.asClassContext().methodCnt())
    }

    fun testToJson() {
        assertEquals("{}", objectPsiClass.asClassContext().toJson(true, true))

        assertEquals(
            "{\n" +
                    "  \"id\": 0,\n" +
                    "  \"type\": 0,\n" +
                    "  \"name\": \"\",\n" +
                    "  \"age\": 0,\n" +
                    "  \"sex\": 0,\n" +
                    "  \"birthDay\": \"\",\n" +
                    "  \"regtime\": \"\"\n" +
                    "}", userInfoPsiClass.asClassContext().toJson(true, true)
        )

        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"onlySet\": \"\",\n" +
                    "  \"onlyGet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson(true, true)
        )
        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"shouldIgnoreBySetter\": \"\",\n" +
                    "  \"onlyGet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson(true, false)
        )
        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"shouldIgnoreByGetter\": \"\",\n" +
                    "  \"onlySet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson(false, true)
        )
        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"shouldIgnoreByGetter\": \"\",\n" +
                    "  \"shouldIgnoreBySetter\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson(false, false)
        )

        assertEquals(
            "{\n" +
                    "  \"\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson(true, true)
        )
        assertEquals(
            "{\n" +
                    "  \"\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson(true, false)
        )
        assertEquals(
            "{\n" +
                    "  \"\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson(false, true)
        )
        assertEquals(
            "{\n" +
                    "  \"\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson(false, false)
        )

        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "  {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson(true, true))
        )
        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "  {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson(true, false))
        )
        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "  {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson(false, true))
        )
        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "  {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson(false, false))
        )

        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\",\n" +
                    "  \"data\": {}\n" +
                    "}", resultPsiClass.asClassContext().toJson(true, true)
        )
        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\",\n" +
                    "  \"data\": {}\n" +
                    "}", resultPsiClass.asClassContext().toJson(true, false)
        )
        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\",\n" +
                    "  \"data\": {}\n" +
                    "}", resultPsiClass.asClassContext().toJson(false, true)
        )
        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\",\n" +
                    "  \"data\": {}\n" +
                    "}", resultPsiClass.asClassContext().toJson(false, false)
        )

        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\"\n" +
                    "}", iResultPsiClass.asClassContext().toJson(true, true)
        )
        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\"\n" +
                    "}", iResultPsiClass.asClassContext().toJson(true, false)
        )
        assertEquals("{}", iResultPsiClass.asClassContext().toJson(false, true))
        assertEquals("{}", iResultPsiClass.asClassContext().toJson(false, false))
    }

    fun testToJson5() {
        assertEquals("{}", objectPsiClass.asClassContext().toJson5(true, true))

        assertEquals(
            "{\n" +
                    "    \"id\": 0, //user id\n" +
                    "    /**\n" +
                    "     * user type\n" +
                    "     * 1 :administration\n" +
                    "     * ADMINISTRATION\n" +
                    "     * 2 :a person, an animal or a plant\n" +
                    "     * MEMBER\n" +
                    "     * 3 :Anonymous visitor\n" +
                    "     * ANONYMOUS\n" +
                    "     */\n" +
                    "    \"type\": 0,\n" +
                    "    \"name\": \"\", //user name\n" +
                    "    \"age\": 0, //user age\n" +
                    "    \"sex\": 0,\n" +
                    "    \"birthDay\": \"\", //user birthDay\n" +
                    "    \"regtime\": \"\" //user regtime\n" +
                    "}", userInfoPsiClass.asClassContext().toJson5(true, true)
        )
        assertEquals(
            "{\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"\", //string field\n" +
                    "    \"integer\": 0, //integer field\n" +
                    "    \"stringList\": [ //stringList field\n" +
                    "        \"\"\n" +
                    "    ],\n" +
                    "    \"integerArray\": [ //integerArray field\n" +
                    "        0\n" +
                    "    ],\n" +
                    "    \"onlySet\": \"\",\n" +
                    "    \"onlyGet\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson5(true, true)
        )
        assertEquals(
            "{\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"\", //string field\n" +
                    "    \"integer\": 0, //integer field\n" +
                    "    \"stringList\": [ //stringList field\n" +
                    "        \"\"\n" +
                    "    ],\n" +
                    "    \"integerArray\": [ //integerArray field\n" +
                    "        0\n" +
                    "    ],\n" +
                    "    \"shouldIgnoreBySetter\": \"\",\n" +
                    "    \"onlyGet\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson5(true, false)
        )
        assertEquals(
            "{\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"\", //string field\n" +
                    "    \"integer\": 0, //integer field\n" +
                    "    \"stringList\": [ //stringList field\n" +
                    "        \"\"\n" +
                    "    ],\n" +
                    "    \"integerArray\": [ //integerArray field\n" +
                    "        0\n" +
                    "    ],\n" +
                    "    \"shouldIgnoreByGetter\": \"\",\n" +
                    "    \"onlySet\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson5(false, true)
        )
        assertEquals(
            "{\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"\", //string field\n" +
                    "    \"integer\": 0, //integer field\n" +
                    "    \"stringList\": [ //stringList field\n" +
                    "        \"\"\n" +
                    "    ],\n" +
                    "    \"integerArray\": [ //integerArray field\n" +
                    "        0\n" +
                    "    ],\n" +
                    "    \"shouldIgnoreByGetter\": \"\",\n" +
                    "    \"shouldIgnoreBySetter\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "}", modelPsiClass.asClassContext().toJson5(false, false)
        )

        assertEquals(
            "{\n" +
                    "    \"key\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson5(true, true)
        )
        assertEquals(
            "{\n" +
                    "    \"key\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson5(true, false)
        )
        assertEquals(
            "{\n" +
                    "    \"key\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson5(false, true)
        )
        assertEquals(
            "{\n" +
                    "    \"key\": null\n" +
                    "}", mapPsiClass.asClassContext().toJson5(false, false)
        )

        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "    {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson5(true, true))
        )
        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "    {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson5(true, false))
        )
        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "    {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson5(false, true))
        )
        assertTrue(
            arrayOf(
                "[]", "[\n" +
                        "    {}\n" +
                        "]"
            ).contains(collectionPsiClass.asClassContext().toJson5(false, false))
        )

        assertEquals(
            "{\n" +
                    "    \"code\": 0, //response code\n" +
                    "    \"msg\": \"\", //message\n" +
                    "    \"data\": {} //response data\n" +
                    "}", resultPsiClass.asClassContext().toJson5(true, true)
        )
        assertEquals(
            "{\n" +
                    "    \"code\": 0, //response code\n" +
                    "    \"msg\": \"\", //message\n" +
                    "    \"data\": {} //response data\n" +
                    "}", resultPsiClass.asClassContext().toJson5(true, false)
        )
        assertEquals(
            "{\n" +
                    "    \"code\": 0, //response code\n" +
                    "    \"msg\": \"\", //message\n" +
                    "    \"data\": {} //response data\n" +
                    "}", resultPsiClass.asClassContext().toJson5(false, true)
        )
        assertEquals(
            "{\n" +
                    "    \"code\": 0, //response code\n" +
                    "    \"msg\": \"\", //message\n" +
                    "    \"data\": {} //response data\n" +
                    "}", resultPsiClass.asClassContext().toJson5(false, false)
        )

        assertEquals(
            "{\n" +
                    "    \"code\": 0,\n" +
                    "    \"msg\": \"\"\n" +
                    "}", iResultPsiClass.asClassContext().toJson5(true, true)
        )
        assertEquals(
            "{\n" +
                    "    \"code\": 0,\n" +
                    "    \"msg\": \"\"\n" +
                    "}", iResultPsiClass.asClassContext().toJson5(true, false)
        )
        assertEquals("{}", iResultPsiClass.asClassContext().toJson5(false, true))
        assertEquals("{}", iResultPsiClass.asClassContext().toJson5(false, false))
    }

    fun testToObject() {
        assertEquals(
            "{}", objectPsiClass.asClassContext().toObject(
                readGetter = true,
                readSetter = true,
                readComment = true
            ).toJson()
        )

        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"@comment\": {\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"string field\",\n" +
                    "    \"integer\": \"integer field\",\n" +
                    "    \"stringList\": \"stringList field\",\n" +
                    "    \"integerArray\": \"integerArray field\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "  },\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"onlySet\": \"\",\n" +
                    "  \"onlyGet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                modelPsiClass.asClassContext().toObject(
                    readGetter = true,
                    readSetter = true,
                    readComment = true
                )
            )
        )
        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"@comment\": {\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"string field\",\n" +
                    "    \"integer\": \"integer field\",\n" +
                    "    \"stringList\": \"stringList field\",\n" +
                    "    \"integerArray\": \"integerArray field\",\n" +
                    "    \"shouldIgnoreBySetter\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "  },\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"shouldIgnoreBySetter\": \"\",\n" +
                    "  \"onlyGet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                modelPsiClass.asClassContext().toObject(
                    readGetter = true,
                    readSetter = false,
                    readComment = true
                )
            )
        )
        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"@comment\": {\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"string field\",\n" +
                    "    \"integer\": \"integer field\",\n" +
                    "    \"stringList\": \"stringList field\",\n" +
                    "    \"integerArray\": \"integerArray field\",\n" +
                    "    \"shouldIgnoreByGetter\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "  },\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"shouldIgnoreByGetter\": \"\",\n" +
                    "  \"onlySet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                modelPsiClass.asClassContext().toObject(false, true, true)
            )
        )
        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"@comment\": {\n" +
                    "    \"shouldBeFirst\": \"\",\n" +
                    "    \"s\": \"string field\",\n" +
                    "    \"integer\": \"integer field\",\n" +
                    "    \"stringList\": \"stringList field\",\n" +
                    "    \"integerArray\": \"integerArray field\",\n" +
                    "    \"shouldIgnoreByGetter\": \"\",\n" +
                    "    \"shouldIgnoreBySetter\": \"\",\n" +
                    "    \"shouldBeLast\": \"\"\n" +
                    "  },\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"shouldIgnoreByGetter\": \"\",\n" +
                    "  \"shouldIgnoreBySetter\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                modelPsiClass.asClassContext().toObject(false, false, true)
            )
        )

        assertEquals(
            "{\n" +
                    "  \"shouldBeFirst\": \"\",\n" +
                    "  \"s\": \"\",\n" +
                    "  \"integer\": 0,\n" +
                    "  \"stringList\": [\n" +
                    "    \"\"\n" +
                    "  ],\n" +
                    "  \"integerArray\": [\n" +
                    "    0\n" +
                    "  ],\n" +
                    "  \"onlySet\": \"\",\n" +
                    "  \"onlyGet\": \"\",\n" +
                    "  \"shouldBeLast\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                modelPsiClass.asClassContext().toObject(true, true, false)
            )
        )

        assertEquals(
            "{}",
            GsonUtils.prettyJson(
                mapPsiClass.asClassContext().toObject(true, true, true)
            )
        )

        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"@comment\": {\n" +
                    "    \"code\": \"response code\",\n" +
                    "    \"msg\": \"message\",\n" +
                    "    \"data\": \"response data\"\n" +
                    "  },\n" +
                    "  \"msg\": \"\",\n" +
                    "  \"data\": {}\n" +
                    "}",
            GsonUtils.prettyJson(
                resultPsiClass.asClassContext().toObject(true, true, true)
            )
        )
        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"msg\": \"\",\n" +
                    "  \"data\": {}\n" +
                    "}",
            GsonUtils.prettyJson(
                resultPsiClass.asClassContext().toObject(true, false, false)
            )
        )

        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"@comment\": {},\n" +
                    "  \"msg\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                iResultPsiClass.asClassContext().toObject(true, true, true)
            )
        )
        assertEquals(
            "{\n" +
                    "  \"code\": 0,\n" +
                    "  \"@comment\": {},\n" +
                    "  \"msg\": \"\"\n" +
                    "}",
            GsonUtils.prettyJson(
                iResultPsiClass.asClassContext().toObject(true, false, true)
            )
        )
        assertEquals(
            "{}",
            GsonUtils.prettyJson(
                iResultPsiClass.asClassContext().toObject(false, true, false)
            )
        )
    }

    fun testIsInterface() {
        assertFalse(objectPsiClass.asClassContext().isInterface())
        assertFalse(integerPsiClass.asClassContext().isInterface())
        assertFalse(longPsiClass.asClassContext().isInterface())
        assertTrue(mapPsiClass.asClassContext().isInterface())
        assertFalse(hashMapPsiClass.asClassContext().isInterface())
        assertTrue(collectionPsiClass.asClassContext().isInterface())
        assertTrue(listPsiClass.asClassContext().isInterface())
        assertTrue(iResultPsiClass.asClassContext().isInterface())
        assertFalse(resultPsiClass.asClassContext().isInterface())
        assertFalse(modelPsiClass.asClassContext().isInterface())
    }

    fun testIsAnnotationType() {
        assertFalse(objectPsiClass.asClassContext().isAnnotationType())
        assertFalse(integerPsiClass.asClassContext().isAnnotationType())
        assertFalse(longPsiClass.asClassContext().isAnnotationType())
        assertFalse(mapPsiClass.asClassContext().isAnnotationType())
        assertFalse(hashMapPsiClass.asClassContext().isAnnotationType())
        assertFalse(collectionPsiClass.asClassContext().isAnnotationType())
        assertFalse(listPsiClass.asClassContext().isAnnotationType())
        assertFalse(iResultPsiClass.asClassContext().isAnnotationType())
        assertFalse(resultPsiClass.asClassContext().isAnnotationType())
        assertFalse(modelPsiClass.asClassContext().isAnnotationType())
    }

    fun testIsEnum() {
        assertFalse(objectPsiClass.asClassContext().isEnum())
        assertFalse(integerPsiClass.asClassContext().isEnum())
        assertFalse(longPsiClass.asClassContext().isEnum())
        assertFalse(mapPsiClass.asClassContext().isEnum())
        assertFalse(hashMapPsiClass.asClassContext().isEnum())
        assertFalse(collectionPsiClass.asClassContext().isEnum())
        assertFalse(listPsiClass.asClassContext().isEnum())
        assertFalse(iResultPsiClass.asClassContext().isEnum())
        assertFalse(resultPsiClass.asClassContext().isEnum())
        assertFalse(modelPsiClass.asClassContext().isEnum())
    }

    fun testSuperClass() {
        assertNull(objectPsiClass.asClassContext().superClass())
        assertNotNull(integerPsiClass.asClassContext().superClass())
        assertNotNull(longPsiClass.asClassContext().superClass())
        assertNull(mapPsiClass.asClassContext().superClass())
        assertNotNull(hashMapPsiClass.asClassContext().superClass())
        assertNull(collectionPsiClass.asClassContext().superClass())
        assertNull(listPsiClass.asClassContext().superClass())
        assertNull(iResultPsiClass.asClassContext().superClass())
        assertNotNull(resultPsiClass.asClassContext().superClass())
        assertNotNull(modelPsiClass.asClassContext().superClass())
    }

    fun testExtends() {
        assertTrue(objectPsiClass.asClassContext().extends()!!.isEmpty())
        assertFalse(integerPsiClass.asClassContext().extends()!!.isEmpty())
        assertFalse(longPsiClass.asClassContext().extends()!!.isEmpty())
        assertTrue(mapPsiClass.asClassContext().extends()!!.isEmpty())
        assertFalse(hashMapPsiClass.asClassContext().extends()!!.isEmpty())
        assertFalse(collectionPsiClass.asClassContext().extends()!!.isEmpty())
        assertFalse(listPsiClass.asClassContext().extends()!!.isEmpty())
        assertTrue(iResultPsiClass.asClassContext().extends()!!.isEmpty())
        assertTrue(resultPsiClass.asClassContext().extends()!!.isEmpty())
        assertTrue(modelPsiClass.asClassContext().extends()!!.isEmpty())
    }

    fun testImplements() {
        assertTrue(objectPsiClass.asClassContext().implements()!!.isEmpty())
        assertFalse(integerPsiClass.asClassContext().implements()!!.isEmpty())
        assertFalse(longPsiClass.asClassContext().implements()!!.isEmpty())
        assertTrue(mapPsiClass.asClassContext().implements()!!.isEmpty())
        assertFalse(hashMapPsiClass.asClassContext().implements()!!.isEmpty())
        assertTrue(collectionPsiClass.asClassContext().implements()!!.isEmpty())
        assertTrue(listPsiClass.asClassContext().implements()!!.isEmpty())
        assertTrue(iResultPsiClass.asClassContext().implements()!!.isEmpty())
        assertFalse(resultPsiClass.asClassContext().implements()!!.isEmpty())
        assertTrue(modelPsiClass.asClassContext().implements()!!.isEmpty())
    }

    fun testMavenId() {
        assertNull(objectPsiClass.asClassContext().mavenId())
    }

    //endregion

    //region tests of ScriptFieldContext

    fun testIsEnumField() {
        assertFalse(userInfoPsiClass.asClassContext().fields()[0].isEnumField())
        assertTrue(userTypePsiClass.asClassContext().fields()[0].isEnumField())
    }

    fun testAsEnumField() {
        assertNull(userInfoPsiClass.asClassContext().fields()[0].asEnumField())
        run {
            val enumField = userTypePsiClass.asClassContext().fields()[0].asEnumField()
            assertNotNull(enumField)
            enumField!!
            assertEquals(0, enumField.ordinal())
            assertEquals(mapOf("type" to 1, "desc" to "ADMINISTRATION"), enumField.getParams())
            assertEquals("ADMINISTRATION", enumField.getParam("desc"))
        }
        run {
            val enumField = userTypePsiClass.asClassContext().fields()[1].asEnumField()
            assertNotNull(enumField)
            enumField!!
            assertEquals(1, enumField.ordinal())
            assertEquals(mapOf("type" to 2, "desc" to "MEMBER"), enumField.getParams())
            assertEquals("MEMBER", enumField.getParam("desc"))
        }
    }

    //endregion
}

