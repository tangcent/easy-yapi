# EasyApi

[![CI](https://github.com/tangcent/easy-api/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-api/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-api/branch/master/graph/badge.svg?token=4DPGLAWL3Q)](https://codecov.io/gh/tangcent/easy-api)
[![](https://img.shields.io/jetbrains/plugin/v/12211?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12211)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Percentage of issues still open")


> **Note:** This is the v3.0 rewrite of EasyApi. For the source code of stable v2.x releases, see the [`stable/v2.x.x`](https://github.com/tangcent/easy-api/tree/stable/v2.x.x) branch.

## Feature

- Export API Documents
- Send API requests

|            | Support                                                                                                                                                                                                                                                                                   | Extended Support                  |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| language   | java, kotlin                                                                                                                                                                                                                                                                              | scala                             |
| web        | [spring](https://spring.io/), [feign](https://spring.io/projects/spring-cloud-openfeign), [jaxrs](https://www.oracle.com/technical-resources/articles/java/jax-rs.html) ([quarkus](https://quarkus.io/) or [jersey](https://eclipse-ee4j.github.io/jersey/))                              | -                                 |
| channels   | Postman, Markdown, [Curl](https://curl.se/), [HttpClient](https://plugins.jetbrains.com/plugin/13121-http-client)                                                              | -                                 |
| frameworks | javax.validation, Jackson, Gson, [swagger](https://swagger.io/)                                                                                                                                                                                                                           | -                                 |

## Run application

- `./gradlew runIde` will run an IDEA instance with the EasyApi installed.
- `./gradlew clean test` will run all test cases.

## Requirements

- IDE: IntelliJ IDEA Ultimate / IntelliJ IDEA Community 2025.2 or higher
- JDK: Version 17 or higher

## Compatibility

| JDK | IDE      | status |
|-----|----------|--------|
| 17  | 2025.2.1 | ✓      |

## Contributing

You can propose a feature request opening an issue or a pull request.

Here is a list of contributors:

<a href="https://github.com/tangcent/easy-api/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-api" />
</a>
