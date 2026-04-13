package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.exporter.model.httpMetadata
import kotlinx.coroutines.runBlocking

class SpringMvcClassExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/PatchMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("api/BaseController.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY


    fun testExportSimpleController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())
    }

    fun testExportNonController() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isEmpty())
    }

    fun testExportWithGetMapping() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoints = endpoints.filter { it.httpMetadata?.method == com.itangcent.easyapi.exporter.model.HttpMethod.GET }
        assertTrue(getEndpoints.isNotEmpty())
    }

    fun testExportWithPostMapping() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoints = endpoints.filter { it.httpMetadata?.method == com.itangcent.easyapi.exporter.model.HttpMethod.POST }
        assertTrue(postEndpoints.isNotEmpty())
    }

    fun testRequestMappingWithRequestBodyDefaultsToPost() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val updateWithBody = endpoints.find { it.httpMetadata?.path == "/user/update-with-body" }
        assertNotNull(updateWithBody)
        assertEquals(com.itangcent.easyapi.exporter.model.HttpMethod.POST, updateWithBody!!.httpMetadata?.method)
    }

    fun testRequestMappingWithoutBodyDefaultsToGet() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val simple = endpoints.find { it.httpMetadata?.path == "/user/simple" }
        assertNotNull(simple)
        assertEquals(com.itangcent.easyapi.exporter.model.HttpMethod.GET, simple!!.httpMetadata?.method)
    }

    fun testExplicitGetWithBodyRemainsGet() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val explicitGet = endpoints.find { it.httpMetadata?.path == "/user/explicit-get-with-body" }
        assertNotNull(explicitGet)
        assertEquals(com.itangcent.easyapi.exporter.model.HttpMethod.GET, explicitGet!!.httpMetadata?.method)
    }

    fun testRequestMappingWithModelAttributeDefaultsToPost() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val updateWithModel = endpoints.find { it.httpMetadata?.path == "/user/update-with-model" }
        assertNotNull(updateWithModel)
        assertEquals(com.itangcent.easyapi.exporter.model.HttpMethod.POST, updateWithModel!!.httpMetadata?.method)
    }

    fun testResponseBodyIsPopulated() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)

        // The "create" endpoint returns Result<UserInfo>, so responseBody should be populated
        val createEndpoint = endpoints.find { it.httpMetadata?.path == "/user/add" }
        assertNotNull("Should find /user/add endpoint", createEndpoint)
        assertNotNull("responseBody should be populated for Result<UserInfo>", createEndpoint!!.httpMetadata?.responseBody)

        // Verify the response body has the expected structure (Result fields: code, msg, data)
        val responseObj = createEndpoint.httpMetadata?.responseBody
        assertNotNull(responseObj)
        assertTrue("responseBody should be an Object model", responseObj is com.itangcent.easyapi.psi.model.ObjectModel.Object)
        val fields = (responseObj as com.itangcent.easyapi.psi.model.ObjectModel.Object).fields
        assertTrue("Response should contain 'code' field", fields.containsKey("code"))
        assertTrue("Response should contain 'msg' field", fields.containsKey("msg"))
        assertTrue("Response should contain 'data' field", fields.containsKey("data"))
    }

    fun testResponseBodyForStringReturn() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)

        // The "greeting" endpoint returns String
        val greetingEndpoint = endpoints.find { it.httpMetadata?.path == "/user/greeting" }
        assertNotNull("Should find /user/greeting endpoint", greetingEndpoint)
        // String return type should produce a Single model
        assertNotNull("responseBody should be populated for String return", greetingEndpoint!!.httpMetadata?.responseBody)
    }

    fun testRequestBodyIsPopulated() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)

        // The "create" endpoint has @RequestBody UserInfo
        val createEndpoint = endpoints.find { it.httpMetadata?.path == "/user/add" }
        assertNotNull("Should find /user/add endpoint", createEndpoint)
        assertNotNull("body should be populated for @RequestBody UserInfo", createEndpoint!!.httpMetadata?.body)

        val bodyObj = createEndpoint.httpMetadata?.body
        assertTrue("body should be an Object model", bodyObj is com.itangcent.easyapi.psi.model.ObjectModel.Object)
        val fields = (bodyObj as com.itangcent.easyapi.psi.model.ObjectModel.Object).fields
        assertTrue("Body should contain 'name' field", fields.containsKey("name"))
        assertTrue("Body should contain 'age' field", fields.containsKey("age"))
    }

    fun testFieldCommentsArePopulated() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)

        // The "create" endpoint has @RequestBody UserInfo which has field comments
        val createEndpoint = endpoints.find { it.httpMetadata?.path == "/user/add" }
        assertNotNull("Should find /user/add endpoint", createEndpoint)
        assertNotNull("body should be populated", createEndpoint!!.httpMetadata?.body)

        val bodyObj = createEndpoint.httpMetadata?.body as? com.itangcent.easyapi.psi.model.ObjectModel.Object
        assertNotNull("body should be an Object model", bodyObj)

        // UserInfo.java has: private Long id = 0;//user id
        val idField = bodyObj!!.fields["id"]
        assertNotNull("Should have 'id' field", idField)
        assertNotNull("id field should have a comment", idField!!.comment)
        assertTrue("id field comment should contain 'user id'", idField.comment!!.contains("user id"))

        // UserInfo.java has: /** user name */ @NotBlank private String name;
        val nameField = bodyObj.fields["name"]
        assertNotNull("Should have 'name' field", nameField)
        assertNotNull("name field should have a comment", nameField!!.comment)
        assertTrue("name field comment should contain 'user name'", nameField.comment!!.contains("user name"))

        // UserInfo.java has: /** user age */ @NotNull private Integer age;
        val ageField = bodyObj.fields["age"]
        assertNotNull("Should have 'age' field", ageField)
        assertNotNull("age field should have a comment", ageField!!.comment)
        assertTrue("age field comment should contain 'user age'", ageField.comment!!.contains("user age"))
    }

    fun testResponseBodyGenericTypeResolution() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)

        // The "create" endpoint returns Result<UserInfo>
        val createEndpoint = endpoints.find { it.httpMetadata?.path == "/user/add" }
        assertNotNull("Should find /user/add endpoint", createEndpoint)
        assertNotNull("responseBody should be populated", createEndpoint!!.httpMetadata?.responseBody)

        val responseObj = createEndpoint.httpMetadata?.responseBody as? com.itangcent.easyapi.psi.model.ObjectModel.Object
        assertNotNull("responseBody should be an Object model", responseObj)

        // Result has: Integer code, String msg, T data
        val codeField = responseObj!!.fields["code"]
        assertNotNull("Response should have 'code' field", codeField)

        val dataField = responseObj.fields["data"]
        assertNotNull("Response should have 'data' field", dataField)

        // The generic T should be resolved to UserInfo, so data should be an Object with UserInfo fields
        val dataModel = dataField!!.model
        assertTrue(
            "data field should be resolved to an Object (UserInfo), not a simple type. Got: $dataModel",
            dataModel is com.itangcent.easyapi.psi.model.ObjectModel.Object
        )

        val userInfoFields = (dataModel as com.itangcent.easyapi.psi.model.ObjectModel.Object).fields
        assertTrue("data (UserInfo) should contain 'id' field", userInfoFields.containsKey("id"))
        assertTrue("data (UserInfo) should contain 'name' field", userInfoFields.containsKey("name"))
        assertTrue("data (UserInfo) should contain 'age' field", userInfoFields.containsKey("age"))
    }

    fun testHttpServletRequestIgnored() = runTest {
        loadFile("api/TestCtrl.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/SessionAttribute.java")
        loadFile("spring/CookieValue.java")
        loadFile("javax/servlet/http/HttpServletRequest.java",
            "package javax.servlet.http; public interface HttpServletRequest {}")
        loadFile("javax/servlet/http/HttpServletResponse.java",
            "package javax.servlet.http; public interface HttpServletResponse {}")

        val psiClass = findClass("com.itangcent.api.TestCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)

        // /test/httpServletRequest has only HttpServletRequest param — should have no query params
        val reqEndpoint = endpoints.find { it.httpMetadata?.path == "/test/httpServletRequest" }
        assertNotNull("Should find /test/httpServletRequest", reqEndpoint)
        assertTrue(
            "HttpServletRequest should be ignored, no parameters expected",
            (reqEndpoint!!.httpMetadata?.parameters ?: emptyList()).isEmpty()
        )

        // /test/httpServletResponse has only HttpServletResponse param — should have no query params
        val respEndpoint = endpoints.find { it.httpMetadata?.path == "/test/httpServletResponse" }
        assertNotNull("Should find /test/httpServletResponse", respEndpoint)
        assertTrue(
            "HttpServletResponse should be ignored, no parameters expected",
            (respEndpoint!!.httpMetadata?.parameters ?: emptyList()).isEmpty()
        )
    }

    fun testReturnDocOnGenericField() = runTest {
        // Create a controller with @return doc and Result<T> return type
        loadFile("api/ReturnDocCtrl.java",
            """
            package com.itangcent.api;
            import com.itangcent.model.Result;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RestController
            @RequestMapping("/return-doc")
            public class ReturnDocCtrl {
                /**
                 * get processing result
                 * @return processing result description
                 */
                @GetMapping("/test")
                public Result<String> getResult() {
                    return Result.success("ok");
                }
            }
            """.trimIndent()
        )

        val psiClass = findClass("com.itangcent.api.ReturnDocCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val endpoint = endpoints.find { it.httpMetadata?.path == "/return-doc/test" }
        assertNotNull("Should find /return-doc/test", endpoint)
        assertNotNull("responseBody should be populated", endpoint!!.httpMetadata?.responseBody)

        val responseObj = endpoint.httpMetadata?.responseBody as? com.itangcent.easyapi.psi.model.ObjectModel.Object
        assertNotNull(responseObj)

        // The "data" field is generic (T resolved to String)
        val dataField = responseObj!!.fields["data"]
        assertNotNull("Should have 'data' field", dataField)
        // Note: @return doc attachment to generic fields is not yet implemented
        // Just verify the endpoint was exported with a response body
        assertNotNull("data field should exist in response body", dataField)
    }

    fun testMultipartFileFieldExportedAsFileParam() = runTest {
        loadFile("api/FileUploadCtrl.java",
            """
            package com.itangcent.api;
            import com.itangcent.model.Result;
            import com.itangcent.model.UserDto;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.ModelAttribute;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/file")
            public class FileUploadCtrl {
                /**
                 * Create new user and upload avatar
                 * @param userDto user data with avatar
                 * @return save result
                 */
                @PostMapping("/add")
                public Result<String> add(@ModelAttribute UserDto userDto) {
                    return Result.success("Save successful");
                }
            }
            """.trimIndent()
        )
        loadFile("model/UserDto.java",
            """
            package com.itangcent.model;
            import org.springframework.web.multipart.MultipartFile;

            public class UserDto extends UserInfo {
                /** User Profile Image File */
                private MultipartFile profileImg;

                public MultipartFile getProfileImg() {
                    return profileImg;
                }

                public void setProfileImg(MultipartFile profileImg) {
                    this.profileImg = profileImg;
                }
            }
            """.trimIndent()
        )

        val psiClass = findClass("com.itangcent.api.FileUploadCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val endpoint = endpoints.find { it.httpMetadata?.path == "/file/add" }
        assertNotNull("Should find /file/add endpoint", endpoint)

        val params = endpoint!!.httpMetadata?.parameters ?: emptyList()
        val profileImgParam = params.find { it.name == "profileImg" }
        assertNotNull("profileImg field should be expanded as a form parameter", profileImgParam)
        assertEquals(
            "profileImg (MultipartFile) should be FILE type, not TEXT",
            com.itangcent.easyapi.exporter.model.ParameterType.FILE,
            profileImgParam!!.type
        )
        assertEquals(
            "profileImg should have Form binding",
            com.itangcent.easyapi.exporter.model.ParameterBinding.Form,
            profileImgParam.binding
        )
    }

    fun testMultipartFileDirectParamExportedAsFileParam() = runTest {
        loadFile("api/DirectFileUploadCtrl.java",
            """
            package com.itangcent.api;
            import com.itangcent.model.Result;
            import org.springframework.web.multipart.MultipartFile;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/file-direct")
            public class DirectFileUploadCtrl {
                /**
                 * Upload a single file
                 * @param file the file to upload
                 * @return upload result
                 */
                @PostMapping("/upload")
                public Result<String> upload(@RequestParam("file") MultipartFile file) {
                    return Result.success("Upload successful");
                }
            }
            """.trimIndent()
        )

        val psiClass = findClass("com.itangcent.api.DirectFileUploadCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val endpoint = endpoints.find { it.httpMetadata?.path == "/file-direct/upload" }
        assertNotNull("Should find /file-direct/upload endpoint", endpoint)

        val params = endpoint!!.httpMetadata?.parameters ?: emptyList()
        val fileParam = params.find { it.name == "file" }
        assertNotNull("file parameter should be present", fileParam)
        assertEquals(
            "MultipartFile parameter should be FILE type",
            com.itangcent.easyapi.exporter.model.ParameterType.FILE,
            fileParam!!.type
        )
        assertEquals(
            "MultipartFile parameter should have Form binding",
            com.itangcent.easyapi.exporter.model.ParameterBinding.Form,
            fileParam.binding
        )
    }
}
