package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

class ProtoUtilsDebugTest {

    @Test
    fun debugFlattenEnums() {
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
        
        println("=== Parse Result ===")
        println("Top-level enums: ${result.enums.map { it.name }}")
        println("Top-level messages: ${result.messages.map { it.name }}")
        
        for (msg in result.messages) {
            println("\nMessage: ${msg.name}")
            println("  Nested enums: ${msg.nestedEnums.map { it.name }}")
            println("  Nested messages: ${msg.nestedMessages.map { it.name }}")
            
            for (nested in msg.nestedMessages) {
                println("  Nested message: ${nested.name}")
                println("    Nested enums: ${nested.nestedEnums.map { it.name }}")
                println("    Nested messages: ${nested.nestedMessages.map { it.name }}")
            }
        }
        
        val flattened = ProtoUtils.flattenEnums(result)
        println("\n=== Flattened Enums ===")
        println("Count: ${flattened.size}")
        println("Names: ${flattened.map { it.name }}")
        
        assertEquals(3, flattened.size)
    }
    
    @Test
    fun debugDemoProto() {
        val text = """
            syntax = "proto3";

            package com.itangcent.springboot.demo.grpc;

            service DemoService {
                rpc SayHello (HelloRequest) returns (HelloReply) {}
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
            }

            message UpdateUserRequest {
                int64 id = 1;
            }

            message DeleteUserRequest {
                int64 id = 1;
            }

            message DeleteUserResponse {
                bool success = 1;
            }

            message UserResponse {
                int64 id = 1;
            }

            message ListUsersRequest {
                int32 page = 1;
            }

            message UploadUsersResponse {
                int32 total_count = 1;
            }

            message ChatMessage {
                int64 user_id = 1;
            }

            enum UserType {
                UNKNOWN = 0;
            }
        """.trimIndent()
        
        val result = ProtoUtils.parseProtoText(text)
        
        println("=== Messages ===")
        println("Count: ${result.messages.size}")
        result.messages.forEach { msg ->
            println("  - ${msg.name}")
        }
        
        assertEquals(11, result.messages.size)
    }
}
