# easy-yapi
- [yapi](https://github.com/YMFE/yapi)定制版[easy-api](https://github.com/tangcent/easy-api)

- [中文](https://github.com/tangcent/easy-yapi/blob/master/README_ZN.md) | [English](https://github.com/tangcent/easy-yapi/blob/master/README.md)

- [demo](https://github.com/tangcent/spring-demo)

- 在[easy-api](https://github.com/tangcent/easy-api)的基础上增加对yapi的支持

  如果你对easy-api有兴趣或者希望支持其他第三方API管理平台,那么这也是一个很好的例子，通过对比[easy-api](https://github.com/tangcent/easy-api)与[easy-yapi](https://github.com/tangcent/easy-aypi)的差异，可以了解到如何在
  [easy-api](https://github.com/tangcent/easy-api)的基础上开发，使其得以支持第三方API管理平台


安装方法
----

**支持以下IDE version > 173**

- IntelliJ IDEA
- IntelliJ IDEA Community Edition

**从IDEA中安装**
- <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>find"EasyYapi"</kbd> > <kbd>Install Plugin</kbd>

**手动安装:**
- download from [Jetbrains](https://plugins.jetbrains.com/plugin/12458-easyyapi) or [Github](https://github.com/tangcent/easy-yapi-plugins/raw/master/idea/easy-yapi.jar) -> <kbd>Preferences(Settings)</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

重启 **IDE**.

## 使用方法

- 详细信息参考:[wiki](https://github.com/tangcent/easy-yapi/wiki)

* 导出API到Yapi
```textCode
    有三种方法.
    1. 打开spring项目中的Controller文件
    右键文件内容选择Generate...或者用"[Alt + Insert]/[Ctrl+Enter](快捷键可能不一样)"
    然后选择"ExportYapi"
    2. 在IDEA的左边项目文件区域选择文件或者文件夹
    鼠标点击最上方[Code -> ExportYapi]
    3. 鼠标点击最上方[Code -> YapiDashBoard]
    然后就可以用鼠标将左边的API拖动到右边yapi目录中
```
