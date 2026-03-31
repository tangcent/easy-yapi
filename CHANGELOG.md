# Changelog

All notable changes to the EasyAPI plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

For changes in version 2.x and earlier, please refer to the [easy-yapi repository](https://github.com/tangcent/easy-yapi).
