package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [ProtoUtils].
 */
class ProtoUtilsTest {

    // -------------------------------------------------------------------------
    // parseProtoText tests
    // -------------------------------------------------------------------------

    @Test
    fun testParseProtoTextExtractsPackage() {
        val result = ProtoUtils.parseProtoText("syntax = \"proto3\";\npackage test.grpc;\n")
        assertEquals("test.grpc", result.packageName)
    }

    @Test
    fun testParseProtoTextExtractsPackageWithTrailingSemicolon() {
        val result = ProtoUtils.parseProtoText("package com.example.api;")
        assertEquals("com.example.api", result.packageName)
    }

    @Test
    fun testParseProtoTextExtractsImports() {
        val text = """
            import "google/protobuf/empty.proto";
            import "google/protobuf/timestamp.proto";
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(2, result.imports.size)
        assertEquals("google/protobuf/empty.proto", result.imports[0])
        assertEquals("google/protobuf/timestamp.proto", result.imports[1])
    }

    @Test
    fun testParseProtoTextExtractsService() {
        val text = """
            service EchoService {
              rpc Echo(EchoRequest) returns (EchoResponse);
              rpc Reverse(ReverseRequest) returns (ReverseResponse);
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.services.size)
        assertEquals("EchoService", result.services[0].name)
        assertEquals(2, result.services[0].methods.size)
        assertEquals("Echo", result.services[0].methods[0].name)
        assertEquals("EchoRequest", result.services[0].methods[0].inputType)
        assertEquals("EchoResponse", result.services[0].methods[0].outputType)
        assertEquals("Reverse", result.services[0].methods[1].name)
    }

    @Test
    fun testParseProtoTextExtractsMessageWithFields() {
        val text = """
            message EchoRequest {
              string message = 1;
              int32 count = 2;
              bool enabled = 3;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.messages.size)
        assertEquals("EchoRequest", result.messages[0].name)
        assertEquals(3, result.messages[0].fields.size)

        val fields = result.messages[0].fields
        assertEquals("message", fields[0].name)
        assertEquals("string", fields[0].type)
        assertEquals(1, fields[0].number)
        assertEquals("", fields[0].label)

        assertEquals("count", fields[1].name)
        assertEquals("int32", fields[1].type)
        assertEquals(2, fields[1].number)

        assertEquals("enabled", fields[2].name)
        assertEquals("bool", fields[2].type)
        assertEquals(3, fields[2].number)
    }

    @Test
    fun testParseProtoTextHandlesRepeatedFields() {
        val text = """
            message ListRequest {
              repeated string items = 1;
              repeated int32 numbers = 2;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(2, result.messages[0].fields.size)
        assertEquals("repeated", result.messages[0].fields[0].label)
        assertEquals("repeated", result.messages[0].fields[1].label)
    }

    @Test
    fun testParseProtoTextHandlesSingleLineMessage() {
        val text = "message EmptyRequest {}"
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.messages.size)
        assertEquals("EmptyRequest", result.messages[0].name)
        assertTrue(result.messages[0].fields.isEmpty())
    }

    @Test
    fun testParseProtoTextHandlesEmptyInput() {
        val result = ProtoUtils.parseProtoText("")
        assertEquals("", result.packageName)
        assertTrue(result.services.isEmpty())
        assertTrue(result.messages.isEmpty())
        assertTrue(result.imports.isEmpty())
    }

    @Test
    fun testParseProtoTextHandlesMultipleServices() {
        val text = """
            service ServiceA { rpc MethodA(RequestA) returns (ResponseA); }
            service ServiceB { rpc MethodB(RequestB) returns (ResponseB); }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(2, result.services.size)
        assertEquals("ServiceA", result.services[0].name)
        assertEquals("ServiceB", result.services[1].name)
    }

    @Test
    fun testParseProtoTextHandlesMultipleMessages() {
        val text = """
            message Request { string data = 1; }
            message Response { string result = 1; }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(2, result.messages.size)
        assertEquals("Request", result.messages[0].name)
        assertEquals("Response", result.messages[1].name)
    }

    @Test
    fun testParseProtoTextHandlesQualifiedTypes() {
        val text = """
            service TestService {
              rpc Method(com.example.Request) returns (com.example.Response);
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.services.size)
        assertEquals("com.example.Request", result.services[0].methods[0].inputType)
        assertEquals("com.example.Response", result.services[0].methods[0].outputType)
    }

    // -------------------------------------------------------------------------
    // nested types tests
    // -------------------------------------------------------------------------

    @Test
    fun testParseProtoTextHandlesNestedMessage() {
        val text = """
            message Outer {
              string name = 1;
              message Inner {
                string value = 1;
              }
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.messages.size)
        assertEquals("Outer", result.messages[0].name)
        assertEquals(1, result.messages[0].fields.size)
        assertEquals(1, result.messages[0].nestedMessages.size)
        assertEquals("Outer.Inner", result.messages[0].nestedMessages[0].name)
        assertEquals("value", result.messages[0].nestedMessages[0].fields[0].name)
    }

    @Test
    fun testParseProtoTextHandlesNestedEnum() {
        val text = """
            message LabelDescriptor {
              string key = 1;
              enum ValueType {
                STRING = 0;
                BOOL = 1;
                INT64 = 2;
              }
              ValueType value_type = 2;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.messages.size)
        assertEquals("LabelDescriptor", result.messages[0].name)
        assertEquals(2, result.messages[0].fields.size)
        assertEquals(1, result.messages[0].nestedEnums.size)
        assertEquals("LabelDescriptor.ValueType", result.messages[0].nestedEnums[0].name)
        assertEquals(3, result.messages[0].nestedEnums[0].values.size)
        assertEquals("STRING", result.messages[0].nestedEnums[0].values[0].name)
        assertEquals(0, result.messages[0].nestedEnums[0].values[0].number)
        assertEquals("BOOL", result.messages[0].nestedEnums[0].values[1].name)
        assertEquals(1, result.messages[0].nestedEnums[0].values[1].number)
    }

    @Test
    fun testParseProtoTextHandlesTopLevelEnum() {
        val text = """
            enum Status {
              UNKNOWN = 0;
              ACTIVE = 1;
              INACTIVE = 2;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(0, result.messages.size)
        assertEquals(1, result.enums.size)
        assertEquals("Status", result.enums[0].name)
        assertEquals(3, result.enums[0].values.size)
        assertEquals("UNKNOWN", result.enums[0].values[0].name)
        assertEquals(0, result.enums[0].values[0].number)
        assertEquals("ACTIVE", result.enums[0].values[1].name)
        assertEquals(1, result.enums[0].values[1].number)
        assertEquals("INACTIVE", result.enums[0].values[2].name)
        assertEquals(2, result.enums[0].values[2].number)
    }

    @Test
    fun testParseProtoTextHandlesDeeplyNestedTypes() {
        val text = """
            message Level1 {
              message Level2 {
                message Level3 {
                  string value = 1;
                }
                Level3 nested = 1;
              }
              Level2 nested = 1;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals(1, result.messages.size)
        assertEquals("Level1", result.messages[0].name)
        assertEquals(1, result.messages[0].nestedMessages.size)
        assertEquals("Level1.Level2", result.messages[0].nestedMessages[0].name)
        assertEquals(1, result.messages[0].nestedMessages[0].nestedMessages.size)
        assertEquals("Level1.Level2.Level3", result.messages[0].nestedMessages[0].nestedMessages[0].name)
    }

    @Test
    fun testParseProtoTextHandlesMixedNestedAndTopLevel() {
        val text = """
            package google.api;

            message Http {
              repeated HttpRule rules = 1;
            }

            message HttpRule {
              string pattern = 1;
            }

            enum Method {
              GET = 0;
              POST = 1;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals("google.api", result.packageName)
        assertEquals(2, result.messages.size)
        assertEquals(1, result.enums.size)
        assertEquals("Http", result.messages[0].name)
        assertEquals("HttpRule", result.messages[1].name)
        assertEquals("Method", result.enums[0].name)
    }

    // -------------------------------------------------------------------------
    // flattenMessages and flattenEnums tests
    // -------------------------------------------------------------------------

    @Test
    fun testFlattenMessages() {
        val text = """
            message Outer {
              message Inner1 {
                message DeepNested {
                  string value = 1;
                }
              }
              message Inner2 {
                string value = 1;
              }
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        val flattened = ProtoUtils.flattenMessages(result.messages)
        assertEquals(4, flattened.size)
        assertTrue(flattened.any { it.name == "Outer" })
        assertTrue(flattened.any { it.name == "Outer.Inner1" })
        assertTrue(flattened.any { it.name == "Outer.Inner1.DeepNested" })
        assertTrue(flattened.any { it.name == "Outer.Inner2" })
    }

    @Test
    fun testFlattenEnums() {
        val text = """
            enum TopLevel { A = 0; B = 1; }
            
            message Container {
              enum NestedEnum { X = 0; Y = 1; }
              message Inner {
                enum DeepEnum { P = 0; Q = 1; }
              }
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        val flattened = ProtoUtils.flattenEnums(result)
        assertEquals(3, flattened.size)
        assertTrue(flattened.any { it.name == "TopLevel" })
        assertTrue(flattened.any { it.name == "Container.NestedEnum" })
        assertTrue(flattened.any { it.name == "Container.Inner.DeepEnum" })
    }

    // -------------------------------------------------------------------------
    // mapJavaTypeToProto tests
    // -------------------------------------------------------------------------

    @Test
    fun testMapJavaTypeToProtoString() {
        assertEquals("string", ProtoUtils.mapJavaTypeToProto("java.lang.String"))
        assertEquals("string", ProtoUtils.mapJavaTypeToProto("String"))
    }

    @Test
    fun testMapJavaTypeToProtoInt() {
        assertEquals("int32", ProtoUtils.mapJavaTypeToProto("int"))
        assertEquals("int32", ProtoUtils.mapJavaTypeToProto("java.lang.Integer"))
    }

    @Test
    fun testMapJavaTypeToProtoLong() {
        assertEquals("int64", ProtoUtils.mapJavaTypeToProto("long"))
        assertEquals("int64", ProtoUtils.mapJavaTypeToProto("java.lang.Long"))
    }

    @Test
    fun testMapJavaTypeToProtoBoolean() {
        assertEquals("bool", ProtoUtils.mapJavaTypeToProto("boolean"))
        assertEquals("bool", ProtoUtils.mapJavaTypeToProto("java.lang.Boolean"))
    }

    @Test
    fun testMapJavaTypeToProtoFloat() {
        assertEquals("float", ProtoUtils.mapJavaTypeToProto("float"))
        assertEquals("float", ProtoUtils.mapJavaTypeToProto("java.lang.Float"))
    }

    @Test
    fun testMapJavaTypeToProtoDouble() {
        assertEquals("double", ProtoUtils.mapJavaTypeToProto("double"))
        assertEquals("double", ProtoUtils.mapJavaTypeToProto("java.lang.Double"))
    }

    @Test
    fun testMapJavaTypeToProtoBytes() {
        assertEquals("bytes", ProtoUtils.mapJavaTypeToProto("com.google.protobuf.ByteString"))
        assertEquals("bytes", ProtoUtils.mapJavaTypeToProto("byte[]"))
    }

    @Test
    fun testMapJavaTypeToProtoUnknownType() {
        assertEquals("MyMessage", ProtoUtils.mapJavaTypeToProto("com.example.MyMessage"))
        assertEquals("SimpleType", ProtoUtils.mapJavaTypeToProto("SimpleType"))
    }

    @Test
    fun testMapJavaTypeToProtoCaseInsensitive() {
        assertEquals("string", ProtoUtils.mapJavaTypeToProto("STRING"))
        assertEquals("int32", ProtoUtils.mapJavaTypeToProto("Integer"))
        assertEquals("int32", ProtoUtils.mapJavaTypeToProto("INT"))
        assertEquals("int64", ProtoUtils.mapJavaTypeToProto("Long"))
        assertEquals("bool", ProtoUtils.mapJavaTypeToProto("Boolean"))
        assertEquals("bool", ProtoUtils.mapJavaTypeToProto("BOOL"))
        assertEquals("float", ProtoUtils.mapJavaTypeToProto("Float"))
        assertEquals("double", ProtoUtils.mapJavaTypeToProto("Double"))
    }

    @Test
    fun testMapJavaTypeToProtoNestedClass() {
        assertEquals("InnerMessage", ProtoUtils.mapJavaTypeToProto("com.example.OuterClass.InnerMessage"))
    }

    // -------------------------------------------------------------------------
    // mapProtoType tests
    // -------------------------------------------------------------------------

    @Test
    fun testMapProtoTypeScalarTypes() {
        assertEquals("TYPE_DOUBLE", ProtoUtils.mapProtoType("double"))
        assertEquals("TYPE_FLOAT", ProtoUtils.mapProtoType("float"))
        assertEquals("TYPE_INT32", ProtoUtils.mapProtoType("int32"))
        assertEquals("TYPE_INT64", ProtoUtils.mapProtoType("int64"))
        assertEquals("TYPE_UINT32", ProtoUtils.mapProtoType("uint32"))
        assertEquals("TYPE_UINT64", ProtoUtils.mapProtoType("uint64"))
        assertEquals("TYPE_SINT32", ProtoUtils.mapProtoType("sint32"))
        assertEquals("TYPE_SINT64", ProtoUtils.mapProtoType("sint64"))
        assertEquals("TYPE_FIXED32", ProtoUtils.mapProtoType("fixed32"))
        assertEquals("TYPE_FIXED64", ProtoUtils.mapProtoType("fixed64"))
        assertEquals("TYPE_SFIXED32", ProtoUtils.mapProtoType("sfixed32"))
        assertEquals("TYPE_SFIXED64", ProtoUtils.mapProtoType("sfixed64"))
        assertEquals("TYPE_BOOL", ProtoUtils.mapProtoType("bool"))
        assertEquals("TYPE_STRING", ProtoUtils.mapProtoType("string"))
        assertEquals("TYPE_BYTES", ProtoUtils.mapProtoType("bytes"))
    }

    @Test
    fun testMapProtoTypeReturnsNullForNonScalar() {
        assertNull(ProtoUtils.mapProtoType("MyMessage"))
        assertNull(ProtoUtils.mapProtoType("com.example.MyMessage"))
        assertNull(ProtoUtils.mapProtoType("Status"))
        assertNull(ProtoUtils.mapProtoType(""))
    }

    // -------------------------------------------------------------------------
    // qualifyTypeName tests
    // -------------------------------------------------------------------------

    @Test
    fun testQualifyTypeNameWithPackage() {
        assertEquals(".com.example.MyMessage", ProtoUtils.qualifyTypeName("MyMessage", "com.example"))
        assertEquals(".test.grpc.Request", ProtoUtils.qualifyTypeName("Request", "test.grpc"))
    }

    @Test
    fun testQualifyTypeNameWithoutPackage() {
        assertEquals(".MyMessage", ProtoUtils.qualifyTypeName("MyMessage", ""))
    }

    @Test
    fun testQualifyTypeNameAlreadyQualified() {
        assertEquals(".com.example.MyMessage", ProtoUtils.qualifyTypeName(".com.example.MyMessage", "other.package"))
        assertEquals(".test.grpc.Request", ProtoUtils.qualifyTypeName(".test.grpc.Request", ""))
    }

    @Test
    fun testQualifyTypeNameNestedPackage() {
        assertEquals(".a.b.c.d.Message", ProtoUtils.qualifyTypeName("Message", "a.b.c.d"))
    }

    @Test
    fun testParseProtoTextHandlesNestedEnumWithFieldReference() {
        val text = """
            package audit.gateway;
            
            message AuditResultItem {
              string id = 1;
              enum AuditStatus {
                UNKNOWN = 0;
                PASS = 1;
                FAIL = 2;
              }
              AuditStatus status = 2;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals("audit.gateway", result.packageName)
        assertEquals(1, result.messages.size)
        
        val msg = result.messages[0]
        assertEquals("AuditResultItem", msg.name)
        assertEquals(2, msg.fields.size)
        assertEquals("id", msg.fields[0].name)
        assertEquals("status", msg.fields[1].name)
        assertEquals("AuditStatus", msg.fields[1].type)
        
        assertEquals(1, msg.nestedEnums.size)
        val enumDef = msg.nestedEnums[0]
        assertEquals("AuditResultItem.AuditStatus", enumDef.name)
        assertEquals(3, enumDef.values.size)
        assertEquals("UNKNOWN", enumDef.values[0].name)
        assertEquals(0, enumDef.values[0].number)
    }

    @Test
    fun testParseProtoTextHandlesTopLevelEnumReferencedByMessage() {
        val text = """
            package audit.gateway;
            
            message AuditResultItem {
              string auditId = 1;
              string seat = 2;
              AuditStatus status = 3;
            }
            
            enum AuditStatus {
              PASS = 0;
              REJECT = 1;
              FAILED = 2;
            }
        """.trimIndent()
        val result = ProtoUtils.parseProtoText(text)
        assertEquals("audit.gateway", result.packageName)
        
        assertEquals(1, result.messages.size)
        val msg = result.messages[0]
        assertEquals("AuditResultItem", msg.name)
        assertEquals(3, msg.fields.size)
        assertEquals("status", msg.fields[2].name)
        assertEquals("AuditStatus", msg.fields[2].type)
        
        assertEquals(0, msg.nestedEnums.size)
        
        assertEquals(1, result.enums.size)
        val enumDef = result.enums[0]
        assertEquals("AuditStatus", enumDef.name)
        assertEquals(3, enumDef.values.size)
    }

    // -------------------------------------------------------------------------
    // demo.proto tests (real-world example)
    // -------------------------------------------------------------------------

    @Test
    fun testParseDemoProto() {
        val text = """
            syntax = "proto3";

            package com.itangcent.springboot.demo.grpc;

            option java_multiple_files = true;
            option java_package = "com.itangcent.springboot.demo.grpc";
            option java_outer_classname = "DemoServiceProto";

            service DemoService {
                rpc SayHello (HelloRequest) returns (HelloReply) {}

                rpc GetUser (GetUserRequest) returns (UserResponse) {}

                rpc CreateUser (CreateUserRequest) returns (UserResponse) {}

                rpc UpdateUser (UpdateUserRequest) returns (UserResponse) {}

                rpc DeleteUser (DeleteUserRequest) returns (DeleteUserResponse) {}

                rpc ListUsers (ListUsersRequest) returns (stream UserResponse) {}

                rpc UploadUsers (stream CreateUserRequest) returns (UploadUsersResponse) {}

                rpc Chat (stream ChatMessage) returns (stream ChatMessage) {}
            }

            message HelloRequest {
                string name = 1;
            }

            message HelloReply {
                string message = 1;
            }

            message GetUserRequest {
                int64 id = 1;
            }

            message CreateUserRequest {
                string name = 1;
                int32 age = 2;
                string email = 3;
                UserType user_type = 4;
            }

            message UpdateUserRequest {
                int64 id = 1;
                string name = 2;
                int32 age = 3;
                string email = 4;
                UserType user_type = 5;
            }

            message DeleteUserRequest {
                int64 id = 1;
            }

            message DeleteUserResponse {
                bool success = 1;
                string message = 2;
            }

            message UserResponse {
                int64 id = 1;
                string name = 2;
                int32 age = 3;
                string email = 4;
                UserType user_type = 5;
                int64 created_at = 6;
                int64 updated_at = 7;
            }

            message ListUsersRequest {
                int32 page = 1;
                int32 page_size = 2;
                UserType user_type = 3;
            }

            message UploadUsersResponse {
                int32 total_count = 1;
                int32 success_count = 2;
                int32 failed_count = 3;
                string message = 4;
            }

            message ChatMessage {
                int64 user_id = 1;
                string user_name = 2;
                string message = 3;
                int64 timestamp = 4;
            }

            enum UserType {
                UNKNOWN = 0;
                ADMIN = 1;
                USER = 2;
                GUEST = 3;
            }
        """.trimIndent()

        val result = ProtoUtils.parseProtoText(text)

        // Verify package
        assertEquals("com.itangcent.springboot.demo.grpc", result.packageName)

        // Verify service
        assertEquals(1, result.services.size)
        val service = result.services[0]
        assertEquals("DemoService", service.name)
        assertEquals(8, service.methods.size)

        // Verify RPC methods
        val methods = service.methods
        assertEquals("SayHello", methods[0].name)
        assertEquals("HelloRequest", methods[0].inputType)
        assertEquals("HelloReply", methods[0].outputType)
        assertFalse(methods[0].clientStreaming)
        assertFalse(methods[0].serverStreaming)

        assertEquals("GetUser", methods[1].name)
        assertEquals("GetUserRequest", methods[1].inputType)
        assertEquals("UserResponse", methods[1].outputType)

        assertEquals("CreateUser", methods[2].name)
        assertEquals("CreateUserRequest", methods[2].inputType)
        assertEquals("UserResponse", methods[2].outputType)

        assertEquals("UpdateUser", methods[3].name)
        assertEquals("UpdateUserRequest", methods[3].inputType)
        assertEquals("UserResponse", methods[3].outputType)

        assertEquals("DeleteUser", methods[4].name)
        assertEquals("DeleteUserRequest", methods[4].inputType)
        assertEquals("DeleteUserResponse", methods[4].outputType)

        // Server streaming
        assertEquals("ListUsers", methods[5].name)
        assertEquals("ListUsersRequest", methods[5].inputType)
        assertEquals("UserResponse", methods[5].outputType)
        assertFalse(methods[5].clientStreaming)
        assertTrue(methods[5].serverStreaming)

        // Client streaming
        assertEquals("UploadUsers", methods[6].name)
        assertEquals("CreateUserRequest", methods[6].inputType)
        assertEquals("UploadUsersResponse", methods[6].outputType)
        assertTrue(methods[6].clientStreaming)
        assertFalse(methods[6].serverStreaming)

        // Bidirectional streaming
        assertEquals("Chat", methods[7].name)
        assertEquals("ChatMessage", methods[7].inputType)
        assertEquals("ChatMessage", methods[7].outputType)
        assertTrue(methods[7].clientStreaming)
        assertTrue(methods[7].serverStreaming)

        // Verify messages
        assertEquals(11, result.messages.size)

        // Verify HelloRequest
        val helloRequest = result.messages.find { it.name == "HelloRequest" }
        assertNotNull(helloRequest)
        assertEquals(1, helloRequest!!.fields.size)
        assertEquals("name", helloRequest.fields[0].name)
        assertEquals("string", helloRequest.fields[0].type)
        assertEquals(1, helloRequest.fields[0].number)

        // Verify HelloReply
        val helloReply = result.messages.find { it.name == "HelloReply" }
        assertNotNull(helloReply)
        assertEquals(1, helloReply!!.fields.size)
        assertEquals("message", helloReply.fields[0].name)

        // Verify UserResponse
        val userResponse = result.messages.find { it.name == "UserResponse" }
        assertNotNull(userResponse)
        assertEquals(7, userResponse!!.fields.size)
        assertEquals("id", userResponse.fields[0].name)
        assertEquals("int64", userResponse.fields[0].type)
        assertEquals("name", userResponse.fields[1].name)
        assertEquals("string", userResponse.fields[1].type)
        assertEquals("age", userResponse.fields[2].name)
        assertEquals("int32", userResponse.fields[2].type)
        assertEquals("email", userResponse.fields[3].name)
        assertEquals("string", userResponse.fields[3].type)
        assertEquals("user_type", userResponse.fields[4].name)
        assertEquals("UserType", userResponse.fields[4].type)
        assertEquals("created_at", userResponse.fields[5].name)
        assertEquals("int64", userResponse.fields[5].type)
        assertEquals("updated_at", userResponse.fields[6].name)
        assertEquals("int64", userResponse.fields[6].type)

        // Verify CreateUserRequest
        val createUserRequest = result.messages.find { it.name == "CreateUserRequest" }
        assertNotNull(createUserRequest)
        assertEquals(4, createUserRequest!!.fields.size)
        assertEquals("name", createUserRequest.fields[0].name)
        assertEquals("age", createUserRequest.fields[1].name)
        assertEquals("email", createUserRequest.fields[2].name)
        assertEquals("user_type", createUserRequest.fields[3].name)
        assertEquals("UserType", createUserRequest.fields[3].type)

        // Verify top-level enum
        assertEquals(1, result.enums.size)
        val userType = result.enums[0]
        assertEquals("UserType", userType.name)
        assertEquals(4, userType.values.size)
        assertEquals("UNKNOWN", userType.values[0].name)
        assertEquals(0, userType.values[0].number)
        assertEquals("ADMIN", userType.values[1].name)
        assertEquals(1, userType.values[1].number)
        assertEquals("USER", userType.values[2].name)
        assertEquals(2, userType.values[2].number)
        assertEquals("GUEST", userType.values[3].name)
        assertEquals(3, userType.values[3].number)

        // Verify no imports
        assertTrue(result.imports.isEmpty())
    }
}
