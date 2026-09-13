# EasyYapi

[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/tangcent/easy-yapi)

[English](README.md) | 中文

> **注意：** 这是 EasyYapi 的 v3.0 重写版本。如需获取稳定版 v2.x 的源代码，请访问
> [`stable/v2.x.x`](https://github.com/tangcent/easy-yapi/tree/stable/v2.x.x) 分支。

一个用于 API 开发的 IntelliJ IDEA 插件 —— 将 API 文档导出至 YApi/Postman/Markdown 等目标，发送请求，直接在代码中管理接口。

---

## 关于本文档

**本文只提供中文的快速开始与入口，不重复英文版的完整功能面。**

导出的全部渠道（含 Hoppscotch / OpenAPI）、字段转换的全部格式（含 YAML）、支持的框架（含 Custom）、配置优先级、IDE 兼容性、架构与项目结构，以及每个扩展点的分步指南，都以
[README.md](README.md) 与 [docs/](docs/) 为唯一来源 —— 避免两份翻译长期漂移。

## 快速开始

### 安装

- **Marketplace** —— `Settings → Plugins → Marketplace`，搜索 **EasyYapi** 安装
- **离线包** —— 从 [GitHub Releases](https://github.com/tangcent/easy-yapi/releases) 下载 zip，`Settings → Plugins → ⚙ → Install Plugin from Disk…`

需要 IntelliJ IDEA **2025.2+** 与 JDK 17+。旧版 IDE（2022.1–2025.1）的兼容构建、以及自行打包的
`script/package.sh` 用法见 [README.md §Compatibility](README.md#compatibility)。

### 导出 API

1. 在编辑器或项目视图中右键点击控制器文件、类或方法
2. 选择 **EasyApi → Export**（或按 `Alt+Shift+E`；macOS 为 `Ctrl+E`）
3. 选择目标格式，API 即自动导出

可选的导出渠道与各自的配置说明见 [README.md §API Export](README.md#api-export)。

### 调用 API

1. 右键点击控制器方法 → **EasyApi → Call**（或按 `Alt+Shift+C`；macOS 为 `Ctrl+C`）
2. API 仪表盘会打开并加载该接口
3. 编辑参数 / 请求头 / 请求体后发送，响应带语法高亮

### 打开 API 仪表盘

**Tools → Open API Dashboard**，或点击 IDE 底部的 **API Dashboard** 标签页。

### 搜索 API

双击 `Shift` 打开 Search Everywhere，切到 **APIs** 标签页，输入 HTTP 方法前缀（如 `GET /users`）或任意关键词。

### 转换字段

右键点击类 → **EasyApi → To…**（JSON / JSON5 / Properties / YAML 等）。各格式的语义与取舍见
[README.md §Field Conversion](README.md#field-conversion)。

## 编写规则

EasyApi 开箱即用即可理解标准 HTTP 框架（Spring MVC、WebFlux、JAX-RS、Feign）——
**绝大多数项目无需自定义规则**。只有当项目里有扫描器无法感知的约定时（例如某个
`jakarta.servlet.Filter` 要求一个请求头，或某个 `ResponseBodyAdvice` 把响应包成统一信封），
才需要写规则。

- **规则编写指南** —— [`docs/knowledge-base/rule-guide.md`](docs/knowledge-base/rule-guide.md)（规则文件格式、过滤器语法、值格式、配方目录）；插件内 **Help** 按钮打开的是同一份内容
- **AI 辅助** —— 内置助手（`Settings → EasyApi → Rules → Chat / Magic`），或给外部 AI 编程助手的 skill：

```bash
npx skills add tangcent/easy-yapi -g -y
```

两者的分工与细节见 [README.md §Skills](README.md#skills)。

## 参与开发

```bash
./gradlew runIde            # 启动装了插件的 IDEA 实例
./gradlew clean test        # 跑全部测试
```

开发规范（线程模型、日志通道、包布局、测试规约）的**唯一来源**是
[`AGENTS.md`](AGENTS.md)；贡献流程见 [`CONTRIBUTING.md`](CONTRIBUTING.md)；
新增 channel / format / framework 以及 AI 子系统的分步指南见 [`docs/developer/`](docs/developer/README.md)。

## 更多文档

- [指南站点](https://easyyapi.github.io/guide/) —— 概述、安装、使用、各渠道导出
- [README.md](README.md) —— 完整功能面、配置、兼容性、架构与项目结构（英文）
- [docs/developer/](docs/developer/README.md) —— 扩展点 SPI 与分步指南
- [CHANGELOG.md](CHANGELOG.md) —— 版本变更
