# Changelog

All notable changes to the EasyAPI plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
-  export swagger3 `@Parameter#example` as the query/form/path/header parameter example

---

## [3.2.1] - 2026-07-14

### Added
- feat(ai): enrich PSI perception tools with name resolution, type FQNs, and method bodies
- feat(ai): add loop-safety and chat-retry to rule authoring agent
- feat(ai): namespace per-app env vars across exporters and agent (#1417)
-  add Hoppscotch export channel as beta feature (#1416)
- feat(ai): teach rule agent cross-endpoint workflow patterns
- feat(curl): add variable rendering, output options, pre-scripts, and reusable cURL builder

### Fixed
-  inherit mapping annotations from bounded-generic interfaces (#1343) (#1415)

### Improved
- chore: drop spec references from build comment and YapiSettings KDoc
- docs(readme): sync Architecture section with v3.0 channel EP and add YAML field conversion (#1414)

---

## [3.2.0] - 2026-07-05

### Added
-  refactor BodyView to carry ObjectModel with render helpers
-  free-form Markdown export templates with i18n and remote source support (#1411)
-  support `###include <path-or-url>` directive for loading config from local files and remote URLs (#1410)

### Fixed
-  YAML export drops @ConfigurationProperties prefix
-  FieldsTo* resolves caret class instead of first class in file
-  converge markdown channel template files to byte-identity with easy-api
-  respect method-level selection when exporting APIs (#1407) (#1408)

### Changed
-  restructure settings modules, rename Intelligent tab, group panel sections
-  consolidate exporters into self-contained channel folders with modular settings (#1412)

### Improved
- docs: add DeepWiki badge to READMEs (#1409)

---

## [3.1.9] - 2026-06-28

### Added
- feat: add Postman environment sync with dashboard integration (#722)
- feat(rules): add folder-based rule management with AI assistant (#1398)
- feat(logging): hide EasyAPI console tool window when log level is SILENT (#1396)
- feat(logging): enforce logging channel discipline with SILENT default console (#1394)
- feat(logging): make the plugin self-explanatory when it fails (#1393)

### Fixed
- fix: settings/AI-panel UI interactions (modality, stale proposals, unsaved edits) (#1404)
- fix(test): add AwaitUtils and stabilize flaky ApiIndexManagerTest (#1400)
- fix(dashboard): prevent header duplication when switching APIs in ApiDashboard (#1397)

### Changed
-  improve password access logic with a dedicated AiApiKeyStore (#1403)

### Improved
- build(skill): sync shared knowledge base to skill (#1405)
- docs: fix broken links and incorrect setting in knowledge base (#1402)
- amend: improve SettingsPanel layout and extract shared size utilities (#1401)
- docs: point plugin description Guide links to easyyapi.github.io (#1399)
- style: replace tool window icons for EasyAPI and API Dashboard (#1395)

---

## [3.1.8] - 2026-06-21

### Added
-  add YAML field format support with dynamic extension architecture (#1390)
-  implement enum resolution spec with @JsonValue/@EnumValue and @see support (#1387)

### Fixed
-  fix annotation expression bugs and add comprehensive integration tests for all extensions (#1386)
-  preserve dashboard edits when switching between APIs (#1389)
-  @Api ignored for class folder names (#1384)

### Improved
- amend: extract ObjectModelVisitTracker to centralize visit-count logic (#1391)
- docs: add git-commit skill, AGENTS.md, and fix skill accuracy issues (#1385)

---

## [3.1.7] - 2026-06-15

### Added
-  make yapiInfo mutable so save.before scripts can customize YAPI exports (#1379)
-  add toJson/toJson5 to script class context and define context interfaces (#1369)

### Fixed
-  req object add jsonType (#1380)
-  replace fragile delay with waitForClass to fix flaky test (#1377)
-  add delay after loadFile in ProjectClassAvailabilityServiceTest to prevent flaky failures (#1375)
-  make ApiIndex.invalidate() atomic to prevent race condition (#1376)
-  channel export actions missing from Keymap settings (#1371)
-  invalidate cache on settings/branch change to prevent occasional test failures (#1368)
-  annotation array attributes are stringified instead of expanded (#1363) (#1364)

### Improved
- test: replace jacoco with kotlinx kover for coverage reporting and add variety of new unit tests across codebase
- docs: remove contributing section and contributor image from readmes
- ci: fix pr-release workflow checkout and script injection (#1374)
- build: fix github workflow permissions and add missing configs (#1373)
- fix : method.return support generic canonical types (#1372)
- chore: upgrade codecov-action from v5 to v7 (#1365)

---

## [3.1.6] - 2026-06-07

### Fixed
-  api.tag=#tag captures all @tag values instead of only the first (#1361)
-  avoid double-zip when downloading PR artifact
-  properly handle array/collection return values from groovy scripts (#1358)
- fix(source helper): fix source file resolution for library classes
-  Fix binary incompatibility with IntelliJ 2026.2 by removing Groovy runtime dependencies

### Improved
- chore: use org.jetbrains.changelog plugin for proper HTML rendering in marketplace (#1359)

---

## [3.1.5] - 2026-06-01

### Added
-  implement pluggable exporter architecture with extension points (#1346)

### Fixed
-  handle IndexNotReadyException during IDEA Dumb Mode export (#1355)
-  resolve inherited method/field handling in ClassType (#1352) (#1354)
-  wrap PsiAnnotation.owner access in read action in JaxRsClassExporter (#1350)

### Changed
-  reorganize project structure with feature-based package layout (#1347)

---

## [3.1.4] - 2026-05-21

### Added
-  add setting to enable/disable API method gutter icon (#1340)

### Fixed
-  resolve IntelliJ IDEA 2026.1 (IU-261) compatibility issues (#1344)
-  leave tag as null instead of empty array when no tags exist (#1338)
-  resolve ClassCastException on double-shift Search Everywhere (#1336) (#1337)

---

## [3.1.3] - 2026-05-11

### Added
-  add yapi project resolution fallback from method to class level (#1334)

### Fixed
-  DocMetadataResolver now correctly reads settings.pathMulti dynamically
-  remove duplicated comments when combining doc comments with rule-based docs

---

## [3.1.2] - 2026-05-07

### Fixed
-  support extracting comments from JAR classes and fix gRPC runtime Guava resolution (#1332)
-  correct log level filtering semantic inversion (#1330)

---

## [3.1.1] - 2026-04-28

### Added
-  show response JSON demo when viewing API endpoint
-  add depth and element count limits to prevent OOM in object model building

### Fixed
-  fix config parsing and extension sources loading issues
-  keep api dashboard in bottom tool window

---

## [3.1.0] - 2026-04-26

### Added
-  add Postman-compatible script execution with PmScriptExecutor as project service
-  support Scala/Kotlin/Groovy language adapters for PSI integration
-  improve settings panel usability
-  extract ClassNameConstants and InheritanceHelper with cached inheritance checks

### Changed
-  extract shared EndpointBuilder from ClassExporters

### Improved
- test: add unit tests for ScriptSupport, EventBus, RequestPersistence, RepositoryService, MavenHelper, ModuleHelper
- docs: update README files with comprehensive feature documentation

---

## [3.0.9] - 2026-04-20

### Fixed
-  use full version in release download link

### Improved
- amend: improve YapiUpdateConfirmationDialog UI layout (#1322)
- test: add unit tests for IDE actions, settings, and utilities

---

## [3.0.8] - 2026-04-19

### Added
-  add YAPI mock rules support with separate extension config (#1320)
-  add variable resolution support in ApiDashboard
-  handle properties.prefix rule in FieldsToPropertiesAction
-  render markdown to HTML when exporting APIs to YAPI (#1318)
-  enhance script PSI context with class introspection methods and fix Swing dispatcher modality
-  remember export dialog options for better UX
-  add concurrent API scanning option for better performance
-  add rule-based configuration support with cache invalidation

### Improved
- amend: rename rule key 'module' to 'yapi.project' with backward-compatible aliases (#1319)
- test: add missing unit tests and fix test failures
- test: improve test coverage across multiple packages
- perf: optimize rule engine with Flow-based lazy evaluation
- perf: optimize exporter selection with framework availability caching

---

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
