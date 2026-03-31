# EasyAPI

[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Percentage of issues still open")

English | [中文](README_CN.md)

> **Note:** This is the v3.0 rewrite of EasyAPI. For the source code of stable v2.x releases, see the [`stable/v2.x.x`](https://github.com/tangcent/easy-yapi/tree/stable/v2.x.x) branch.

## Feature

- [Export API Documents](https://easyyapi.com/documents/use.html)
- [Send API requests](http://easyyapi.com/documents/call.html)

|            | Support                                                                                                                                                                                                                                                                                   | Extended Support                  |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| language   | java, kotlin                                                                                                                                                                                                                                                                              | scala                             |
| web        | [spring](https://spring.io/), [feign](https://spring.io/projects/spring-cloud-openfeign), [jaxrs](https://www.oracle.com/technical-resources/articles/java/jax-rs.html) ([quarkus](https://quarkus.io/) or [jersey](https://eclipse-ee4j.github.io/jersey/))                              | -                                 |
| channels   | [Postman](https://easyyapi.com/documents/export2postman.html), [Yapi](https://easyyapi.com/documents/export2yapi.html), [Markdown](https://easyyapi.com/documents/export2markdown.html) , [Curl](https://curl.se/) , [HttpClient](https://plugins.jetbrains.com/plugin/13121-http-client) | -                                 |
| frameworks | javax.validation, Jackson, Gson, [swagger](https://swagger.io/)                                                                                                                                                                                                                           | -                                 |

## Navigation

* [Guide](https://easyyapi.com/documents/index.html)
* [Installation](https://easyyapi.com/documents/installation.html)
* [Usage](https://easyyapi.com/documents/use.html)
* [Setting](https://easyyapi.com/setting/index.html)
* [Demo](https://easyyapi.com/demo/index.html)

## Run application

- `./gradlew runIde` will run an IDEA instance with the EasyAPI installed.
- `./gradlew clean test` will run all test cases.

## Requirements

- IDE: IntelliJ IDEA Ultimate / IntelliJ IDEA Community 2025.2 or higher
- JDK: Version 17 or higher

## Compatibility

| JDK | IDE      | status |
|-----|----------|--------|
| 17  | 2025.2.1 | ✓      |

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)
- [baike](https://baidu.com/item/javadoc)

## KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

## Contributing

You can propose a feature request opening an issue or a pull request.

Here is a list of contributors:

<a href="https://github.com/tangcent/easy-api/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-yapi" />
</a>
