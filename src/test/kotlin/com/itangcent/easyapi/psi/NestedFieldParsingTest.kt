package com.itangcent.easyapi.psi

import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.psi.model.ObjectModelValueConverter
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

/**
 * Tests for nested/recursive field parsing in DefaultPsiClassHelper.
 *
 * Verifies that circular references and deeply nested structures
 * are handled correctly without causing OOM or infinite recursion.
 *
 * Related: https://github.com/tangcent/easy-yapi/issues/1325
 */
class NestedFieldParsingTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultPsiClassHelper

    override fun setUp() {
        super.setUp()
        helper = DefaultPsiClassHelper.getInstance(project)
    }

    // ---- Self-referencing (A → A) ----

    fun testSelfReferencingClass() = runBlocking {
        myFixture.addFileToProject(
            "model/TreeNode.java",
            """
            package model;
            public class TreeNode {
                public String name;
                public TreeNode parent;
                public TreeNode left;
                public TreeNode right;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.TreeNode")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Self-referencing class should produce a model", result)
        val obj = result as ObjectModel.Object
        assertTrue("Should have 'name' field", obj.fields.containsKey("name"))
        // parent/left/right should be null (circular reference detected)
        // because TreeNode is already in the visited set when processing its own fields
        val parentField = obj.fields["parent"]
        assertNotNull("Should have 'parent' field", parentField)
    }

    fun testSelfReferencingClassDoesNotOOM() = runBlocking {
        myFixture.addFileToProject(
            "model/LinkedNode.java",
            """
            package model;
            public class LinkedNode {
                public String value;
                public LinkedNode next;
                public LinkedNode prev;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.LinkedNode")!!

        // Should complete without OOM even with high maxDepth
        val result = helper.buildObjectModel(psiClass)

        assertNotNull(result)
        // Verify the model is finite by converting to JSON
        val json = ObjectModelJsonConverter.toJson5(result)
        assertNotNull(json)
        assertTrue("JSON should not be excessively large", json.length < 10000)
    }

    // ---- Mutual circular reference (A → B → A) ----

    fun testMutualCircularReference() = runBlocking {
        myFixture.addFileToProject(
            "model/Department.java",
            """
            package model;
            import java.util.List;
            public class Department {
                public String name;
                public List<Employee> employees;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Employee.java",
            """
            package model;
            import model.Department;
            public class Employee {
                public String name;
                public Department department;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Department")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Mutual circular reference should produce a model", result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["name"])
        assertTrue("Should have employees list", map["employees"] is List<*>)
    }

    fun testMutualCircularReferenceReverse() = runBlocking {
        myFixture.addFileToProject(
            "model/Department2.java",
            """
            package model;
            import java.util.List;
            public class Department2 {
                public String deptName;
                public List<Employee2> members;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Employee2.java",
            """
            package model;
            import model.Department2;
            public class Employee2 {
                public String empName;
                public Department2 dept;
            }
            """.trimIndent()
        )
        // Start from Employee side
        val psiClass = findClass("model.Employee2")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Should produce a model starting from Employee side", result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["empName"])
        assertTrue("Should have dept field", map["dept"] is Map<*, *>)
        val dept = map["dept"] as Map<*, *>
        assertEquals("", dept["deptName"])
    }

    // ---- Indirect circular reference (A → B → C → A) ----

    fun testIndirectCircularReference() = runBlocking {
        myFixture.addFileToProject(
            "model/Company.java",
            """
            package model;
            import model.Team;
            public class Company {
                public String companyName;
                public Team mainTeam;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Team.java",
            """
            package model;
            import model.Member;
            public class Team {
                public String teamName;
                public Member leader;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Member.java",
            """
            package model;
            import model.Company;
            public class Member {
                public String memberName;
                public Company employer;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Company")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Indirect circular reference should produce a model", result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["companyName"])
        assertTrue("Should have mainTeam", map["mainTeam"] is Map<*, *>)
        val team = map["mainTeam"] as Map<*, *>
        assertEquals("", team["teamName"])
        assertTrue("Should have leader", team["leader"] is Map<*, *>)
        val leader = team["leader"] as Map<*, *>
        assertEquals("", leader["memberName"])
        // employer should be null or empty map (circular reference back to Company)
    }

    // ---- Circular reference through collections ----

    fun testCircularReferenceThroughList() = runBlocking {
        myFixture.addFileToProject(
            "model/Category.java",
            """
            package model;
            import java.util.List;
            public class Category {
                public String name;
                public Category parentCategory;
                public List<Category> subCategories;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Category")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Circular reference through list should produce a model", result)
        val json = ObjectModelJsonConverter.toJson5(result)
        assertNotNull(json)
        assertTrue("JSON should be finite", json.length < 10000)
    }

    // ---- Multiple circular paths ----

    fun testMultipleCircularPaths() = runBlocking {
        myFixture.addFileToProject(
            "model/GraphNode.java",
            """
            package model;
            import java.util.List;
            import model.GraphEdge;
            public class GraphNode {
                public String id;
                public List<GraphEdge> inEdges;
                public List<GraphEdge> outEdges;
                public GraphNode parent;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/GraphEdge.java",
            """
            package model;
            import model.GraphNode;
            public class GraphEdge {
                public String label;
                public GraphNode source;
                public GraphNode target;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.GraphNode")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Multiple circular paths should produce a model", result)
        val json = ObjectModelJsonConverter.toJson5(result)
        assertNotNull(json)
        assertTrue("JSON should be finite", json.length < 50000)
    }

    // ---- Circular reference with generics ----

    fun testCircularReferenceWithGenericWrapper() = runBlocking {
        myFixture.addFileToProject(
            "model/Wrapper.java",
            """
            package model;
            public class Wrapper<T> {
                public T data;
                public String info;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/SelfWrapped.java",
            """
            package model;
            import model.Wrapper;
            public class SelfWrapped {
                public String name;
                public Wrapper<SelfWrapped> wrapped;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.SelfWrapped")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Circular reference with generic wrapper should produce a model", result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["name"])
        assertTrue("Should have wrapped field", map["wrapped"] is Map<*, *>)
    }

    // ---- Complex real-world-like scenario (ChannelDTO-like) ----

    fun testComplexDtoWithCircularReferences() = runBlocking {
        myFixture.addFileToProject(
            "model/ChannelDTO.java",
            """
            package model;
            import java.util.List;
            import model.SubChannelDTO;
            public class ChannelDTO {
                public Long id;
                public String channelName;
                public String channelCode;
                public Integer status;
                public Long parentId;
                public ChannelDTO parentChannel;
                public List<SubChannelDTO> subChannels;
                public String description;
                public Long createTime;
                public Long updateTime;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/SubChannelDTO.java",
            """
            package model;
            import model.ChannelDTO;
            public class SubChannelDTO {
                public Long id;
                public String subChannelName;
                public ChannelDTO ownerChannel;
                public Integer priority;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.ChannelDTO")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Complex DTO with circular references should produce a model", result)
        val json = ObjectModelJsonConverter.toJson5(result)
        assertNotNull(json)
        // The JSON should be reasonably sized, not exploding due to circular references
        assertTrue(
            "JSON should not be excessively large (was ${json.length} chars)",
            json.length < 50000
        )
    }

    // ---- Deep inheritance with circular reference ----

    fun testDeepInheritanceWithCircularReference() = runBlocking {
        myFixture.addFileToProject(
            "model/BaseEntity.java",
            """
            package model;
            public class BaseEntity {
                public Long id;
                public Long createTime;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Organization.java",
            """
            package model;
            import model.BaseEntity;
            import model.OrganizationMember;
            import java.util.List;
            public class Organization extends BaseEntity {
                public String orgName;
                public Organization parentOrg;
                public List<Organization> childOrgs;
                public List<OrganizationMember> members;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/OrganizationMember.java",
            """
            package model;
            import model.BaseEntity;
            import model.Organization;
            public class OrganizationMember extends BaseEntity {
                public String memberName;
                public Organization organization;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Organization")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Deep inheritance with circular reference should produce a model", result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(0L, map["id"])
        assertEquals("", map["orgName"])
    }

    // ---- Verify ObjectModelValueConverter handles circular ObjectModel references ----

    fun testValueConverterHandlesCircularObjectModel() {
        // Manually create a circular ObjectModel to verify the converter handles it
        val fields = linkedMapOf<String, com.itangcent.easyapi.psi.model.FieldModel>()
        val obj = ObjectModel.Object(fields)
        // Add a field that references the same object (simulating circular reference from cache)
        fields["self"] = com.itangcent.easyapi.psi.model.FieldModel(model = obj)
        fields["name"] = com.itangcent.easyapi.psi.model.FieldModel(
            model = ObjectModel.single(com.itangcent.easyapi.psi.type.JsonType.STRING)
        )

        // Should not stack overflow
        val value = ObjectModelValueConverter.toSimpleValue(obj)
        assertNotNull(value)
        assertTrue(value is Map<*, *>)
        val map = value as Map<*, *>
        assertEquals("", map["name"])
        // The self-referencing field should be an empty map (circular reference detected)
        assertTrue("Self-referencing field should be empty map", map["self"] is Map<*, *>)
        assertTrue(
            "Self-referencing field should be empty",
            (map["self"] as Map<*, *>).isEmpty()
        )
    }

    // ---- Verify JSON builder handles circular ObjectModel references ----

    fun testJsonBuilderHandlesCircularObjectModel() {
        val fields = linkedMapOf<String, com.itangcent.easyapi.psi.model.FieldModel>()
        val obj = ObjectModel.Object(fields)
        fields["self"] = com.itangcent.easyapi.psi.model.FieldModel(model = obj)
        fields["name"] = com.itangcent.easyapi.psi.model.FieldModel(
            model = ObjectModel.single(com.itangcent.easyapi.psi.type.JsonType.STRING)
        )

        // Should not stack overflow
        val json = ObjectModelJsonConverter.toJson5(obj)
        assertNotNull(json)
        assertTrue("JSON should be finite", json.length < 10000)
    }

    // ---- Wide class with many fields and circular reference ----

    fun testWideClassWithCircularReference() = runBlocking {
        myFixture.addFileToProject(
            "model/WideDTO.java",
            """
            package model;
            import model.RelatedDTO;
            public class WideDTO {
                public String field1;
                public String field2;
                public String field3;
                public String field4;
                public String field5;
                public Integer field6;
                public Integer field7;
                public Integer field8;
                public Long field9;
                public Long field10;
                public RelatedDTO related1;
                public RelatedDTO related2;
                public RelatedDTO related3;
                public WideDTO self;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/RelatedDTO.java",
            """
            package model;
            import model.WideDTO;
            public class RelatedDTO {
                public String relName;
                public Integer relValue;
                public WideDTO backRef;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.WideDTO")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Wide class with circular reference should produce a model", result)
        val json = ObjectModelJsonConverter.toJson5(result)
        assertNotNull(json)
        assertTrue(
            "JSON should not be excessively large (was ${json.length} chars)",
            json.length < 50000
        )
    }

    // ---- Circular reference with generic inheritance ----

    fun testCircularReferenceWithGenericInheritance() = runBlocking {
        myFixture.addFileToProject(
            "model/BaseResponse.java",
            """
            package model;
            public class BaseResponse<T> {
                public int code;
                public String message;
                public T data;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/RecursiveItem.java",
            """
            package model;
            import java.util.List;
            public class RecursiveItem {
                public String name;
                public RecursiveItem parent;
                public List<RecursiveItem> children;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/RecursiveItemResponse.java",
            """
            package model;
            import model.BaseResponse;
            import model.RecursiveItem;
            public class RecursiveItemResponse extends BaseResponse<RecursiveItem> {
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.RecursiveItemResponse")!!

        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Circular reference with generic inheritance should produce a model", result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals(0, map["code"])
        assertEquals("", map["message"])
        assertTrue("Should have data field", map["data"] is Map<*, *>)
        val data = map["data"] as Map<*, *>
        assertEquals("", data["name"])
    }

    // ---- Verify maxDepth is respected for non-circular deep nesting ----

    fun testMaxDepthLimitsNonCircularNesting() = runBlocking {
        // Set max.deep=3 via config to test depth limiting
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, "max.deep" to "3")
        )

        myFixture.addFileToProject(
            "model/Level1.java",
            """
            package model;
            import model.Level2;
            public class Level1 {
                public String l1;
                public Level2 next;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Level2.java",
            """
            package model;
            import model.Level3;
            public class Level2 {
                public String l2;
                public Level3 next;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Level3.java",
            """
            package model;
            import model.Level4;
            public class Level3 {
                public String l3;
                public Level4 next;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "model/Level4.java",
            """
            package model;
            public class Level4 {
                public String l4;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.Level1")!!

        // With max.deep=3 (from config), Level4 should not be reached
        val result = helper.buildObjectModel(psiClass)

        assertNotNull(result)
        val map = ObjectModelValueConverter.toSimpleValue(result) as Map<*, *>
        assertEquals("", map["l1"])
        assertTrue("Should have next at depth 1", map["next"] is Map<*, *>)
        val level2 = map["next"] as Map<*, *>
        assertEquals("", level2["l2"])
        // Level3 should be at depth 2, which is within maxDepth=3
        assertTrue("Should have next at depth 2", level2["next"] is Map<*, *>)
        val level3 = level2["next"] as Map<*, *>
        assertEquals("", level3["l3"])
        // Level4 would be at depth 3, which equals maxDepth=3, so it should be an empty object
        // (buildTypeObject returns null, buildFieldValue falls back to emptyObject)
        val level4 = level3["next"]
        if (level4 != null) {
            assertTrue("Level4 should be empty map (maxDepth reached)", level4 is Map<*, *>)
            assertTrue(
                "Level4 should have no fields (maxDepth reached)",
                (level4 as Map<*, *>).isEmpty()
            )
        }
    }

    // ---- Element counter limits total complexity ----

    /**
     * Verifies that the element counter prevents OOM for classes with
     * many fields and circular references. The default limit (512 elements)
     * should cap the total number of fields processed.
     *
     * This mirrors the legacy plugin's maxElements guard.
     */
    fun testElementCounterLimitsComplexModel() = runBlocking {
        // Create a class with many fields that reference each other
        val fieldDecls = (1..20).joinToString("\n") { "                public String field$it;" }
        myFixture.addFileToProject(
            "model/HugeDTO.java",
            """
            package model;
            import java.util.List;
            import model.HugeSubDTO;
            public class HugeDTO {
$fieldDecls
                public HugeDTO parent;
                public List<HugeSubDTO> subs;
            }
            """.trimIndent()
        )
        val subFieldDecls = (1..15).joinToString("\n") { "                public String subField$it;" }
        myFixture.addFileToProject(
            "model/HugeSubDTO.java",
            """
            package model;
            import model.HugeDTO;
            public class HugeSubDTO {
$subFieldDecls
                public HugeDTO owner;
            }
            """.trimIndent()
        )
        val psiClass = findClass("model.HugeDTO")!!

        // Should complete without OOM — element counter caps total fields
        val result = helper.buildObjectModel(psiClass)

        assertNotNull("Complex model should produce a result", result)
        val json = ObjectModelJsonConverter.toJson5(result)
        assertNotNull(json)
        // The JSON should be bounded, not exploding
        assertTrue(
            "JSON should be bounded (was ${json.length} chars)",
            json.length < 200000
        )
    }

    fun testElementCounterUnit() {
        val counter = ElementCounter(maxElements = 10)
        assertFalse("Should not be exceeded initially", counter.isExceeded())
        assertEquals(0, counter.count())

        // Increment 10 times — should not exceed
        repeat(10) {
            assertFalse("Should not exceed at count ${it + 1}", counter.incrementAndCheckExceeded())
        }
        assertEquals(10, counter.count())
        assertFalse("Should not be exceeded at exactly the limit", counter.isExceeded())

        // 11th increment should exceed
        assertTrue("Should exceed at count 11", counter.incrementAndCheckExceeded())
        assertTrue("Should be exceeded after exceeding", counter.isExceeded())
        assertEquals(11, counter.count())
    }
}
