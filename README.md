# easy-yapi
- Customized [easyApi](https://github.com/tangcent/easy-api) for [yapi](https://github.com/YMFE/yapi)

- [中文](https://github.com/tangcent/easy-yapi/blob/master/README_cn.md) | [English](https://github.com/tangcent/easy-yapi/blob/master/README.md)

- [demo](https://github.com/tangcent/spring-demo)

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)
- [baike](https://baike.baidu.com/item/javadoc)

## Tips
Before the 1.0.0 release, easy-yapi will be quick iteration.
If you encounter a failure
1. Please commit a issue.
2. Try roll back to the previous version.
3. Feel free to email [me](mailto:pentatangcent@gmail.com) at any time.

## Version

`$major.$minor.$min_support.$max_support.$fix`

Installation
----

**support following product build version > 173(2017.3)**

- IntelliJ IDEA
- IntelliJ IDEA Community Edition

**using IDE plugin system**
- <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>find"EasyYapi"</kbd> > <kbd>Install Plugin</kbd>

**Manual:**
- download from [Jetbrains](https://plugins.jetbrains.com/plugin/12458-easyyapi) or [Github](https://github.com/tangcent/easy-yapi-plugins/raw/master/idea/easy-yapi.jar) -> <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

restart **IDE**.


## Guide

* [custom config for project](https://github.com/tangcent/easy-yapi/wiki/%E6%94%AF%E6%8C%81%E9%A2%9D%E5%A4%96%E9%85%8D%E7%BD%AE)

* ExportApi(0.8.2.1+)
```textCode
    1. Open existed Spring Controller File Or Select files or directories from project navigation
    You can use by this : "alt shift E(windows)/ctrl E(mac)"
    2.select apis and channel
    3.click [✔️] button or press enter key
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
    set postman privatetoken
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

## Feature
- [X] Support Spring
- [X] Export api to Postman
- [X] Export api to Yapi
- [ ] Export api to Word
- [X] Export api to Markdown
- [X] Call api from code
- [X] Api DashBoard
- [X] YApi DashBoard
