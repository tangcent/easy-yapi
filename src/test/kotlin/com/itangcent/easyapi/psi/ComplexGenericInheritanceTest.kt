package com.itangcent.easyapi.psi

import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests for complex generic type resolution through deep inheritance hierarchies
 * where type parameters are wrapped/transformed when passed to parent classes.
 *
 * Reproduces the bug from https://github.com/tangcent/easy-yapi/issues/1326
 *
 * Hierarchy under test:
 *   BaseResult<D> { D content; boolean success; String errorCode; String errorMsg; }
 *   AtaBaseResult<T> extends BaseResult<T> { String traceId; }
 *   AtaPageResult<T> extends AtaBaseResult<AtaPage<T>> { Boolean hasNextPage; }
 *   Page<T> { List<T> data; Integer totalCount; Integer currentPage; Integer pageSize; }
 *   AtaPage<T> extends Page<T> { Boolean hasNextPage; }
 *   VotePageQueryVO { Long id; String label; }
 *   VotePageQueryResult extends AtaPageResult<VotePageQueryVO>
 *
 * The key complexity: AtaPageResult<T> passes AtaPage<T> (not T) to its parent,
 * so the inherited `content` field should resolve to AtaPage<VotePageQueryVO>,
 * not VotePageQueryVO directly.
 */
class ComplexGenericInheritanceTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        // Load all files in dependency order (bottom-up)
        loadFile("model/generic/Page.java")
        loadFile("model/generic/AtaPage.java")
        loadFile("model/generic/BaseResult.java")
        loadFile("model/generic/AtaBaseResult.java")
        loadFile("model/generic/AtaPageResult.java")
        loadFile("model/generic/VotePageQueryVO.java")
        loadFile("model/generic/VotePageQueryResult.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    /**
     * Tests that the `content` field in VotePageQueryResult resolves to
     * AtaPage<VotePageQueryVO> (an object with data, totalCount, etc.),
     * NOT to VotePageQueryVO directly.
     *
     * This is the core bug from issue #1326: the intermediate AtaPage wrapper
     * was being lost during generic resolution.
     */
    fun testContentFieldResolvesToAtaPage() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryResult")
        assertNotNull("Should find VotePageQueryResult", psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build model for VotePageQueryResult", model)

        val obj = model as? ObjectModel.Object
        assertNotNull("Model should be Object", obj)

        // The content field should exist (inherited from BaseResult<D>)
        val contentField = obj!!.fields["content"]
        assertNotNull("Should have inherited 'content' field from BaseResult", contentField)

        // content should be an Object (AtaPage<VotePageQueryVO>), NOT a Single type
        val contentModel = contentField!!.model
        assertTrue(
            "content should be an Object (AtaPage<VotePageQueryVO>), but got: $contentModel",
            contentModel is ObjectModel.Object
        )

        // Verify the content object has AtaPage/Page fields
        val contentObj = contentModel as ObjectModel.Object

        // From Page<T>: totalCount, currentPage, pageSize
        val totalCountField = contentObj.fields["totalCount"]
        assertNotNull("content (AtaPage) should have 'totalCount' field from Page", totalCountField)
        assertTrue(
            "totalCount should be int, got: ${totalCountField!!.model}",
            totalCountField.model is ObjectModel.Single && (totalCountField.model as ObjectModel.Single).type == JsonType.INT
        )

        val currentPageField = contentObj.fields["currentPage"]
        assertNotNull("content (AtaPage) should have 'currentPage' field from Page", currentPageField)

        val pageSizeField = contentObj.fields["pageSize"]
        assertNotNull("content (AtaPage) should have 'pageSize' field from Page", pageSizeField)

        // From AtaPage<T>: hasNextPage
        val ataPageHasNextPage = contentObj.fields["hasNextPage"]
        assertNotNull("content (AtaPage) should have 'hasNextPage' field from AtaPage", ataPageHasNextPage)

        // From Page<T>: data field should be an array of VotePageQueryVO
        // Note: In the light test fixture, java.util.List may resolve as either
        // ObjectModel.Array (when JDK classes are fully available) or
        // ObjectModel.Single(type=array) (when List resolves as UnresolvedType).
        // Both representations correctly indicate an array type.
        val dataField = contentObj.fields["data"]
        assertNotNull("content (AtaPage) should have 'data' field from Page", dataField)
        val dataModel = dataField!!.model
        assertTrue(
            "data should be an array type, got: $dataModel",
            dataModel is ObjectModel.Array ||
                    (dataModel is ObjectModel.Single && dataModel.type == JsonType.ARRAY)
        )

        // If data resolved as a proper Array, verify the item type
        if (dataModel is ObjectModel.Array) {
            assertTrue(
                "data items should be Objects (VotePageQueryVO), got: ${dataModel.item}",
                dataModel.item is ObjectModel.Object
            )

            val voObj = dataModel.item as ObjectModel.Object
            val idField = voObj.fields["id"]
            assertNotNull("VotePageQueryVO should have 'id' field", idField)
            assertTrue(
                "id should be long, got: ${idField!!.model}",
                idField.model is ObjectModel.Single && (idField.model as ObjectModel.Single).type == JsonType.LONG
            )

            val labelField = voObj.fields["label"]
            assertNotNull("VotePageQueryVO should have 'label' field", labelField)
            assertTrue(
                "label should be string, got: ${labelField!!.model}",
                labelField.model is ObjectModel.Single && (labelField.model as ObjectModel.Single).type == JsonType.STRING
            )
        }
    }

    /**
     * Tests that top-level fields from the full hierarchy are present.
     */
    fun testTopLevelFieldsPresent() = runTest {
        val psiClass = findClass("com.itangcent.model.generic.VotePageQueryResult")
        assertNotNull(psiClass)

        val helper = DefaultPsiClassHelper.getInstance(project)
        val obj = helper.buildObjectModel(psiClass!!) as? ObjectModel.Object
        assertNotNull(obj)

        // From BaseResult: success, errorCode, errorMsg
        assertNotNull("Should have 'success' from BaseResult", obj!!.fields["success"])
        assertNotNull("Should have 'errorCode' from BaseResult", obj.fields["errorCode"])
        assertNotNull("Should have 'errorMsg' from BaseResult", obj.fields["errorMsg"])

        // From AtaBaseResult: traceId
        assertNotNull("Should have 'traceId' from AtaBaseResult", obj.fields["traceId"])

        // From AtaPageResult: hasNextPage (the top-level one)
        assertNotNull("Should have 'hasNextPage' from AtaPageResult", obj.fields["hasNextPage"])

        // From BaseResult: content (should be AtaPage object)
        assertNotNull("Should have 'content' from BaseResult", obj.fields["content"])
    }
}
