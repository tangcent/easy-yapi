# Test Case Plan for Commit: feat: add gRPC support

**Commit:** `9f6bd16bd1f127569eb2123089b530b5fccebf9b`  
**Author:** tangcent  
**Date:** Fri Apr 3 21:13:57 2026 +0800  
**Message:** feat: add gRPC support

---

## Summary

This commit adds comprehensive gRPC support to the EasyAPI plugin. It includes new gRPC exporters, client implementations, descriptor resolvers, and settings panels. This document outlines the test coverage plan for all added and modified classes.

---

## 1. New Classes Requiring Tests (Added - A)

### 1.1 gRPC Exporter Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `GrpcClassExporter` | `exporter/grpc/GrpcClassExporter.kt` | **HIGH** | ✅ Has test | Core exporter that parses gRPC service classes and extracts API endpoints. Uses `GrpcServiceRecognizer` to identify gRPC classes, `GrpcMethodResolver` to discover RPC methods, and `GrpcTypeParser` to parse protobuf message types. Outputs `ApiEndpoint` with `GrpcMetadata` containing service name, package name, and streaming type. |
| `GrpcMethodResolver` | `exporter/grpc/GrpcMethodResolver.kt` | **HIGH** | ✅ Has test | Project service that resolves RPC methods from gRPC service implementation classes. Analyzes method signatures to detect streaming types (UNARY, SERVER_STREAMING, CLIENT_STREAMING, BIDIRECTIONAL). Extracts service/package names from generated `XxxGrpc.XxxImplBase` base class hierarchy. Returns `GrpcMethodInfo` with full path, request/response types, and descriptions. |
| `GrpcServiceRecognizer` | `exporter/grpc/GrpcServiceRecognizer.kt` | **HIGH** | ✅ Has test | Recognizes gRPC service implementation classes by checking: (1) extends `io.grpc.BindableService` directly or through ImplBase superclass, (2) annotated with `@GrpcService` (grpc-spring-boot-starter) or meta-annotations. Implements `ApiClassRecognizer` interface for integration with the exporter framework. |
| `GrpcTypeParser` | `exporter/grpc/GrpcTypeParser.kt` | **MEDIUM** | ❌ No test | Parses protobuf message types from `PsiClass` to build `ObjectModel` representations. Handles protobuf field naming conventions (snake_case to camelCase), repeated fields, nested messages, and enum types. Used by `GrpcClassExporter` to build request/response body models. |

**Test Scenarios for gRPC Exporter Classes:**

1. **GrpcClassExporter**
   - Export unary RPC method
   - Export server-streaming RPC method
   - Export client-streaming RPC method
   - Export bidirectional streaming RPC method
   - Handle class without gRPC annotations (should return empty)
   - Handle malformed gRPC class
   - Extract description from Javadoc
   - Build request/response body models

2. **GrpcMethodResolver**
   - Resolve unary method signature: `(Req, StreamObserver<Resp>) -> void`
   - Resolve client-streaming method signature: `(StreamObserver<Resp>) -> StreamObserver<Req>`
   - Extract service name from `XxxImplBase` superclass
   - Extract package name from outer class
   - Filter out Object methods (equals, hashCode, toString)
   - Filter out lifecycle methods (bindService, serviceImpl)
   - Handle class without ImplBase superclass (fallback)

3. **GrpcServiceRecognizer**
   - Recognize class extending `BindableService` directly
   - Recognize class extending `XxxImplBase`
   - Recognize class with `@GrpcService` annotation
   - Recognize class with meta-annotation (custom annotation annotated with `@GrpcService`)
   - Reject non-gRPC class
   - Rule engine override

4. **GrpcTypeParser**
   - Parse simple message with primitive fields
   - Parse message with nested message fields
   - Parse message with repeated fields
   - Parse message with enum fields
   - Handle snake_case to camelCase conversion
   - Handle optional/required field labels

---

### 1.2 gRPC Core Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `CompositeDescriptorResolver` | `grpc/CompositeDescriptorResolver.kt` | **HIGH** | ✅ Has test | Project service that chains multiple descriptor resolver strategies in priority order: (1) ProtoFileResolver, (2) StubClassResolver, (3) ServerReflectionResolver. First successful resolver wins. Provides fallback mechanism for different project configurations. |
| `DescriptorResolver` | `grpc/DescriptorResolver.kt` | **MEDIUM** | ❌ No test | Interface for resolving gRPC method descriptors. Implementations provide different approaches to obtain protobuf message descriptors for gRPC service methods. Returns `ResolvedDescriptor` with input/output descriptors and source strategy. Test via implementations. |
| `DynamicJarClient` | `grpc/DynamicJarClient.kt` | **HIGH** | ✅ Has test | Application service implementing `GrpcClient` using dynamic class loading. Loads gRPC runtime JARs from Maven/Gradle cache and uses reflection to: create channels/stubs, convert JSON↔protobuf, invoke unary RPCs. Uses parent-last classloader to ensure gRPC JARs take precedence. Supports invocation without compile-time stubs. |
| `GrpcArtifacts` | `grpc/GrpcArtifacts.kt` | **LOW** | ❌ No test | Constants and data classes for gRPC artifact management. Defines required gRPC artifacts (netty-shaded, protobuf, stub) and additional artifacts (gson, protobuf-java-util). Provides default configurations and artifact parsing utilities. |
| `GrpcClient` | `grpc/GrpcClient.kt` | **HIGH** | ✅ Has test | Interface for gRPC client implementations. Defines `invoke(host, path, body)` for calling gRPC methods and `isAvailable()` for checking runtime availability. Implemented by `DynamicJarClient`. |
| `GrpcResult` | `grpc/GrpcResult.kt` | **MEDIUM** | ✅ Has test | Data class wrapping gRPC call results. Contains response body (JSON string), error flag, status code, and status name. Includes `GrpcStatus` object with standard gRPC status codes (OK, CANCELLED, UNKNOWN, etc.) and utility functions. |
| `GrpcRuntimeResolver` | `grpc/GrpcRuntimeResolver.kt` | **HIGH** | ✅ Has test | Project service that resolves gRPC runtime JARs from Maven local or Gradle cache. Searches for newest complete version with all required artifacts. Supports additional JARs from settings. Returns `ResolvedRuntime` with JAR paths and version. Used by `DynamicJarClient` to create classloader. |
| `ProtoFileResolver` | `grpc/ProtoFileResolver.kt` | **HIGH** | ✅ Has test | Resolves gRPC method descriptors by scanning project VFS for `.proto` files. Parses proto text using `ProtoUtils`, builds `FileDescriptor` objects locally. Caches results by file modification timestamp. Works without generated stubs or server reflection. |
| `ProtoUtils` | `grpc/ProtoUtils.kt` | **HIGH** | ✅ Has test | Utility object for protobuf operations. Parses `.proto` file text into structured data (`ProtoParseResult`, `ServiceDef`, `MessageDef`, `FieldDef`, `EnumDef`). Maps Java types to protobuf types. Maps protobuf scalar types to FieldDescriptorProto.Type enum names. Handles nested messages and enums. |
| `ServerReflectionResolver` | `grpc/ServerReflectionResolver.kt` | **HIGH** | ✅ Has test | Resolves gRPC method descriptors via gRPC Server Reflection protocol. Queries running gRPC server for service descriptors at runtime. Supports both v1 (grpc-services >= 1.39) and v1alpha reflection APIs. Requires active `ManagedChannel`. Enables discovery without .proto files or stubs. |
| `StubClassResolver` | `grpc/StubClassResolver.kt` | **HIGH** | ✅ Has test | Resolves gRPC method descriptors from generated stub classes via PSI analysis. Reads `FileDescriptor` from generated `getServiceDescriptor()` method. Extracts method descriptors by matching method names. Works with any generated gRPC stub (protoc, grpc-java). |

**Test Scenarios for gRPC Core Classes:**

1. **CompositeDescriptorResolver**
   - Resolve via ProtoFileResolver (first priority)
   - Fallback to StubClassResolver when proto files not found
   - Fallback to ServerReflectionResolver when stubs not found
   - Return null when all resolvers fail
   - Cache invalidation

2. **DynamicJarClient**
   - Invoke unary RPC successfully
   - Handle invalid host/port
   - Handle invalid service path format
   - Handle descriptor resolution failure
   - Handle gRPC status errors (UNAVAILABLE, NOT_FOUND, etc.)
   - JSON to protobuf conversion
   - Protobuf to JSON conversion
   - Classloader isolation (parent-last)
   - Channel shutdown

3. **GrpcRuntimeResolver**
   - Resolve from Maven local repository
   - Resolve from Gradle cache
   - Resolve from custom repository
   - Select newest complete version
   - Handle incomplete versions (missing required artifacts)
   - Include additional JARs from settings
   - Handle empty repository

4. **ProtoFileResolver**
   - Resolve from .proto file in project
   - Handle multiple .proto files
   - Handle nested messages
   - Handle imported types
   - Cache by modification timestamp
   - Handle parse errors

5. **ProtoUtils**
   - Parse simple proto file
   - Parse proto with nested messages
   - Parse proto with enums
   - Parse service with streaming methods
   - Map Java types to proto types
   - Map proto types to FieldDescriptorProto.Type
   - Qualify type names with package

6. **ServerReflectionResolver**
   - Resolve via v1 reflection API
   - Fallback to v1alpha API
   - Handle missing reflection on server
   - Handle connection errors
   - Handle unknown service

7. **StubClassResolver**
   - Resolve from generated stub class
   - Handle method name case differences
   - Handle missing service descriptor
   - Handle missing method in descriptor

---

### 1.3 Repository Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `RepositoryConfig` | `repository/RepositoryConfig.kt` | **MEDIUM** | ✅ Has test | Data class representing a Maven/Gradle repository configuration. Supports `MAVEN_LOCAL`, `GRADLE_CACHE`, and `CUSTOM` types. Provides serialization/deserialization for settings storage. `DefaultRepositories` object detects default Maven local and Gradle cache locations from environment. |
| `RepositoryService` | `repository/RepositoryService.kt` | **HIGH** | ❌ No test | Project service for managing repository configurations. Reads user-configured repositories from settings, falls back to auto-detected defaults (Maven local, Gradle cache). Provides both enabled and all repositories for UI and runtime resolution. |

**Test Scenarios for Repository Classes:**

1. **RepositoryConfig**
   - Parse Maven local config string
   - Parse Gradle cache config string
   - Parse custom repository config string
   - Serialize config to string
   - Handle malformed config strings
   - Detect default Maven local from system property
   - Detect default Gradle cache from environment

2. **RepositoryService**
   - Get user-configured repositories
   - Fall back to auto-detected repositories
   - Filter disabled repositories
   - Handle empty settings

---

### 1.4 Settings Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `GrpcSettingsPanel` | `settings/ui/GrpcSettingsPanel.kt` | **MEDIUM** | ❌ No test | Settings UI panel for gRPC configuration. Provides checkboxes for enabling gRPC support and gRPC call functionality. Shows runtime packages table with artifact coordinates and versions. Supports auto-detection of gRPC runtime, adding/editing/removing artifacts, and adding additional JARs. Implements `SettingsPanel` interface. |

**Test Scenarios for GrpcSettingsPanel:**

1. Load settings into panel
2. Save settings from panel
3. Auto-detect runtime versions
4. Add/edit/remove artifact configurations
5. Add/remove additional JARs
6. Toggle gRPC call enabled visibility

---

## 2. Modified Classes Requiring Test Updates (Modified - M)

### 2.1 Core DI

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `OperationScope` | `core/di/OperationScope.kt` | **MEDIUM** | ❌ No test | Lightweight DI container for managing service bindings. Modified to add new bindings for gRPC-related services. Provides builder pattern for configuration, lazy binding support, and auto-creation via constructor injection. Verify new gRPC service bindings work correctly. |

**Test Scenarios:**
- Verify gRPC service bindings in `addSpiBindings`
- Test lazy resolution of gRPC services
- Test auto-creation of services with gRPC dependencies

---

### 2.2 Dashboard Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `ApiDashboardPanel` | `dashboard/ApiDashboardPanel.kt` | **MEDIUM** | ✅ Has test | Main dashboard UI panel. Modified to support gRPC endpoints display. Verify gRPC endpoints appear in tree, gRPC icons render correctly, and gRPC metadata displays properly. |
| `ApiScanner` | `dashboard/ApiScanner.kt` | **HIGH** | ❌ No test | Project service that scans for API endpoints. Modified to include `GrpcClassExporter` in the exporter chain. Now scans for gRPC service classes in addition to Spring MVC, JAX-RS, and Feign. Verify gRPC classes are discovered and exported. |
| `ApiTreeCellRenderer` | `dashboard/ApiTreeCellRenderer.kt` | **LOW** | ✅ Has test | Tree cell renderer for API tree. Modified to show gRPC-specific icons for gRPC endpoints. Verify gRPC icon displays. |
| `EndpointDetailsPanel` | `dashboard/EndpointDetailsPanel.kt` | **MEDIUM** | ❌ No test | Panel showing endpoint details. Modified to display gRPC-specific metadata (streaming type, service name, package name). Verify gRPC details render correctly. |
| `RequestEditCacheService` | `dashboard/RequestEditCacheService.kt` | **LOW** | ❌ No test | Service for caching request edits. Modified to handle gRPC request caching. Verify gRPC requests are cached and restored. |

**Test Scenarios for Dashboard Classes:**

1. **ApiScanner**
   - Scan project for gRPC services
   - Include gRPC endpoints in scan results
   - Filter by gRPC service recognizer
   - Progress reporting for gRPC scan

2. **EndpointDetailsPanel**
   - Display gRPC endpoint path
   - Display streaming type (UNARY, SERVER_STREAMING, etc.)
   - Display service name and package name
   - Display request/response protobuf models

---

### 2.3 Exporter Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `CompositeApiClassRecognizer` | `exporter/core/CompositeApiClassRecognizer.kt` | **HIGH** | ❌ No test | Combines multiple `ApiClassRecognizer` implementations. Modified to include `GrpcServiceRecognizer`. Now recognizes gRPC service classes in addition to Spring MVC, JAX-RS, and Feign. Verify gRPC recognition integration. |
| `CurlFormatter` | `exporter/curl/CurlFormatter.kt` | **MEDIUM** | ✅ Has test | Formats API endpoints as cURL commands. Modified to support gRPC endpoints (using `grpcurl` format). Verify gRPC curl output. |
| `FeignClassExporter` | `exporter/feign/FeignClassExporter.kt` | **MEDIUM** | ✅ Has test | Exports Feign client interfaces. Verify compatibility with gRPC changes (no regression). |
| `HttpClientFileFormatter` | `exporter/formatter/HttpClientFileFormatter.kt` | **MEDIUM** | ❌ No test | Formats endpoints as IntelliJ HTTP Client files. Modified to support gRPC endpoints with gRPC request format. Verify gRPC HTTP client output. |
| `HttpClientFileFormatter` | `exporter/httpclient/HttpClientFileFormatter.kt` | **MEDIUM** | ✅ Has test | Alternative HTTP client formatter. Verify gRPC support. |
| `JaxRsClassExporter` | `exporter/jaxrs/JaxRsClassExporter.kt` | **LOW** | ✅ Has test | Exports JAX-RS resources. Verify compatibility with gRPC changes (no regression). |
| `DefaultMarkdownFormatter` | `exporter/markdown/DefaultMarkdownFormatter.kt` | **MEDIUM** | ✅ Has test | Formats endpoints as Markdown documentation. Modified to include gRPC streaming type and metadata in output. Verify gRPC markdown format. |
| `ApiModels` | `exporter/model/ApiModels.kt` | **LOW** | ❌ No test | Data models for API endpoints. Modified to add `GrpcMetadata` and `GrpcStreamingType`. Verify model serialization. |
| `PostmanFormatter` | `exporter/postman/PostmanFormatter.kt` | **MEDIUM** | ✅ Has test | Formats endpoints for Postman collection export. Modified to support gRPC endpoints with gRPC request format. Verify gRPC Postman output. |
| `ActuatorEndpointScanner` | `exporter/springmvc/ActuatorEndpointScanner.kt` | **LOW** | ✅ Has test | Scans Spring Actuator endpoints. Verify compatibility (no regression). |
| `SpringMvcClassExporter` | `exporter/springmvc/SpringMvcClassExporter.kt` | **LOW** | ✅ Has test | Exports Spring MVC controllers. Verify compatibility (no regression). |

**Test Scenarios for Exporter Classes:**

1. **CompositeApiClassRecognizer**
   - Recognize gRPC service class
   - Include gRPC annotations in target annotations
   - Combine with other recognizers

2. **CurlFormatter**
   - Format gRPC unary call as grpcurl
   - Include streaming type in output
   - Handle gRPC metadata

3. **HttpClientFileFormatter**
   - Format gRPC request for IntelliJ HTTP Client
   - Include protobuf JSON body
   - Handle streaming types

4. **DefaultMarkdownFormatter**
   - Format gRPC endpoint documentation
   - Show streaming type
   - Show service/package names
   - Show request/response protobuf schema

5. **PostmanFormatter**
   - Export gRPC endpoint to Postman
   - Include gRPC-specific metadata
   - Handle protobuf JSON body

---

### 2.4 IDE Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `ApiListCellRenderer` | `ide/dialog/ApiListCellRenderer.kt` | **LOW** | ❌ No test | List cell renderer for API selection dialogs. Modified to show gRPC-specific icons and labels. Verify gRPC display. |
| `ApiMethodLineMarkerProvider` | `ide/linemarker/ApiMethodLineMarkerProvider.kt` | **MEDIUM** | ❌ No test | Provides line markers for API methods in editor gutter. Modified to show markers for gRPC service methods. Verify gRPC method markers. |
| `ApiSearchEverywhereContributor` | `ide/search/ApiSearchEverywhereContributor.kt` | **MEDIUM** | ✅ Has test | Contributes API endpoints to IDE's Search Everywhere. Modified to include gRPC endpoints in search results. Verify gRPC search. |
| `ApiSearchResultRenderer` | `ide/search/ApiSearchResultRenderer.kt` | **LOW** | ❌ No test | Renders search results for API endpoints. Modified to show gRPC-specific icons and metadata. Verify gRPC result rendering. |
| `ProgressHelper` | `ide/support/ProgressHelper.kt` | **LOW** | ❌ No test | Helper for progress indicators. Modified to support gRPC-specific progress messages. Verify gRPC progress. |

**Test Scenarios for IDE Classes:**

1. **ApiMethodLineMarkerProvider**
   - Show marker on gRPC service method
   - Show marker on gRPC ImplBase method
   - Navigate to endpoint details

2. **ApiSearchEverywhereContributor**
   - Search for gRPC endpoint by method name
   - Search for gRPC endpoint by service name
   - Include gRPC endpoints in results

---

### 2.5 PSI Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `StandardDocHelper` | `psi/helper/StandardDocHelper.kt` | **LOW** | ❌ No test | Helper for extracting documentation from PSI elements. Modified to handle gRPC Javadoc on service methods. Verify gRPC doc extraction. |
| `JsonType` | `psi/type/JsonType.kt` | **MEDIUM** | ❌ No test | JSON type representation for API bodies. Modified to support protobuf JSON mapping for gRPC. Verify protobuf JSON type handling. |

---

### 2.6 Settings Classes

| Class | Path | Priority | Test Status | Description |
|-------|------|----------|-------------|-------------|
| `RuleKeys` | `rule/RuleKeys.kt` | **LOW** | ❌ No test | Rule engine keys for custom rules. Modified to add `CLASS_IS_GRPC` key for custom gRPC recognition rules. Verify new key. |
| `Settings` | `settings/Settings.kt` | **MEDIUM** | ❌ No test | Plugin settings data class. Modified to add gRPC-specific settings: `grpcEnable`, `grpcCallEnabled`, `grpcArtifactConfigs`, `grpcAdditionalJars`, `grpcRepositories`. Verify settings persistence. |
| `ApplicationSettingsState` | `settings/state/ApplicationSettingsState.kt` | **MEDIUM** | ❌ No test | Application-level settings state. Modified to store gRPC settings. Verify state persistence. |
| `SettingsSupport` | `settings/state/SettingsSupport.kt` | **LOW** | ❌ No test | Settings support utilities. Modified to support gRPC settings. Verify utilities. |
| `EasyApiSettingsConfigurable` | `settings/ui/EasyApiSettingsConfigurable.kt` | **MEDIUM** | ❌ No test | Main settings configurable. Modified to include `GrpcSettingsPanel`. Verify gRPC panel integration. |
| `SettingsPanels` | `settings/ui/SettingsPanels.kt` | **LOW** | ❌ No test | Settings panel interface and utilities. Modified to support gRPC panel. Verify panel registration. |

**Test Scenarios for Settings Classes:**

1. **Settings**
   - Serialize/deserialize gRPC settings
   - Default values for gRPC settings
   - Validate artifact config strings
   - Validate repository config strings

2. **EasyApiSettingsConfigurable**
   - Show gRPC settings panel
   - Navigate to gRPC settings
   - Save gRPC settings

---

## 3. Deleted Classes

| Class | Path | Notes |
|-------|------|-------|
| `RequestPanel` | `dashboard/RequestPanel.kt` | Replaced by `EndpointDetailsPanel` which provides better support for both REST and gRPC endpoints |
| `RequestPanelTest` | `test/.../dashboard/RequestPanelTest.kt` | Test deleted with class - no migration needed |

---

## 4. Test Implementation Priority

### Phase 1: Critical gRPC Core Tests (HIGH Priority)

| # | Test Class | Target Class | Key Scenarios |
|---|------------|--------------|---------------|
| 1 | `GrpcClassExporterTest` | `GrpcClassExporter` | Export all streaming types, handle non-gRPC class |
| 2 | `GrpcMethodResolverTest` | `GrpcMethodResolver` | Resolve method signatures, extract service/package names |
| 3 | `GrpcServiceRecognizerTest` | `GrpcServiceRecognizer` | Recognize by BindableService, ImplBase, @GrpcService |
| 4 | `GrpcClientTest` | `GrpcClient` | Interface contract test |
| 5 | `GrpcRuntimeResolverTest` | `GrpcRuntimeResolver` | Resolve from Maven/Gradle, select newest version |
| 6 | `RepositoryServiceTest` | `RepositoryService` | Get repositories, fallback to defaults |

### Phase 2: Important Integration Tests (HIGH Priority)

| # | Test Class | Target Class | Key Scenarios |
|---|------------|--------------|---------------|
| 1 | `CompositeApiClassRecognizerTest` | `CompositeApiClassRecognizer` | Include gRPC recognition |
| 2 | `ApiScannerTest` | `ApiScanner` | Scan for gRPC services |
| 3 | `GrpcEndToEndTest` | Full workflow | Create proto → generate stub → export → call |

### Phase 3: Medium Priority Tests

| # | Test Class | Target Class | Key Scenarios |
|---|------------|--------------|---------------|
| 1 | `GrpcTypeParserTest` | `GrpcTypeParser` | Parse protobuf message types |
| 2 | `GrpcResultTest` | `GrpcResult` | Status code handling |
| 3 | `GrpcSettingsPanelTest` | `GrpcSettingsPanel` | UI interactions |
| 4 | `HttpClientFileFormatterTest` (formatter) | `HttpClientFileFormatter` | gRPC HTTP client format |
| 5 | `JsonTypeTest` | `JsonType` | Protobuf JSON mapping |
| 6 | `RepositoryConfigTest` | `RepositoryConfig` | Parse/serialize configs |

### Phase 4: Low Priority Tests

| # | Test Class | Target Class | Key Scenarios |
|---|------------|--------------|---------------|
| 1 | `GrpcArtifactsTest` | `GrpcArtifacts` | Constants verification |
| 2 | `ProgressHelperTest` | `ProgressHelper` | Progress tracking |
| 3 | `ApiListCellRendererTest` | `ApiListCellRenderer` | UI rendering |
| 4 | `ApiSearchResultRendererTest` | `ApiSearchResultRenderer` | Search result rendering |
| 5 | `StandardDocHelperTest` | `StandardDocHelper` | Documentation helper |

---

## 5. Test File Locations

New test files should be created in:

```
src/test/kotlin/com/itangcent/easyapi/
├── exporter/grpc/
│   ├── GrpcClassExporterTest.kt
│   ├── GrpcMethodResolverTest.kt
│   ├── GrpcServiceRecognizerTest.kt
│   └── GrpcTypeParserTest.kt
├── grpc/
│   ├── GrpcClientTest.kt
│   ├── GrpcResultTest.kt
│   ├── GrpcRuntimeResolverTest.kt
│   └── DescriptorResolverTest.kt (interface tests)
├── repository/
│   ├── RepositoryConfigTest.kt
│   └── RepositoryServiceTest.kt
└── settings/ui/
    └── GrpcSettingsPanelTest.kt
```

---

## 6. Existing Tests to Verify

The following existing tests were modified in this commit and should be verified:

| Test File | Status | Notes |
|-----------|--------|-------|
| `CompositeDescriptorResolverTest.kt` | ✅ Added | New test for composite resolver |
| `DynamicJarClientIntegrationTest.kt` | ✅ Added | Integration test for dynamic client |
| `GrpcMockServer.kt` | ✅ Added | Test utility for mock gRPC server |
| `JsonCleanerTest.kt` | ✅ Added | Test for JSON cleaning utility |
| `ProtoFileResolverTest.kt` | ✅ Added | Test for proto file resolver |
| `ProtoUtilsTest.kt` | ✅ Added | Test for proto utilities |
| `ServerReflectionResolverTest.kt` | ✅ Added | Test for server reflection resolver |
| `StubClassResolverTest.kt` | ✅ Added | Test for stub class resolver |

---

## 7. Test Coverage Summary

| Category | Total | Has Test | Needs Test |
|----------|-------|----------|------------|
| New gRPC Exporter Classes | 4 | 3 | 1 |
| New gRPC Core Classes | 11 | 8 | 3 |
| New Repository Classes | 2 | 1 | 1 |
| New Settings Classes | 1 | 0 | 1 |
| Modified Core Classes | 1 | 0 | 1 |
| Modified Dashboard Classes | 5 | 2 | 3 |
| Modified Exporter Classes | 11 | 8 | 3 |
| Modified IDE Classes | 5 | 1 | 4 |
| Modified PSI Classes | 2 | 0 | 2 |
| Modified Settings Classes | 6 | 0 | 6 |
| **Total** | **48** | **23** | **25** |

---

## 8. Test Patterns and Utilities

### Test Base Classes

Based on the project structure, use these test base classes:

- `LightPlatformCodeInsightFixture4TestCase` - For tests requiring PSI but not full project
- `HeavyPlatformTestCase` - For tests requiring full project structure
- Use `ApiFixtures` for creating test API endpoints

### Test Data

Create test data files in `src/test/testData/grpc/`:
- Sample proto files
- Generated stub classes
- Sample gRPC service implementations

### Mock Utilities

- `GrpcMockServer` - Already exists for integration tests
- Create mock `GrpcClient` for unit tests
- Create mock `DescriptorResolver` for exporter tests

---

## 9. Next Steps

1. ✅ Review each class without test coverage
2. ✅ Prioritize based on complexity and usage
3. ⬜ Create test cases following the project's test patterns
4. ⬜ Ensure integration tests cover the full gRPC workflow
5. ⬜ Update this document as tests are implemented
