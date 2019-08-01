# easy-api
Simplifies API Development


Installation
----

**support following product build version > 171(2017.1)**

- IntelliJ IDEA
- IntelliJ IDEA Community Edition

**using IDE plugin system**
- <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>find"EasyApi"</kbd> > <kbd>Install Plugin</kbd>

**Manual:**
- download from [Jetbrains](https://plugins.jetbrains.com/plugin/12211-easyapi/versions) or [Github](https://github.com/tangcent/easy-api-plugins/raw/master/idea/easy-api.jar) -> <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

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

## Feature
- [X] Support Spring
- [X] Export api to Postman
- [ ] Export api to Word
- [X] Export api to Markdown
- [X] Call api from code
- [X] Api DashBoard
