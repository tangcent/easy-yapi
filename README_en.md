# easy-yapi

[![Build Status](https://travis-ci.com/tangcent/easy-yapi.svg?branch=master)](https://travis-ci.com/tangcent/easy-yapi)
[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Percentage of issues still open")
[![Gitter](https://badges.gitter.im/Earth-1610/easy-yapi.svg)](https://gitter.im/Earth-1610/easy-yapi?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

- Parsing based on [javadoc](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)&[KDoc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

- [中文](https://github.com/tangcent/easy-yapi/blob/master/README_cn.md) | [English](https://github.com/tangcent/easy-yapi/blob/master/README.md)

- [demo](https://github.com/Earth-1610/spring-demo)

# Table of Contents

* 1 [Feature](#Feature)
* 2 Doc
  * 2.1 [Javadoc](#Javadoc)
  * 2.2 [KDoc](#KDoc)
* 3 [Installation](#Installation)
* 4 [Guide](#Guide)
* 5 [Support](#Support)
* 6 [Changelog](https://github.com/tangcent/easy-yapi/blob/feature/doc/IDEA_CHANGELOG.md)
* 7 [Docs](https://github.com/tangcent/easy-yapi/tree/master/docs)
  * 7.1 [Config](https://github.com/tangcent/easy-yapi/blob/master/docs/en/1.%20Config.md)
  * 7.2 [Supported-custom-rules](https://github.com/tangcent/easy-yapi/blob/master/docs/en/2.%20Supported-custom-rules.md)
  * 7.3 [Support-local-groovy-extension](https://github.com/tangcent/easy-yapi/blob/master/docs/en/3.%20Support-local-groovy-extension.md)

## Feature
- [X] Support Java&Kotlin
- [X] Support Spring
- [X] Export api to Postman
- [X] Export api to Yapi
- [ ] Export api to Word
- [X] Export api to Markdown
- [X] Export method doc(rpc) to Markdown
- [X] Export method doc(rpc) to Yapi
- [X] Call api from code
- [X] Api DashBoard
- [X] YApi DashBoard


## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)
- [baike](https://baike.baidu.com/item/javadoc)

## KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)


Installation
----

**support following product build version > 173(2017.3)**

- IntelliJ IDEA
- IntelliJ IDEA Community Edition

**using IDE plugin system**
- <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>find"EasyYapi"</kbd> > <kbd>Install Plugin</kbd>

**Manual:**
- download from [Jetbrains](https://plugins.jetbrains.com/plugin/12458-easyyapi) or [Github](https://github.com/tangcent/easy-yapi/releases) -> <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

restart **IDE**.


## Guide

* [docs](https://github.com/tangcent/easy-yapi/blob/master/docs/cn/Home.md)

* ExportApi(0.8.2.1+)
```textCode
    1. Open existed Spring Controller File Or Select files or directories from project navigation
    You can use by this : "alt shift E(windows)/ctrl E(mac)"
    2. Select apis and channel
    3. Click [✔️] button or press enter key
```

* ExportYapi
```textCode
    There are two ways to export api.
    1. Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "[Alt + Insert]/[Ctrl+Enter]" , then
    choose the action "ExportYapi"
    2. Select files or directories from project navigation
    You can use by this : "Click [Code -> ExportYapi] in top"
```

* ExportPostman
```textCode
    There are two ways to export api.
    1. Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "[Alt + Insert]/[Ctrl+Enter]" , then
    choose the action "ExportPostman"
    2. Select files or directories from project navigation
    You can use by this : "Click [Code -> ExportPostman] in top"
```

* How to export to postman automatically?

```text
    Click [Preference -> Other Setting -> EasyApi]
    set postman privateToken
    If you do not have a privateToken of postman,
    you can easily generate one by heading over to the Postman Integrations Dashboard
    [https://go.postman.co/integrations/services/pm_pro_api]
```

* Quick API requests from code

```textCode
    Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "[Alt + Insert]/[Ctrl+Enter]" , then
    choose the action "Call"
```

* ApiDashBoard
```textCode
    It is easily to export api in current project to postman by dragging
    You can use by this : "Click [Code -> ApiDashBoard] in top"
```

* YApiDashBoard
```textCode
    It is easily to export api in current project to yapi by dragging
    You can use by this : "Click [Code -> YApiDashBoard] in top"
```

* ExportMarkdown(Beta)
```textCode
    There are two ways to export api.
    1. Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "[Alt + Insert]/[Ctrl+Enter]" , then
    choose the action "ExportMarkdown"
    2. Select files or directories from project navigation
    You can use by this : "Click [Code -> ExportMarkdown] in top"
```


### Support: 

| Doc Type  |  Postman  |  Markdown  |  Yapi  |
| ------------ | ------------ | ------------ | ------------ |
| spring api | ![yes](assets/yes.png) | ![yes](assets/yes.png) | ![yes](assets/yes.png) |
| method doc(rpc) | ![yes](assets/no.png) | ![yes](assets/yes.png) |  ![yes](assets/yes.png) |
