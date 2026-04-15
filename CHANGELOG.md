# Changelog

All notable changes to the EasyAPI plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.7] - 2026-04-15

### Added
-  add YAPI API update confirmation with multiple export modes (#1311)

### Fixed
- fix(extension): add fastjson and yapi to known extensions
-  Postman workspace and collections stuck on loading in modal dialog
-  add missing same-package imports in test resources

### Changed
-  remove enum mapping extensions and related configs (#1312)

---

## [3.0.6] - 2026-04-15

### Added
-  support URL paste in API Search Everywhere with path variable matching
-  support api.status&api.open rule and fix tag export for YAPI (#1307)

### Fixed
-  resolve JacksonConfigIntegrationTest failures for @JsonUnwrapped and @JsonView
-  implement proper enum.use.custom resolution with unified enum handling
-  ensure fieldContext is always available for field rule evaluation
-  inject fieldContext correctly into Groovy rule engine scripts
-  resolve EDT threading violations in API dashboard navigation (#652)

### Improved
- chore: cleanup unused test resource files
- amend: remove Recommend settings from plugin configuration
- amend: simplify SettingBinder and RuleEngine APIs

---

## [3.0.4] - 2026-04-13

### Added
-  add API endpoint selection panel to ExportDialog
-  add PsiType-aware rule evaluation for json.rule.convert
-  replace recommend config system with extension-based system
-  add ConfigSyncService with coroutine-based debounce for config reload
-  add on-demand Swagger config loading and API lifecycle events

### Fixed
-  resolve generic types in API method params
-  export multipart and file-like params as FILE type
-  NegationParser should return null for null input
-  respect ExportDialog output path and handle user cancellation properly
-  resolve IDE freeze on startup (issue #1299) and improve script engine management
-  prevent OOM from circular ObjectModel references in markdown formatter
-  resolve export silent failure and threading issues

### Changed
-  refactor event system and remove deprecated ActionContext

### Improved
- perf: use fine-grained ReadAction scoping in API exporters (#646)
- chore: remove unused SPI infrastructure and MethodFilter
- chore: remove redundant documentation
- chore: update project branding from EasyAPI to EasyYapi

---

## [3.0.3] - 2026-04-08

### Fixed
-  support inherited method annotations and dashboard navigation
-  support forked yapi success response format (#1296)
-  support inherited API mappings and correct Feign metadata access (#633)

---

## [3.0.2] - 2026-04-06

### Added

- add gRPC support
- support file-type form params in API dashboard
- refactor YapiApiClient - extract interface, add provider, fix project ID resolution

### Fixed

- correct version extraction in release workflow
- catch CancellationException in ReadActionDispatcher to prevent unhandled coroutine exception

### Improved

- amend: improve HTTP client export and add format filtering

---

## [3.0.1] - 2026-04-02

### Added

- add toString() methods to ScriptPsi contexts

### Fixed

- expire setting binder cache after timeout
- improve yapi token diagnostics and test coverage (#1292)
- improve API scan performance and add auto-scan toggle
- inherited controller export — superMethod perf, generic param scoping, resolver early-exit
- add path formatting/sanitization for YAPI export (#1288)

---

## [3.0.0] - TBD

### Added

- Complete rewrite with modern Kotlin architecture
- Kotlin coroutines for all async operations
- Structured concurrency with custom IdeDispatchers
- Type-safe API models using sealed classes
- Hybrid dependency injection (IntelliJ services + OperationScope)
- Improved PSI threading with self-contained read/write actions
- Enhanced type resolution system with generic context support
- Modern event bus implementation using Kotlin Flow

### Changed

- Migrated from Java/Guice to Kotlin/coroutines
- Replaced ThreadPool with CoroutineScope
- Updated minimum IDE version to 2023.1.3
- Updated minimum JDK version to 17
- Kotlin version updated to 2.1.0

### Improved

- Better error handling with Result types
- More maintainable code structure
- Improved performance with structured concurrency
- Enhanced language adapter system (Java, Kotlin, Scala, Groovy)

### Migration Notes

- This is a major version with breaking changes in internal APIs
- Plugin ID remains the same for seamless user migration
- All user-facing features from v2.x are preserved
- Configuration and settings are compatible with previous versions

---

## [2.8.4] - Previous Release

For changes in version 2.x and earlier, please refer to
the [easy-yapi repository](https://github.com/tangcent/easy-yapi).
