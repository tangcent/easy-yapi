# easy-yapi

[![Build Status](https://travis-ci.com/tangcent/easy-yapi.svg?branch=master)](https://travis-ci.com/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/tangcent/easy-yapi.svg)](http://isitmaintained.com/project/tangcent/easy-yapi "Percentage of issues still open")

- [yapi](https://github.com/YMFE/yapi)定制版[easy-api](https://github.com/tangcent/easy-api)

- [中文](https://github.com/tangcent/easy-yapi/blob/master/README_cn.md) | [English](https://github.com/tangcent/easy-yapi/blob/master/README.md)

- [demo](https://github.com/Earth-1610/spring-demo)

- 在[easy-api](https://github.com/tangcent/easy-api)的基础上增加对yapi的支持

  如果你对easy-api有兴趣或者希望支持其他第三方API管理平台,那么这也是一个很好的例子，通过对比[easy-api](https://github.com/tangcent/easy-api)与[easy-yapi](https://github.com/tangcent/easy-aypi)的差异，可以了解到如何在
  [easy-api](https://github.com/tangcent/easy-api)的基础上开发，使其得以支持第三方API管理平台

## Javadoc

- [wiki](https://en.wikipedia.org/wiki/Javadoc)
- [oracle](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html)
- [baike](https://baike.baidu.com/item/javadoc)


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

- 详细信息参考: [docs](https://github.com/tangcent/easy-yapi/blob/master/docs/cn/Home.md)

- [rpc导出](https://github.com/tangcent/easy-yapi/blob/master/docs/cn/6.%20%E6%94%AF%E6%8C%81rpc%E6%8E%A5%E5%8F%A3%E5%AF%BC%E5%87%BA.md)

* 导出API到Yapi
```textCode
    有四种方法.
    1. (0.8.2.1+)打开spring项目中的Controller文件或者在IDEA的左边项目文件区域选择文件或者文件夹
    使用快捷键"alt shift E(windows)/ctrl E(mac)"
    然后选择要导出的API,选择导出渠道为yapi
    点击[✔]按钮或者按回车键完成导出
    2. 打开spring项目中的Controller文件
    右键文件内容选择Generate...或者用"[Alt + Insert]/[Ctrl+Enter](快捷键可能不一样)"
    然后选择"ExportYapi"
    3. 在IDEA的左边项目文件区域选择文件或者文件夹
    鼠标点击最上方[Code -> ExportYapi]
    4. 鼠标点击最上方[Code -> YapiDashBoard]
    然后就可以用鼠标将左边的API拖动到右边yapi目录中
```
