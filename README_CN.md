# easy-yapi

[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Percentage of issues still open")

[English](README.md) | 中文

## 功能特点

- [导出API文档](https://easyyapi.com/documents/use.html)
- [发送API请求](http://easyyapi.com/documents/call.html)

|            | 支持                                                                                                                                                                                                                                                                                      | 扩展支持                          |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| 语言       | java, kotlin                                                                                                                                                                                                                                                                              | scala                             |
| Web框架    | [spring](https://spring.io/), [feign](https://spring.io/projects/spring-cloud-openfeign), [jaxrs](https://www.oracle.com/technical-resources/articles/java/jax-rs.html) ([quarkus](https://quarkus.io/) 或 [jersey](https://eclipse-ee4j.github.io/jersey/))                              | [dubbo](https://dubbo.apache.org) |
| 导出渠道   | [Postman](https://easyyapi.com/documents/export2postman.html), [Yapi](https://easyyapi.com/documents/export2yapi.html), [Markdown](https://easyyapi.com/documents/export2markdown.html) , [Curl](https://curl.se/) , [HttpClient](https://plugins.jetbrains.com/plugin/13121-http-client) | -                                 |
| 支持的框架 | javax.validation, Jackson, Gson                                                                                                                                                                                                                                                           | [swagger](https://swagger.io/)    |

## AI增强 (Beta)

EasyYAPI可以通过接入AI生成更好API文档：

### 特性

- **API翻译**：导出API文档时, 自动将API文档翻译成指定语言。
- **方法返回类型推断**：使用AI分析方法代码并更准确地推断复杂返回类型，提高 API 文档的准确性。

### 配置

- **支持多种AI提供商**：可以配置AI提供商（OpenAI, DeepSeek 等）和模型（GPT-4, DeepSeek-V3 等）。
- **API响应缓存**：通过缓存相同请求的AI响应来优化性能。

要启用这些功能，请在IDEA中配置AI提供商。

## 导航

* [指南](https://easyyapi.com/documents/index.html)
* [安装](https://easyyapi.com/documents/installation.html)
* [使用](https://easyyapi.com/documents/use.html)
* [设置](https://easyyapi.com/setting/index.html)
* [示例](https://easyyapi.com/demo/index.html)

## 运行应用

- `./gradlew :idea-plugin:runIde` 将运行一个安装了 EasyYapi 的 IDEA 实例。
- `./gradlew clean test` 将运行所有测试用例。

## 环境要求

- IDE: Intellij Idea Ultimate / Intellij Idea Community 2021.2.1 或更高版本
- JDK: 11 或更高版本

## 兼容性

| JDK | IDE      | 状态 |
|-----|----------|------|
| 11  | 2021.2.1 | ✓    |
| 15  | 2022.2.3 | ✓    |
| 17  | 2023.1.3 | ✓    |

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)
- [百科](https://baike.baidu.com/item/javadoc)

## KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

## 贡献

您可以通过提出 issue 或提交 pull request 来提出功能请求。

以下是贡献者列表：

<a href="https://github.com/tangcent/easy-yapi/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-yapi" />
</a> 