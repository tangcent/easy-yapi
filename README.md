# easy-api
Simplifies API Development

## Install

- [Download](https://github.com/tangcent/easy-api-plugins/raw/master/idea/easy-api.jar) the plugin jar and select "Install Plugin From Disk" in IntelliJ's plugin preferences.

## Guide

* [custom config for project](https://github.com/tangcent/easy-api/wiki/Use-Config-Make-Plugin-More-Intelligent(Export-Spring-Api-To-Postman))

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
    Click [File -> Other Setting -> EasyApiSetting]
    add new setting
    [host] https://api.getpostman.com
    [token] Get from https://go.postman.co/integrations/services/pm_pro_api
```

* Quick API requests from code

```textCode
    Open existed Spring Controller File
    You can use by this : "Right click in the file -> generate... " or use its shortcuts "Alt + Insert" , then
    choose the action "Call"
```

## Feature
- [X] Support Spring
- [X] Export api to Postman
- [ ] Export api to Excel
- [ ] Export api to Markdown
- [X] Call api from code
