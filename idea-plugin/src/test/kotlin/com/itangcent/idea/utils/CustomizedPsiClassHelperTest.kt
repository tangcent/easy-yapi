package com.itangcent.idea.utils

import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.common.utils.GsonUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.JsonOption

/**
 * Test case of [CustomizedPsiClassHelper]
 */
internal class CustomizedPsiClassHelperTest : ContextualPsiClassHelperBaseTest() {

    override fun customConfig(): String {
        //language=Properties
        return "dev=true\n" +
                "json.rule.field.name=@com.fasterxml.jackson.annotation.JsonProperty#value\n" +
                "field.required=@javax.validation.constraints.NotBlank\n" +
                "field.required=@javax.validation.constraints.NotNull\n" +
                "field.default.value=#default\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n" +
                "json.additional.field[com.itangcent.model.UserInfo]={\"name\":\"label\",\"defaultValue\":\"genius\",\"type\":\"java.lang.String\",\"desc\":\"label of the user\",\"required\":true}\n" +
                "json.additional.field[com.itangcent.model.UserInfo#name]={\"name\":\"firstName\",\"defaultValue\":\"tang\",\"type\":\"java.lang.String\",\"desc\":\"a family name\",\"required\":false}\n" +
                "json.additional.field[com.itangcent.model.UserInfo#age]={\"name\":\"order\",\"defaultValue\":\"12\",\"type\":\"int\",\"desc\":\"order of the age in family\",\"required\":true}"
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class) }
    }

    fun testGetTypeObject() {

        //getTypeObject from psiType without option-------------------------------------------------

        assertEquals(
            "{}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(objectPsiClass), objectPsiClass))
        )
        assertEquals(
            "0",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(integerPsiClass), integerPsiClass))
        )
        assertEquals(
            "\"\"",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(stringPsiClass), stringPsiClass))
        )
        assertEquals(
            "[]", GsonUtils.toJson(
                psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(collectionPsiClass), collectionPsiClass)
            )
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(mapPsiClass), mapPsiClass))
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(listPsiClass), listPsiClass))
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(hashMapPsiClass), hashMapPsiClass))
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(linkedListPsiClass),
                    linkedListPsiClass
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(PsiTypesUtil.getClassType(modelPsiClass), modelPsiClass))
        )

        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(userInfoPsiClass),
                    userInfoPsiClass
                )
            )
        )

        assertEquals(
            "{\"intArr\":[123,456],\"@required\":{\"intArr\":false,\"amount\":false,\"strings\":false,\"invalid\":false,\"model\":false,\"modelList\":false},\"@default\":{\"intArr\":\"[123, 456]\",\"amount\":\"{\\\"abc\\\":\\\"123\\\",\\\"def\\\":\\\"456\\\"}\",\"strings\":\"[\\\"abc\\\",\\\"123\\\"]\",\"invalid\":\"[\\\"abc\\\",\\\"123\\\"}\",\"model\":\"{\\\"s\\\":\\\"aaa\\\",\\\"s2\\\":\\\"bbb\\\",\\\"stringList\\\":\\\"abc\\\"}\",\"modelList\":\"[{\\\"s\\\":\\\"aaa\\\",\\\"s2\\\":\\\"bbb\\\",\\\"stringList\\\":\\\"abc\\\"}}\"},\"amount\":{\"abc\":\"123\",\"def\":\"456\",\"@default\":{\"abc\":\"123\",\"def\":\"456\"}},\"strings\":[\"abc\",\"123\"],\"invalid\":[\"\"],\"model\":{\"s\":\"aaa\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":\"abc\",\"integerArray\":[0],\"s2\":\"bbb\",\"@default\":{\"s\":\"aaa\",\"s2\":\"bbb\",\"stringList\":\"abc\"}},\"modelList\":[{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}]}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(defaultPsiClass),
                    userInfoPsiClass
                )
            )
        )

        //getTypeObject from psiType  with option-------------------------------------------------

        assertEquals(
            "{}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(objectPsiClass),
                    objectPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "0",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(integerPsiClass), integerPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "\"\"",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(stringPsiClass), stringPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "[]", GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(collectionPsiClass), collectionPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(mapPsiClass), mapPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(listPsiClass), listPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(hashMapPsiClass), hashMapPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(linkedListPsiClass),
                    linkedListPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(modelPsiClass), modelPsiClass,
                    JsonOption.NONE
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlyGet\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlyGet\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(modelPsiClass), modelPsiClass,
                    JsonOption.READ_GETTER
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlySet\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlySet\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(modelPsiClass), modelPsiClass,
                    JsonOption.READ_SETTER
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlySet\":false,\"onlyGet\":false},\"@comment\":{\"s\":\"string field\",\"integer\":\"integer field\",\"stringList\":\"stringList field\",\"integerArray\":\"integerArray field\"},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlySet\":\"\",\"onlyGet\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(modelPsiClass), modelPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.NONE
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.READ_GETTER
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.READ_SETTER
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"name\":\"user name\",\"firstName\":\"a family name\",\"age\":\"user age\",\"order\":\"order of the age in family\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\",\"label\":\"label of the user\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    PsiTypesUtil.getClassType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.ALL
                )
            )
        )

        //getTypeObject from duckType without option-------------------------------------------------

        assertEquals(
            "{}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(objectPsiClass), objectPsiClass))
        )
        assertEquals(
            "0",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(integerPsiClass), integerPsiClass))
        )
        assertEquals(
            "\"\"",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(stringPsiClass), stringPsiClass))
        )
        assertEquals(
            "[]", GsonUtils.toJson(
                psiClassHelper.getTypeObject(SingleDuckType(collectionPsiClass), collectionPsiClass)
            )
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(mapPsiClass), mapPsiClass))
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(listPsiClass), listPsiClass))
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(hashMapPsiClass), hashMapPsiClass))
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(linkedListPsiClass),
                    linkedListPsiClass
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(modelPsiClass), modelPsiClass))
        )

        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getTypeObject(SingleDuckType(userInfoPsiClass), userInfoPsiClass))
        )

        //getTypeObject from duckType  with option-------------------------------------------------

        assertEquals(
            "{}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(objectPsiClass),
                    objectPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "0",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(integerPsiClass), integerPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "\"\"",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(stringPsiClass), stringPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "[]", GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(collectionPsiClass), collectionPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(mapPsiClass), mapPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(listPsiClass), listPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(hashMapPsiClass), hashMapPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "[]",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(linkedListPsiClass),
                    linkedListPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(modelPsiClass), modelPsiClass,
                    JsonOption.NONE
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlyGet\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlyGet\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(modelPsiClass), modelPsiClass,
                    JsonOption.READ_GETTER
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlySet\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlySet\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(modelPsiClass), modelPsiClass,
                    JsonOption.READ_SETTER
                )
            )
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlySet\":false,\"onlyGet\":false},\"@comment\":{\"s\":\"string field\",\"integer\":\"integer field\",\"stringList\":\"stringList field\",\"integerArray\":\"integerArray field\"},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlySet\":\"\",\"onlyGet\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(modelPsiClass), modelPsiClass,
                    JsonOption.ALL
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.NONE
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.READ_GETTER
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.READ_SETTER
                )
            )
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"name\":\"user name\",\"firstName\":\"a family name\",\"age\":\"user age\",\"order\":\"order of the age in family\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\",\"label\":\"label of the user\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(
                psiClassHelper.getTypeObject(
                    SingleDuckType(userInfoPsiClass), userInfoPsiClass,
                    JsonOption.ALL
                )
            )
        )
    }

    fun testGetFields() {
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}",
            GsonUtils.toJson(psiClassHelper.getFields(modelPsiClass))
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0]}",
            GsonUtils.toJson(psiClassHelper.getFields(modelPsiClass, modelPsiClass))
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlySet\":false,\"onlyGet\":false},\"@comment\":{\"s\":\"string field\",\"integer\":\"integer field\",\"stringList\":\"stringList field\",\"integerArray\":\"integerArray field\"},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlySet\":\"\",\"onlyGet\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getFields(modelPsiClass, JsonOption.ALL))
        )
        assertEquals(
            "{\"s\":\"\",\"@required\":{\"s\":false,\"integer\":false,\"stringList\":false,\"integerArray\":false,\"onlySet\":false,\"onlyGet\":false},\"@comment\":{\"s\":\"string field\",\"integer\":\"integer field\",\"stringList\":\"stringList field\",\"integerArray\":\"integerArray field\"},\"integer\":0,\"stringList\":[\"\"],\"integerArray\":[0],\"onlySet\":\"\",\"onlyGet\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getFields(modelPsiClass, modelPsiClass, JsonOption.ALL))
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getFields(userInfoPsiClass))
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getFields(userInfoPsiClass, userInfoPsiClass))
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"name\":\"user name\",\"firstName\":\"a family name\",\"age\":\"user age\",\"order\":\"order of the age in family\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\",\"label\":\"label of the user\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getFields(userInfoPsiClass, JsonOption.ALL))
        )
        assertEquals(
            "{\"id\":0,\"@required\":{\"id\":false,\"type\":false,\"name\":true,\"firstName\":false,\"age\":true,\"order\":true,\"sex\":false,\"birthDay\":false,\"regtime\":false,\"label\":true},\"@default\":{\"id\":\"0\",\"name\":\"tangcent\",\"firstName\":\"tang\",\"order\":\"12\",\"label\":\"genius\"},\"@comment\":{\"id\":\"user id\",\"type\":\"user type\",\"name\":\"user name\",\"firstName\":\"a family name\",\"age\":\"user age\",\"order\":\"order of the age in family\",\"sex\":\"\",\"birthDay\":\"user birthDay\",\"regtime\":\"user regtime\",\"label\":\"label of the user\"},\"type\":0,\"name\":\"\",\"firstName\":\"\",\"age\":0,\"order\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\",\"label\":\"\"}",
            GsonUtils.toJson(psiClassHelper.getFields(userInfoPsiClass, userInfoPsiClass, JsonOption.ALL))
        )
    }

    fun testIsNormalType() {
        //check isNormalType from PsiClass
        assertTrue(psiClassHelper.isNormalType(objectPsiClass))
        assertTrue(psiClassHelper.isNormalType(integerPsiClass))
        assertTrue(psiClassHelper.isNormalType(stringPsiClass))
        assertFalse(psiClassHelper.isNormalType(collectionPsiClass))
        assertFalse(psiClassHelper.isNormalType(mapPsiClass))
        assertFalse(psiClassHelper.isNormalType(listPsiClass))
        assertFalse(psiClassHelper.isNormalType(hashMapPsiClass))
        assertFalse(psiClassHelper.isNormalType(linkedListPsiClass))
        assertFalse(psiClassHelper.isNormalType(modelPsiClass))
        assertFalse(psiClassHelper.isNormalType(userInfoPsiClass))

        //check isNormalType from PsiType
        assertTrue(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(objectPsiClass)))
        assertTrue(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(integerPsiClass)))
        assertTrue(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(stringPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(collectionPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(mapPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(listPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(hashMapPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(linkedListPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(modelPsiClass)))
        assertFalse(psiClassHelper.isNormalType(PsiTypesUtil.getClassType(userInfoPsiClass)))
    }

    fun testUnboxArrayOrList() {
        assertEquals("java.lang.String", psiClassHelper.unboxArrayOrList(modelPsiClass.fields[0].type).canonicalText)
        assertEquals("java.lang.Integer", psiClassHelper.unboxArrayOrList(modelPsiClass.fields[1].type).canonicalText)
        assertEquals("java.lang.String", psiClassHelper.unboxArrayOrList(modelPsiClass.fields[2].type).canonicalText)
        assertEquals("java.lang.Integer", psiClassHelper.unboxArrayOrList(modelPsiClass.fields[3].type).canonicalText)
    }

    fun testGetDefaultValue() {

        //check getDefaultValue of PsiClass
        assertEquals(emptyMap<Any, Any>(), psiClassHelper.getDefaultValue(objectPsiClass))
        assertEquals(0, psiClassHelper.getDefaultValue(integerPsiClass))
        assertEquals("", psiClassHelper.getDefaultValue(stringPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(collectionPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(mapPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(listPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(hashMapPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(linkedListPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(modelPsiClass))
        assertEquals(null, psiClassHelper.getDefaultValue(userInfoPsiClass))

        //check getDefaultValue of PsiType
        assertEquals(emptyMap<Any, Any>(), psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(objectPsiClass)))
        assertEquals(0, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(integerPsiClass)))
        assertEquals("", psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(stringPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(collectionPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(mapPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(listPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(hashMapPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(linkedListPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(modelPsiClass)))
        assertEquals(null, psiClassHelper.getDefaultValue(PsiTypesUtil.getClassType(userInfoPsiClass)))
    }

    fun testGetJsonFieldName() {
        assertEquals("s", psiClassHelper.getJsonFieldName(modelPsiClass.fields[0]))
        assertEquals("integer", psiClassHelper.getJsonFieldName(modelPsiClass.fields[1]))
    }

    fun testParseStaticFields() {
        assertEquals(
            "[{\"name\":\"ONE\",\"value\":\"1\",\"desc\":\"one\"},{\"name\":\"TWO\",\"value\":\"2\",\"desc\":\"two\"},{\"name\":\"THREE\",\"value\":\"3\",\"desc\":\"three\"},{\"name\":\"FOUR\",\"value\":\"4\",\"desc\":\"four\"}]",
            GsonUtils.toJson(psiClassHelper.parseStaticFields(numbersPsiClass))
        )
    }

    fun testParseEnumConstant() {
        assertEquals(
            "[{\"params\":{\"name\":\"0.9\",\"value\":1.5},\"name\":\"JAVA_0_9\",\"ordinal\":0,\"desc\":\"The Java version reported by Android. This is not an official Java version number.\"},{\"params\":{\"name\":\"1.1\",\"value\":1.1},\"name\":\"JAVA_1_1\",\"ordinal\":1,\"desc\":\"Java 1.1.\"},{\"params\":{\"name\":\"1.2\",\"value\":1.2},\"name\":\"JAVA_1_2\",\"ordinal\":2,\"desc\":\"Java 1.2.\"},{\"params\":{\"name\":\"1.3\",\"value\":1.3},\"name\":\"JAVA_1_3\",\"ordinal\":3,\"desc\":\"Java 1.3.\"},{\"params\":{\"name\":\"1.4\",\"value\":1.4},\"name\":\"JAVA_1_4\",\"ordinal\":4,\"desc\":\"Java 1.4.\"},{\"params\":{\"name\":\"1.5\",\"value\":1.5},\"name\":\"JAVA_1_5\",\"ordinal\":5,\"desc\":\"Java 1.5.\"},{\"params\":{\"name\":\"1.6\",\"value\":1.6},\"name\":\"JAVA_1_6\",\"ordinal\":6,\"desc\":\"Java 1.6.\"},{\"params\":{\"name\":\"1.7\",\"value\":1.7},\"name\":\"JAVA_1_7\",\"ordinal\":7,\"desc\":\"Java 1.7.\"},{\"params\":{\"name\":\"1.8\",\"value\":1.8},\"name\":\"JAVA_1_8\",\"ordinal\":8,\"desc\":\"Java 1.8.\"},{\"params\":{\"name\":\"9\",\"value\":9.0},\"name\":\"JAVA_1_9\",\"ordinal\":9,\"desc\":\"Java 1.9.\"},{\"params\":{\"name\":\"9\",\"value\":9.0},\"name\":\"JAVA_9\",\"ordinal\":10,\"desc\":\"Java 9\"},{\"params\":{\"name\":\"10\",\"value\":10.0},\"name\":\"JAVA_10\",\"ordinal\":11,\"desc\":\"Java 10\"},{\"params\":{\"name\":\"11\",\"value\":11.0},\"name\":\"JAVA_11\",\"ordinal\":12,\"desc\":\"Java 11\"},{\"params\":{\"name\":\"12\",\"value\":12.0},\"name\":\"JAVA_12\",\"ordinal\":13,\"desc\":\"Java 12\"},{\"params\":{\"name\":\"13\",\"value\":13.0},\"name\":\"JAVA_13\",\"ordinal\":14,\"desc\":\"Java 13\"}]",
            GsonUtils.toJson(psiClassHelper.parseEnumConstant(javaVersionPsiClass))
        )
    }

    fun testResolveEnumOrStatic() {
        assertEquals(
            "[{\"value\":\"JAVA_0_9\",\"desc\":\"The Java version reported by Android. This is not an official Java version number.\"},{\"value\":\"JAVA_1_1\",\"desc\":\"Java 1.1.\"},{\"value\":\"JAVA_1_2\",\"desc\":\"Java 1.2.\"},{\"value\":\"JAVA_1_3\",\"desc\":\"Java 1.3.\"},{\"value\":\"JAVA_1_4\",\"desc\":\"Java 1.4.\"},{\"value\":\"JAVA_1_5\",\"desc\":\"Java 1.5.\"},{\"value\":\"JAVA_1_6\",\"desc\":\"Java 1.6.\"},{\"value\":\"JAVA_1_7\",\"desc\":\"Java 1.7.\"},{\"value\":\"JAVA_1_8\",\"desc\":\"Java 1.8.\"},{\"value\":\"JAVA_1_9\",\"desc\":\"Java 1.9.\"},{\"value\":\"JAVA_9\",\"desc\":\"Java 9\"},{\"value\":\"JAVA_10\",\"desc\":\"Java 10\"},{\"value\":\"JAVA_11\",\"desc\":\"Java 11\"},{\"value\":\"JAVA_12\",\"desc\":\"Java 12\"},{\"value\":\"JAVA_13\",\"desc\":\"Java 13\"}]",
            GsonUtils.toJson(
                psiClassHelper.resolveEnumOrStatic(
                    "com.itangcent.constant.JavaVersion",
                    javaVersionPsiClass,
                    ""
                )
            )
        )

        assertEquals(
            "[{\"value\":\"0.9\",\"desc\":\"The Java version reported by Android. This is not an official Java version number.\"},{\"value\":\"1.1\",\"desc\":\"Java 1.1.\"},{\"value\":\"1.2\",\"desc\":\"Java 1.2.\"},{\"value\":\"1.3\",\"desc\":\"Java 1.3.\"},{\"value\":\"1.4\",\"desc\":\"Java 1.4.\"},{\"value\":\"1.5\",\"desc\":\"Java 1.5.\"},{\"value\":\"1.6\",\"desc\":\"Java 1.6.\"},{\"value\":\"1.7\",\"desc\":\"Java 1.7.\"},{\"value\":\"1.8\",\"desc\":\"Java 1.8.\"},{\"value\":\"9\",\"desc\":\"Java 1.9.\"},{\"value\":\"9\",\"desc\":\"Java 9\"},{\"value\":\"10\",\"desc\":\"Java 10\"},{\"value\":\"11\",\"desc\":\"Java 11\"},{\"value\":\"12\",\"desc\":\"Java 12\"},{\"value\":\"13\",\"desc\":\"Java 13\"}]",
            GsonUtils.toJson(
                psiClassHelper.resolveEnumOrStatic(
                    "com.itangcent.constant.JavaVersion",
                    javaVersionPsiClass,
                    "name"
                )
            )
        )
        assertEquals(
            "[{\"value\":1.5,\"desc\":\"The Java version reported by Android. This is not an official Java version number.\"},{\"value\":1.1,\"desc\":\"Java 1.1.\"},{\"value\":1.2,\"desc\":\"Java 1.2.\"},{\"value\":1.3,\"desc\":\"Java 1.3.\"},{\"value\":1.4,\"desc\":\"Java 1.4.\"},{\"value\":1.5,\"desc\":\"Java 1.5.\"},{\"value\":1.6,\"desc\":\"Java 1.6.\"},{\"value\":1.7,\"desc\":\"Java 1.7.\"},{\"value\":1.8,\"desc\":\"Java 1.8.\"},{\"value\":9.0,\"desc\":\"Java 1.9.\"},{\"value\":9.0,\"desc\":\"Java 9\"},{\"value\":10.0,\"desc\":\"Java 10\"},{\"value\":11.0,\"desc\":\"Java 11\"},{\"value\":12.0,\"desc\":\"Java 12\"},{\"value\":13.0,\"desc\":\"Java 13\"}]",
            GsonUtils.toJson(
                psiClassHelper.resolveEnumOrStatic(
                    "com.itangcent.constant.JavaVersion",
                    javaVersionPsiClass,
                    "value"
                )
            )
        )

        //

        assertEquals(
            "[{\"value\":\"0.9\",\"desc\":\"The Java version reported by Android. This is not an official Java version number.\"},{\"value\":\"1.1\",\"desc\":\"Java 1.1.\"},{\"value\":\"1.2\",\"desc\":\"Java 1.2.\"},{\"value\":\"1.3\",\"desc\":\"Java 1.3.\"},{\"value\":\"1.4\",\"desc\":\"Java 1.4.\"},{\"value\":\"1.5\",\"desc\":\"Java 1.5.\"},{\"value\":\"1.6\",\"desc\":\"Java 1.6.\"},{\"value\":\"1.7\",\"desc\":\"Java 1.7.\"},{\"value\":\"1.8\",\"desc\":\"Java 1.8.\"},{\"value\":\"9\",\"desc\":\"Java 1.9.\"},{\"value\":\"9\",\"desc\":\"Java 9\"},{\"value\":\"10\",\"desc\":\"Java 10\"},{\"value\":\"11\",\"desc\":\"Java 11\"},{\"value\":\"12\",\"desc\":\"Java 12\"},{\"value\":\"13\",\"desc\":\"Java 13\"}]",
            GsonUtils.toJson(
                psiClassHelper.resolveEnumOrStatic(
                    javaVersionPsiClass, javaVersionPsiClass,
                    "name",
                    "name"
                )
            )
        )

        assertEquals(
            "[{\"value\":\"1\",\"desc\":\"one\"},{\"value\":\"2\",\"desc\":\"two\"},{\"value\":\"3\",\"desc\":\"three\"},{\"value\":\"4\",\"desc\":\"four\"}]",
            GsonUtils.toJson(
                psiClassHelper.resolveEnumOrStatic(
                    numbersPsiClass, numbersPsiClass,
                    "",
                    ""
                )
            )
        )
    }

}