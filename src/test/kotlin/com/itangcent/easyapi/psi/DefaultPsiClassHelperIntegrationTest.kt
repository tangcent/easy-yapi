package com.itangcent.easyapi.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelValueConverter
import com.itangcent.easyapi.psi.type.JsonType
import kotlinx.coroutines.runBlocking

class DefaultPsiClassHelperIntegrationTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultPsiClassHelper

    override fun setUp() {
        super.setUp()
        helper = DefaultPsiClassHelper()
    }

    private fun actionContext(config: ConfigReader = emptyConfig()): ActionContext {
        return ActionContext.builder()
            .bind(Project::class, project)
            .bind(ConfigReader::class, config)
            .withSpiBindings().build()
    }

    private fun emptyConfig(): ConfigReader = listConfig(emptyMap())

    private fun listConfig(map: Map<String, List<String>>): ConfigReader {
        return object : ConfigReader {
            override fun getFirst(key: String): String? = map[key]?.lastOrNull()
            override fun getAll(key: String): List<String> = map[key].orEmpty()
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
                println("DEBUG listConfig.foreach: map=$map")
                map.forEach { (key, values) ->
                    println("DEBUG listConfig.foreach: key='$key', keyFilter(key)=${keyFilter(key)}")
                    if (keyFilter(key)) {
                        values.forEach { value -> 
                            println("DEBUG listConfig.foreach: action('$key', '$value')")
                            action(key, value) 
                        }
                    }
                }
            }
        }
    }

    fun testBuildObjectModelForSimpleClass() = runBlocking {
        myFixture.addFileToProject(
            "model/SimpleModel.java",
            """
            package model;
            public class SimpleModel {
                public String name;
                public int age;
                public boolean active;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.SimpleModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["name"])
        assertEquals(0, map["age"])
        assertEquals(false, map["active"])
    }

    fun testBuildObjectModelWithNestedObject() = runBlocking {
        myFixture.addFileToProject(
            "model/Address.java",
            """
            package model;
            public class Address {
                public String street;
                public String city;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Person.java",
            """
            package model;
            public class Person {
                public String name;
                public Address address;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Person")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["name"])
        assertTrue(map["address"] is Map<*, *>)
        val address = map["address"] as Map<*, *>
        assertEquals("", address["street"])
        assertEquals("", address["city"])
    }

    fun testBuildObjectModelWithListField() = runBlocking {
        myFixture.addFileToProject(
            "model/ItemList.java",
            """
            package model;
            import java.util.List;
            public class ItemList {
                public List<String> items;
                public List<Integer> numbers;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.ItemList")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["items"] is List<*>)
        assertTrue((map["items"] as List<*>).isEmpty())
        assertTrue(map["numbers"] is List<*>)
    }

    fun testBuildObjectModelWithMapField() = runBlocking {
        myFixture.addFileToProject(
            "model/Config.java",
            """
            package model;
            import java.util.Map;
            public class Config {
                public Map<String, Object> properties;
                public Map<String, String> headers;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Config")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["properties"] is Map<*, *>)
        assertTrue((map["properties"] as Map<*, *>).isEmpty())
        assertTrue(map["headers"] is Map<*, *>)
    }

    fun testBuildObjectModelWithPrimitiveTypes() = runBlocking {
        myFixture.addFileToProject(
            "model/PrimitiveTypes.java",
            """
            package model;
            public class PrimitiveTypes {
                public boolean boolVal;
                public byte byteVal;
                public char charVal;
                public short shortVal;
                public int intVal;
                public long longVal;
                public float floatVal;
                public double doubleVal;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.PrimitiveTypes")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(false, map["boolVal"])
        assertEquals(0, map["byteVal"])
        assertEquals("", map["charVal"])
        assertEquals(0, map["shortVal"])
        assertEquals(0, map["intVal"])
        assertEquals(0L, map["longVal"])
        assertEquals(0.0f, map["floatVal"])
        assertEquals(0.0, map["doubleVal"])
    }

    fun testBuildObjectModelWithArrayField() = runBlocking {
        myFixture.addFileToProject(
            "model/ArrayModel.java",
            """
            package model;
            public class ArrayModel {
                public String[] strings;
                public int[] ints;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.ArrayModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["strings"] is List<*>)
        assertTrue((map["strings"] as List<*>).isNotEmpty())
        assertTrue(map["ints"] is List<*>)
    }

    fun testBuildObjectModelWithEnum() = runBlocking {
        myFixture.addFileToProject(
            "model/Status.java",
            """
            package model;
            public enum Status {
                ACTIVE, INACTIVE, PENDING
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/EnumModel.java",
            """
            package model;
            public class EnumModel {
                public Status status;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.EnumModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["status"])
    }

    fun testBuildObjectModelWithInheritance() = runBlocking {
        myFixture.addFileToProject(
            "model/BaseModel.java",
            """
            package model;
            public class BaseModel {
                public String id;
                public long createTime;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/UserModel.java",
            """
            package model;
            public class UserModel extends BaseModel {
                public String name;
                public int age;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.UserModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["id"])
        assertEquals(0L, map["createTime"])
        assertEquals("", map["name"])
        assertEquals(0, map["age"])
    }

    fun testBuildObjectModelWithStaticFieldsIgnored() = runBlocking {
        myFixture.addFileToProject(
            "model/StaticModel.java",
            """
            package model;
            public class StaticModel {
                public static String CONSTANT = "constant";
                public String name;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.StaticModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertFalse(map.containsKey("CONSTANT"))
        assertEquals("", map["name"])
    }

    fun testBuildObjectModelWithMaxDepth() = runBlocking {
        myFixture.addFileToProject(
            "model/RecursiveNode.java",
            """
            package model;
            public class RecursiveNode {
                public String value;
                public RecursiveNode next;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.RecursiveNode")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 3)
        
        assertNotNull(result)
        var current = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>?
        var depth = 0
        while (current != null && current.containsKey("next")) {
            depth++
            current = current["next"] as Map<*, *>?
        }
        assertTrue(depth <= 3)
    }

    fun testBuildObjectModelWithEmptyClass() = runBlocking {
        myFixture.addFileToProject(
            "model/EmptyModel.java",
            """
            package model;
            public class EmptyModel {
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.EmptyModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map.isEmpty())
    }

    fun testBuildObjectModelWithBooleanGetter() = runBlocking {
        myFixture.addFileToProject(
            "model/BooleanModel.java",
            """
            package model;
            public class BooleanModel {
                private boolean active;
                private boolean enabled;
                
                public boolean isActive() { return active; }
                public void setActive(boolean active) { this.active = active; }
                
                public boolean isEnabled() { return enabled; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.BooleanModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(false, map["active"])
        assertEquals(false, map["enabled"])
    }

    fun testBuildObjectModelWithComplexNestedStructure() = runBlocking {
        myFixture.addFileToProject(
            "model/Order.java",
            """
            package model;
            import java.util.List;
            public class Order {
                public String orderId;
                public Customer customer;
                public List<OrderItem> items;
                public double totalAmount;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Customer.java",
            """
            package model;
            public class Customer {
                public String customerId;
                public String name;
                public String email;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/OrderItem.java",
            """
            package model;
            public class OrderItem {
                public String productId;
                public String productName;
                public int quantity;
                public double price;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Order")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["orderId"])
        assertEquals(0.0, map["totalAmount"])
        
        assertTrue(map["customer"] is Map<*, *>)
        val customer = map["customer"] as Map<*, *>
        assertEquals("", customer["customerId"])
        assertEquals("", customer["name"])
        assertEquals("", customer["email"])
        
        assertTrue(map["items"] is List<*>)
    }

    fun testBuildObjectModelWithGenericFields() = runBlocking {
        myFixture.addFileToProject(
            "model/GenericModel.java",
            """
            package model;
            import java.util.List;
            import java.util.Map;
            public class GenericModel {
                public List<String> stringList;
                public Map<String, Integer> stringIntMap;
                public List<Map<String, String>> complexList;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.GenericModel")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["stringList"] is List<*>)
        assertTrue(map["stringIntMap"] is Map<*, *>)
        assertTrue(map["complexList"] is List<*>)
    }

    fun testBuildObjectModelWithWrapperTypes() = runBlocking {
        myFixture.addFileToProject(
            "model/WrapperTypes.java",
            """
            package model;
            public class WrapperTypes {
                public Integer integerVal;
                public Long longVal;
                public Double doubleVal;
                public Boolean booleanVal;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.WrapperTypes")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(0, map["integerVal"])
        assertEquals(0L, map["longVal"])
        assertEquals(0.0, map["doubleVal"])
        assertEquals(false, map["booleanVal"])
    }

    fun testBuildObjectModelWithGenericClass() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T data;
                public String name;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Xxx")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["data"] is Map<*, *>)
        assertTrue((map["data"] as Map<*, *>).isEmpty())
        assertEquals("", map["name"])
    }

    fun testBuildObjectModelWithGenericClassTyped() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T data;
                public String name;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/XxxString.java",
            """
            package model;
            public class XxxString extends Xxx<String> {
                public String extra;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.XxxString")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        // T is resolved to String via inheritance
        assertEquals("", map["data"])
        assertEquals("", map["name"])
        assertEquals("", map["extra"])
    }

    fun testBuildObjectModelWithClassExtendsGenericWithConcreteType() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T value;
                public String name;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/InnerData.java",
            """
            package model;
            public class InnerData {
                public String field1;
                public int field2;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Yyy.java",
            """
            package model;
            public class Yyy extends Xxx<InnerData> {
                public String yyyField;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Yyy")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        // T is resolved to InnerData via inheritance
        assertTrue(map["value"] is Map<*, *>)
        val innerData = map["value"] as Map<*, *>
        assertEquals("", innerData["field1"])
        assertEquals(0, innerData["field2"])
        assertEquals("", map["name"])
        assertEquals("", map["yyyField"])
    }

    fun testBuildObjectModelWithClassImplementsGenericWithConcreteType() = runBlocking {
        myFixture.addFileToProject(
            "model/XxxInterface.java",
            """
            package model;
            public interface XxxInterface<T> {
                T getData();
                void setData(T data);
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/YyyImpl.java",
            """
            package model;
            public class YyyImpl implements XxxInterface<String> {
                public String data;
                public String name;
                
                @Override
                public String getData() { return data; }
                @Override
                public void setData(String data) { this.data = data; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.YyyImpl")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["data"])
        assertEquals("", map["name"])
    }

    fun testBuildObjectModelWithGenericClassExtendsGenericClass() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T value;
                public String baseName;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/YyyGeneric.java",
            """
            package model;
            public class YyyGeneric<B> extends Xxx<B> {
                public B extraValue;
                public String yyyName;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.YyyGeneric")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["value"] is Map<*, *>)
        assertTrue((map["value"] as Map<*, *>).isEmpty())
        assertTrue(map["extraValue"] is Map<*, *>)
        assertTrue((map["extraValue"] as Map<*, *>).isEmpty())
        assertEquals("", map["baseName"])
        assertEquals("", map["yyyName"])
    }

    fun testBuildObjectModelWithGenericClassImplementsGenericInterface() = runBlocking {
        myFixture.addFileToProject(
            "model/XxxInterface.java",
            """
            package model;
            public interface XxxInterface<T> {
                T getValue();
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/YyyGenericImpl.java",
            """
            package model;
            public class YyyGenericImpl<B> implements XxxInterface<B> {
                public B value;
                public String name;
                
                @Override
                public B getValue() { return value; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.YyyGenericImpl")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["value"] is Map<*, *>)
        assertTrue((map["value"] as Map<*, *>).isEmpty())
        assertEquals("", map["name"])
    }

    fun testBuildObjectModelWithConcreteClassExtendsGenericTyped() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T value;
                public String baseName;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/YyyGeneric.java",
            """
            package model;
            public class YyyGeneric<B> extends Xxx<B> {
                public B extraValue;
                public String yyyName;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Zzz.java",
            """
            package model;
            public class Zzz extends YyyGeneric<Integer> {
                public String zzzField;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Zzz")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        // B→Integer, T→B→Integer via multi-level inheritance
        assertEquals(0, map["value"])
        assertEquals(0, map["extraValue"])
        assertEquals("", map["baseName"])
        assertEquals("", map["yyyName"])
        assertEquals("", map["zzzField"])
    }

    fun testBuildObjectModelWithConcreteClassImplementsGenericTyped() = runBlocking {
        myFixture.addFileToProject(
            "model/XxxInterface.java",
            """
            package model;
            public interface XxxInterface<T> {
                T getValue();
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/YyyGenericInterface.java",
            """
            package model;
            public interface YyyGenericInterface<B> extends XxxInterface<B> {
                B getExtra();
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/ZzzImpl.java",
            """
            package model;
            public class ZzzImpl implements YyyGenericInterface<Long> {
                public Long value;
                public Long extra;
                public String name;
                
                @Override
                public Long getValue() { return value; }
                @Override
                public Long getExtra() { return extra; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.ZzzImpl")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(0L, map["value"])
        assertEquals(0L, map["extra"])
        assertEquals("", map["name"])
    }

    fun testBuildObjectModelWithDeepGenericInheritance() = runBlocking {
        myFixture.addFileToProject(
            "model/BaseEntity.java",
            """
            package model;
            public class BaseEntity<T> {
                public T id;
                public long createTime;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/NamedEntity.java",
            """
            package model;
            public class NamedEntity<N> extends BaseEntity<N> {
                public String name;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/UserEntity.java",
            """
            package model;
            public class UserEntity extends NamedEntity<Long> {
                public String email;
                public int age;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.UserEntity")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        // N→Long, T→N→Long via multi-level inheritance
        assertEquals(0L, map["id"])
        assertEquals(0L, map["createTime"])
        assertEquals("", map["name"])
        assertEquals("", map["email"])
        assertEquals(0, map["age"])
    }

    fun testBuildObjectModelWithListContainingGenericClass() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T data;
                public String name;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/ListContainer.java",
            """
            package model;
            import java.util.List;
            public class ListContainer {
                public List<Xxx<String>> items;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.ListContainer")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["items"] is List<*>)
    }

    fun testBuildObjectModelWithMapContainingGenericClass() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T data;
                public String name;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/MapContainer.java",
            """
            package model;
            import java.util.Map;
            public class MapContainer {
                public Map<String, Xxx<Integer>> dataMap;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.MapContainer")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["dataMap"] is Map<*, *>)
    }

    fun testBuildObjectModelWithComplexGenericScenario() = runBlocking {
        myFixture.addFileToProject(
            "model/Result.java",
            """
            package model;
            public class Result<T> {
                public int code;
                public String message;
                public T data;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/PageData.java",
            """
            package model;
            import java.util.List;
            public class PageData<E> {
                public List<E> list;
                public long total;
                public int pageNum;
                public int pageSize;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/UserInfo.java",
            """
            package model;
            public class UserInfo {
                public Long id;
                public String username;
                public String email;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/UserPageResult.java",
            """
            package model;
            public class UserPageResult extends Result<PageData<UserInfo>> {
                public String requestId;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.UserPageResult")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(0, map["code"])
        assertEquals("", map["message"])
        assertEquals("", map["requestId"])
        // T is resolved to PageData<UserInfo> via inheritance
        assertTrue(map["data"] is Map<*, *>)
        val pageData = map["data"] as Map<*, *>
        assertTrue(pageData["list"] is List<*>)
        assertEquals(0L, pageData["total"])
        assertEquals(0, pageData["pageNum"])
        assertEquals(0, pageData["pageSize"])
    }

    fun testBuildObjectModelWithMultipleGenericInterfaces() = runBlocking {
        myFixture.addFileToProject(
            "model/Identifiable.java",
            """
            package model;
            public interface Identifiable<I> {
                I getId();
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Named.java",
            """
            package model;
            public interface Named {
                String getName();
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Timestamped.java",
            """
            package model;
            public interface Timestamped {
                long getCreatedAt();
                long getUpdatedAt();
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Document.java",
            """
            package model;
            public class Document implements Identifiable<String>, Named, Timestamped {
                public String id;
                public String name;
                public long createdAt;
                public long updatedAt;
                public String content;
                
                @Override
                public String getId() { return id; }
                @Override
                public String getName() { return name; }
                @Override
                public long getCreatedAt() { return createdAt; }
                @Override
                public long getUpdatedAt() { return updatedAt; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Document")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["id"])
        assertEquals("", map["name"])
        assertEquals(0L, map["createdAt"])
        assertEquals(0L, map["updatedAt"])
        assertEquals("", map["content"])
    }

    fun testBuildObjectModelWithGenericArray() = runBlocking {
        myFixture.addFileToProject(
            "model/Xxx.java",
            """
            package model;
            public class Xxx<T> {
                public T data;
                public String name;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/GenericArrayHolder.java",
            """
            package model;
            public class GenericArrayHolder {
                public Xxx<String>[] stringItems;
                public Xxx<Integer>[] intItems;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.GenericArrayHolder")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertTrue(map["stringItems"] is List<*>)
        assertTrue(map["intItems"] is List<*>)
    }

    fun testBuildObjectModelWithNestedGenericTypes() = runBlocking {
        myFixture.addFileToProject(
            "model/Container.java",
            """
            package model;
            public class Container<A> {
                public A value;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Wrapper.java",
            """
            package model;
            public class Wrapper<B> {
                public Container<B> container;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/DeepWrapper.java",
            """
            package model;
            public class DeepWrapper<C> extends Wrapper<C> {
                public String extra;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/ConcreteDeepWrapper.java",
            """
            package model;
            public class ConcreteDeepWrapper extends DeepWrapper<String> {
                public String concreteField;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.ConcreteDeepWrapper")!!
        val ctx = actionContext()
        
        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)
        
        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        // C→String resolved through DeepWrapper→Wrapper→Container
        assertTrue(map["container"] is Map<*, *>)
        val container = map["container"] as Map<*, *>
        // A (Container's type param) → B → C → String
        assertEquals("", container["value"])
        assertEquals("", map["extra"])
        assertEquals("", map["concreteField"])
    }

    private fun findClass(fqn: String): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
    }

    fun testBuildObjectModelWithFieldDocRule() = runBlocking {
        myFixture.addFileToProject(
            "model/CommentModel.java",
            """
            package model;
            public class CommentModel {
                /**
                 * The user name
                 */
                public String name;
                /**
                 * The user age
                 */
                public int age;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.CommentModel")!!
        val config = listConfig(mapOf(
            "field.doc" to listOf("groovy:it.doc()")
        ))
        val ctx = actionContext(config)
        
        val result = helper.buildObjectModel(psiClass, ctx, JsonOption.ALL, 10)
        
        assertNotNull(result)
        assertTrue(result is ObjectModel.Object)
        val obj = result as ObjectModel.Object
        
        val nameField = obj.fields["name"]
        assertNotNull(nameField)
        assertTrue(nameField!!.model is ObjectModel.Single)
        assertEquals(JsonType.STRING, (nameField.model as ObjectModel.Single).type)
        assertTrue(nameField.comment?.contains("The user name") == true)
        
        val ageField = obj.fields["age"]
        assertNotNull(ageField)
        assertTrue(ageField!!.model is ObjectModel.Single)
        assertEquals(JsonType.INT, (ageField.model as ObjectModel.Single).type)
        assertTrue(ageField.comment?.contains("The user age") == true)
    }

    fun testBuildObjectModelWithFieldDocFromAnnotation() = runBlocking {
        myFixture.addFileToProject(
            "model/ApiModel.java",
            """
            package model;
            public @interface ApiModelProperty {
                String value() default "";
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/AnnotatedModel.java",
            """
            package model;
            public class AnnotatedModel {
                @ApiModelProperty("The user name")
                public String name;
                
                @ApiModelProperty("The user age")
                public int age;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.AnnotatedModel")!!
        val config = listConfig(mapOf(
            "field.doc" to listOf("@model.ApiModelProperty#value")
        ))
        val ctx = actionContext(config)
        
        val result = helper.buildObjectModel(psiClass, ctx, JsonOption.ALL, 10)
        
        assertNotNull(result)
        assertTrue(result is ObjectModel.Object)
        val obj = result as ObjectModel.Object
        
        val nameField = obj.fields["name"]
        assertNotNull(nameField)
        assertTrue(nameField!!.model is ObjectModel.Single)
        assertEquals(JsonType.STRING, (nameField.model as ObjectModel.Single).type)
        assertTrue(nameField.comment?.contains("The user name") == true)
        
        val ageField = obj.fields["age"]
        assertNotNull(ageField)
        assertTrue(ageField!!.model is ObjectModel.Single)
        assertEquals(JsonType.INT, (ageField.model as ObjectModel.Single).type)
        assertTrue(ageField.comment?.contains("The user age") == true)
    }

    fun testBuildObjectModelWithFieldDocAndJson5Formatter() = runBlocking {
        myFixture.addFileToProject(
            "model/UserModel.java",
            """
            package model;
            public class UserModel {
                /**
                 * The unique identifier
                 */
                public String id;
                /**
                 * The display name
                 */
                public String name;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.UserModel")!!
        val config = listConfig(mapOf(
            "field.doc" to listOf("groovy:it.doc()")
        ))
        val ctx = actionContext(config)
        
        val result = helper.buildObjectModel(psiClass, ctx, JsonOption.ALL, 10)
        
        assertNotNull(result)
        assertTrue(result is ObjectModel.Object)
        val obj = result as ObjectModel.Object
        
        val idField = obj.fields["id"]
        assertNotNull(idField)
        assertTrue(idField!!.model is ObjectModel.Single)
        assertEquals(JsonType.STRING, (idField.model as ObjectModel.Single).type)
        assertTrue(idField.comment?.contains("The unique identifier") == true)
        
        val nameField = obj.fields["name"]
        assertNotNull(nameField)
        assertTrue(nameField!!.model is ObjectModel.Single)
        assertEquals(JsonType.STRING, (nameField.model as ObjectModel.Single).type)
        assertTrue(nameField.comment?.contains("The display name") == true)
    }

    fun testGenericFieldFlagOnTypeParameter() = runBlocking {
        myFixture.addFileToProject(
            "model/GenericResult.java",
            """
            package model;
            public class GenericResult<T> {
                private int code;
                private String msg;
                private T data;
                public int getCode() { return code; }
                public void setCode(int code) { this.code = code; }
                public String getMsg() { return msg; }
                public void setMsg(String msg) { this.msg = msg; }
                public T getData() { return data; }
                public void setData(T data) { this.data = data; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.GenericResult")!!
        val ctx = actionContext()

        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)

        assertNotNull(result)
        assertTrue(result is ObjectModel.Object)
        val obj = result as ObjectModel.Object

        // "data" field is declared as T — should be marked generic
        val dataField = obj.fields["data"]
        assertNotNull(dataField)
        assertTrue("data field should be marked as generic", dataField!!.generic)

        // "code" and "msg" are concrete types — should NOT be generic
        val codeField = obj.fields["code"]
        assertNotNull(codeField)
        assertFalse("code field should not be generic", codeField!!.generic)

        val msgField = obj.fields["msg"]
        assertNotNull(msgField)
        assertFalse("msg field should not be generic", msgField!!.generic)
    }

    fun testGenericFieldFlagNotSetOnConcreteSubclass() = runBlocking {
        myFixture.addFileToProject(
            "model/GenericResult.java",
            """
            package model;
            public class GenericResult<T> {
                private int code;
                private T data;
                public int getCode() { return code; }
                public void setCode(int code) { this.code = code; }
                public T getData() { return data; }
                public void setData(T data) { this.data = data; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/StringResult.java",
            """
            package model;
            public class StringResult extends GenericResult<String> {
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.StringResult")!!
        val ctx = actionContext()

        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)

        assertNotNull(result)
        assertTrue(result is ObjectModel.Object)
        val obj = result as ObjectModel.Object

        // In StringResult, T is resolved to String — the field is no longer generic
        val dataField = obj.fields["data"]
        assertNotNull(dataField)
        assertFalse("data field in concrete subclass should not be generic", dataField!!.generic)
    }

    fun testNoGenericFieldsInPlainClass() = runBlocking {
        myFixture.addFileToProject(
            "model/PlainDto.java",
            """
            package model;
            public class PlainDto {
                private String name;
                private int age;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public int getAge() { return age; }
                public void setAge(int age) { this.age = age; }
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.PlainDto")!!
        val ctx = actionContext()

        val result = helper.buildObjectModel(psiClass, ctx, maxDepth = 10)

        assertNotNull(result)
        assertTrue(result is ObjectModel.Object)
        val obj = result as ObjectModel.Object

        for ((name, field) in obj.fields) {
            assertFalse("field '$name' in plain class should not be generic", field.generic)
        }
    }
}
