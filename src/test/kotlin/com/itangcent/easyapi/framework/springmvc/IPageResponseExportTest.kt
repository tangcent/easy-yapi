package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Verifies that a controller returning Result<IPage<UserInfo>> exports
 * the full IPage structure (records/total/size/current) with the generic
 * record element type expanded.
 */
class IPageResponseExportTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadJDKClass("java.util.List")
        loadJDKClass("java.util.Collection")
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("com/baomidou/mybatisplus/core/metadata/IPage.java")
        loadFile("api/mybatisplus/PageController.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testIPageResponseExpandsRecords() = runTest {
        val psiClass = findClass("com.itangcent.mybatisplus.PageController")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val listUsers = endpoints.find { it.httpMetadata?.path == "/page/users" }
        assertNotNull("Should find /page/users endpoint", listUsers)

        val responseBody = listUsers!!.httpMetadata?.responseBody
        assertNotNull("responseBody should be populated", responseBody)
        val resultObj = responseBody as? ObjectModel.Object
        assertNotNull("responseBody should be an Object model (Result), but was: $responseBody", resultObj)
        val resultFields = resultObj!!.fields
        assertTrue("Result should contain 'code'", resultFields.containsKey("code"))
        assertTrue("Result should contain 'msg'", resultFields.containsKey("msg"))

        val dataField = resultFields["data"]
        assertNotNull("Result should contain 'data'", dataField)
        val pageObj = dataField!!.model as? ObjectModel.Object
        assertNotNull(
            "'data' (IPage<UserInfo>) should expand into an object with records/total/size/current, but was: ${dataField.model}",
            pageObj
        )
        val pageFields = pageObj!!.fields
        assertTrue("IPage should contain 'records', but had: ${pageFields.keys}", pageFields.containsKey("records"))
        assertTrue("IPage should contain 'total'", pageFields.containsKey("total"))
        assertTrue("IPage should contain 'size'", pageFields.containsKey("size"))
        assertTrue("IPage should contain 'current'", pageFields.containsKey("current"))

        val recordsModel = pageFields["records"]!!.model
        assertTrue("'records' should be an array, but was: $recordsModel", recordsModel is ObjectModel.Array)
        val elementModel = (recordsModel as ObjectModel.Array).item
        val userObj = elementModel as? ObjectModel.Object
        assertNotNull("'records' element should be a UserInfo object, but was: $elementModel", userObj)
        assertTrue(
            "UserInfo should contain 'name', but had: ${userObj!!.fields.keys}",
            userObj.fields.containsKey("name")
        )
        assertTrue("UserInfo should contain 'age'", userObj.fields.containsKey("age"))
    }
}
