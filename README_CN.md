# EasyAPI

[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)

[English](README.md) | 中文

> **注意：** 这是 EasyAPI 的 v3.0 重写版本。如需获取稳定版 v2.x 的源代码，请访问 [`stable/v2.x.x`](https://github.com/tangcent/easy-yapi/tree/stable/v2.x.x) 分支。

## 功能特性

- [导出 API 文档](https://easyyapi.com/documents/use.html)
- [发送 API 请求](http://easyyapi.com/documents/call.html)

|      | 支持                                                                                                                                                                                                                                                                                        | 扩展支持  |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| 语言   | java, kotlin                                                                                                                                                                                                                                                                              | scala |
| web  | [spring](https://spring.io/), [feign](https://spring.io/projects/spring-cloud-openfeign), [jaxrs](https://www.oracle.com/technical-resources/articles/java/jax-rs.html) ([quarkus](https://quarkus.io/) 或 [jersey](https://eclipse-ee4j.github.io/jersey/))                               | -     |
| 导出渠道 | [Postman](https://easyyapi.com/documents/export2postman.html), [Yapi](https://easyyapi.com/documents/export2yapi.html), [Markdown](https://easyyapi.com/documents/export2markdown.html) , [Curl](https://curl.se/) , [HttpClient](https://plugins.jetbrains.com/plugin/13121-http-client) | -     |
| 框架   | javax.validation, Jackson, Gson, [swagger](https://swagger.io/)                                                                                                                                                                                                                           | -     |

## 导航

* [指南](https://easyyapi.com/documents/index.html)
* [安装](https://easyyapi.com/documents/installation.html)
* [使用](https://easyyapi.com/documents/use.html)
* [设置](https://easyyapi.com/setting/index.html)
* [演示](https://easyyapi.com/demo/index.html)

## 开发

### 运行应用

- `./gradlew runIde` 将运行安装了 EasyAPI 的 IDEA 实例。
- `./gradlew clean test` 将运行所有测试用例。

### 要求

- IDE：IntelliJ IDEA Ultimate / IntelliJ IDEA Community 2023.1 或更高版本
- JDK：版本 17 或更高
- Kotlin：2.1.0

### 兼容性

| JDK | IDE      | 状态 |
|-----|----------|----|
| 17  | 2023.1.3 | ✓  |

## 文档

### Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)

### KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

## 贡献

您可以通过提交 issue 或 pull request 来提出功能请求。

贡献者列表：

<a href="https://github.com/tangcent/easy-api/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-yapi" />
</a>

## 许可证

本项目采用 GNU Affero General Public License v3.0 许可 - 详见 [LICENSE](LICENSE) 文件。
