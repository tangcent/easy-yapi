# easy-api

[![Build Status](https://travis-ci.com/tangcent/easy-api.svg?branch=master)](https://travis-ci.com/tangcent/easy-api)
[![](https://img.shields.io/jetbrains/plugin/v/12211?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12211)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-api.svg)](http://isitmaintained.com/project/tangcent/easy-api "Percentage of issues still open")
[![Gitter](https://badges.gitter.im/Earth-1610/easy-api.svg)](https://gitter.im/Earth-1610/easy-api?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

- Simplifies API Development
- Parsing based on [javadoc](#Javadoc)&[KDoc](#KDoc)
- [QA](https://github.com/tangcent/easy-api/blob/master/docs/QA.md)

# Table of Contents

* 1 [Feature](#Feature)
* 2 Doc
  * 2.1 [Javadoc](#Javadoc)
  * 2.2 [KDoc](#KDoc)
* 3 [Installation](#Installation)
* 4 [Guide](#Guide)
* 5 [Support](#Support)
* 6 [Changelog](https://github.com/tangcent/easy-api/blob/feature/doc/IDEA_CHANGELOG.md)
* 7 [Docs](https://github.com/tangcent/easy-api/tree/master/docs)
  * 7.1 [Config](https://github.com/tangcent/easy-api/blob/master/docs/1.%20Config.md)
  * 7.2 [Supported-custom-rules](https://github.com/tangcent/easy-api/blob/master/docs/2.%20Supported-custom-rules.md)
  * 7.3 [Support-local-groovy-extension](https://github.com/tangcent/easy-api/blob/master/docs/3.%20Support-local-groovy-extension.md)


## Feature

- [X] Support Java&Kotlin
- [X] Support Spring
- [X] Export api to Postman
- [ ] Export api to Word
- [X] Export api to Markdown
- [X] Export method doc(rpc) to Markdown
- [X] Call api from code
- [X] Api DashBoard

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)

## KDoc

- [kotlin-doc](https://kotlinlang.org/docs/reference/kotlin-doc.html)

## Version

`$major.$minor.$min_support.$max_support.$fix`

Installation
----

**support following product build version > 173(2017.3)**

- IntelliJ IDEA
- IntelliJ IDEA Community Edition

**using IDE plugin system**
- <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>find"EasyApi"</kbd> > <kbd>Install Plugin</kbd>

**Manual:**
- download from [Jetbrains](https://plugins.jetbrains.com/plugin/12211-easyapi/versions) or [releases page](https://github.com/tangcent/easy-api/releases)  -> <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

restart **IDE**.


## Guide

* [custom config for project](https://github.com/tangcent/easy-api/wiki/Use-Config-Make-Plugin-More-Intelligent(Export-Spring-Api-To-Postman))

* ExportApi(0.8.2+)
```textCode
    1. Open existed Spring Controller File Or Select files or directories from project navigation
    You can use by this : "alt shift E(windows)/ctrl E(mac)"
    2.select apis and channel
    3.click [✔️] button or press enter key
```

* ExportPostman
```textCode
    There are two ways to export api.
    1. Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "Alt + Insert" , then
    choose the action "ExportPostman"
    2. Select files or directories from project navigation
    You can use by this : "Click [Code -> ExportPostman] in top"
```

* How to export to postman automatically?

```text
    Click [Preference -> Other Setting -> EasyApi]
    set postman privatetoken
    If you do not have a privateToken of postman,
    you can easily generate one by heading over to the Postman Integrations Dashboard
    [https://go.postman.co/integrations/services/pm_pro_api]
```

* Quick API requests from code

```textCode
    Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "Alt + Insert" , then
    choose the action "Call"
```



* ApiDashBoard
```textCode
    It is easily to export api in current project to postman by dragging
    You can use by this : "Click [Code -> ApiDashBoard] in top"
```

* ExportMarkdown(Beta)
```textCode
    There are two ways to export api.
    1. Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "Alt + Insert" , then
    choose the action "ExportMarkdown"
    2. Select files or directories from project navigation
    You can use by this : "Click [Code -> ExportMarkdown] in top"
```

### Support 

| doc type  |  Postman  |  Markdown  |
| ------------ | ------------ | ------------ |
| spring api | ![yes](assets/yes.png) | ![yes](assets/yes.png) |
| method doc(rpc) | ![yes](assets/no.png) | ![yes](assets/yes.png) |
