* 2.1.8

	* fix: use `asKV` instead of `as KV<>` [(#364)](https://github.com/tangcent/easy-yapi/pull/364)

* 2.1.0~

    * fix: always trim the name of folder [(#314)](https://github.com/tangcent/easy-yapi/pull/314)
    
    * support param.required for methodDoc [(#315)](https://github.com/tangcent/easy-yapi/pull/315)
    
    * opti: support `setter` for `toJson(5)` [(#318)](https://github.com/tangcent/easy-yapi/pull/318)
    
    * opti: use raw as body and use unbox for query/form [(#320)](https://github.com/tangcent/easy-yapi/pull/320)

    * fix: resolve on-demand import [(#84)](https://github.com/Earth-1610/intellij-kotlin/pull/84)
    
    * opti: import default packages for `kotlin`&`scala` [(#85)](https://github.com/Earth-1610/intellij-kotlin/pull/85)
    
    * opti: log for method infer [(#325)](https://github.com/tangcent/easy-yapi/pull/325)
    
    * opti: support spring.ui by recommend [(#330)](https://github.com/tangcent/easy-yapi/pull/330)
    
    * opti: use `setPragma` instead of `setBusyTimeout` [(#333)](https://github.com/tangcent/easy-yapi/pull/333)
    
    * opti: refactor ApiDashboard [(#335)](https://github.com/tangcent/easy-yapi/pull/335)

    * opti: support login mode [(#340)](https://github.com/tangcent/easy-yapi/pull/340)
    
    * opti: support built-in config [(#341)](https://github.com/tangcent/easy-yapi/pull/341)

    * opti: `properties.additional` support url [(#345)](https://github.com/tangcent/easy-yapi/pull/345)
    
    * opti: support `param.doc` for export methodDoc [(#347)](https://github.com/tangcent/easy-yapi/pull/347)
    
    * fix:change the action name from Debug to ScriptExecutor [(#348)](https://github.com/tangcent/easy-yapi/pull/348)
    
    * opti: support `url.cache.expire` [(#349)](https://github.com/tangcent/easy-yapi/pull/349)
    
    * opti: add recommend third config [(#351)](https://github.com/tangcent/easy-yapi/pull/351)
    
    * opti: show default built-in config in setting [(#353)](https://github.com/tangcent/easy-yapi/pull/353)
    
    * fix: bind `settings.builtInConfig` as nullable [(#358)](https://github.com/tangcent/easy-yapi/pull/358)
    
* 2.0.0~

    * feat: support rule util `session` [(#273)](https://github.com/tangcent/easy-yapi/pull/273)
    
    * feat: support new method `annValue` for rule elements [(#274)](https://github.com/tangcent/easy-yapi/pull/274)
    
    * opti: support rule `param.before`&`param.after` [(#275)](https://github.com/tangcent/easy-yapi/pull/275)
    
    * opti: several recommended configs is not selected by default any longer [(#276)](https://github.com/tangcent/easy-yapi/pull/276)
    
    * feat: new recommend configs [(#277)](https://github.com/tangcent/easy-yapi/pull/277)
    
    * opti: support repeat validation annotation [(#278)](https://github.com/tangcent/easy-yapi/pull/278)

    * feat: new rules `class.postman.prerequest`&`class.postman.test` [(#312)](https://github.com/tangcent/easy-api/pull/312)
    
    * feat: new rule `collection.postman.prerequest`&`collection.postman.test` [(#314)](https://github.com/tangcent/easy-api/pull/314)
    
    * feat: new Setting [postman] wrapCollection & autoMergeScript [(#317)](https://github.com/tangcent/easy-api/pull/317)
    
    * opti: parse param as query by default [(#229)](https://github.com/tangcent/easy-yapi/pull/229)
    
    * feat: [ScriptExecutor] support select field or method in the class. [(#231)](https://github.com/tangcent/easy-yapi/pull/231)
    
    * feat: add rule alias `param.doc`/`method.doc`/`class.doc` [(#232)](https://github.com/tangcent/easy-yapi/pull/232)
    
    * chore: fix recommend config for ignore serialVersionUID [(#237)](https://github.com/tangcent/easy-yapi/pull/237)
    
    * chore: remove cache of recommend config [(#238)](https://github.com/tangcent/easy-yapi/pull/238)
    
    * chore: update recommend config [(#239)](https://github.com/tangcent/easy-yapi/pull/239)
    
    * feat: support DeferredResult by recommend [(#250)](https://github.com/tangcent/easy-yapi/pull/250)
    
    * feat: new recommend config \[support_enum_common] [(#251)](https://github.com/tangcent/easy-yapi/pull/251)
    
    * fix: fix class/type #isExtend [(#253)](https://github.com/tangcent/easy-yapi/pull/253)
    
    * fix: always use json settings. [(#261)](https://github.com/tangcent/easy-yapi/pull/261)
    
    * opti: new func: tool.traversal [(#265)](https://github.com/tangcent/easy-yapi/pull/265)
    
    * opti: support rule `field.default.value` [(#266)](https://github.com/tangcent/easy-yapi/pull/266)
    
    * fix: remove usage of Module.getModuleFilePath [(#267)](https://github.com/tangcent/easy-yapi/pull/267)
    
    * feat: support json5 for postman [(#286)](https://github.com/tangcent/easy-yapi/pull/286)
    
    * opti: support rule `class.is.ctrl` [(#293)](https://github.com/tangcent/easy-yapi/pull/293)
    
    * opti: remove log in recommend config [(#294)](https://github.com/tangcent/easy-yapi/pull/294)
    
    * feat: new action `ToJson5` [(#300)](https://github.com/tangcent/easy-yapi/pull/300)
    
    * opti: support org.springframework.lang.NonNull by recommend [(#302)](https://github.com/tangcent/easy-yapi/pull/302)
    
    * opti: ignore org.springframework.validation.BindingResult by recommend [(#303)](https://github.com/tangcent/easy-yapi/pull/303)
    
    * opti: support param.before&param.after for methodDoc [(#307)](https://github.com/tangcent/easy-yapi/pull/307)
    
    * opti: preview recommendConfig with separator line [(#309)](https://github.com/tangcent/easy-yapi/pull/309)
    
* 1.9.0 ~

    * fix: support `java`/`kt`/`scala` in all action. [(#271)](https://github.com/tangcent/easy-api/pull/271
    
    * support new method 'method/declaration' of 'arg' [(#273)](https://github.com/tangcent/easy-api/pull/273)
    
    * opti: support rule `folder.name` [(#274)](https://github.com/tangcent/easy-api/pull/274
    
    * support new rule `path.multi` [(#275)](https://github.com/tangcent/easy-api/pull/275)
    
    * fix: request body preview-language in postman example [(#281)](https://github.com/tangcent/easy-api/pull/281)
    
    * feat: support new rule `api.open` [(#179)](https://github.com/tangcent/easy-yapi/pull/179)
    
    * feat: support switch notice at `Setting` [(#180)](https://github.com/tangcent/easy-yapi/pull/180)
                                                    
    * opti: support new rule `postman.prerequest`&`postman.test` [(#283)](https://github.com/tangcent/easy-api/pull/283)
    
    * opti: support new rule tool `config` [(#284)](https://github.com/tangcent/easy-api/pull/284)
    
    * fix: preserving the order of field in `YapiFormatter` [(#189)](https://github.com/tangcent/easy-yapi/pull/189)
    
    * opti: input new token if the old one be deleted. [(#192)](https://github.com/tangcent/easy-yapi/pull/192)
                                                          
    * feat: support new rule `export.after` [(#287)](https://github.com/tangcent/easy-api/pull/287)
   
    * feat: new tool `files` [(#289)](https://github.com/tangcent/easy-api/pull/289)
    
    * feat: `Debug` enhancement [(#290)](https://github.com/tangcent/easy-api/pull/290)
    
    * feat: support new rule `method.content.type` [(#292)](https://github.com/tangcent/easy-api/pull/292)
   
    * feat: support rule `param.http.type` for `RequestParam ` [(#298)](https://github.com/tangcent/easy-api/pull/298)
    
    * feat: handle annotation `CookieValue` [(#300)](https://github.com/tangcent/easy-api/pull/300)
     
    * feat: Support Postman export "Path Variables"  [(#213)](https://github.com/tangcent/easy-yapi/pull/213)
    
    * opti: resolve relative path. [(#53)](https://github.com/Earth-1610/intellij-kotlin/pull/53)
    
    * fix: fix required for query with GET. [(#305)](https://github.com/tangcent/easy-api/pull/305)
    
    * fix: keep parameter info to query or form. [(#307)](https://github.com/tangcent/easy-api/pull/307)
    
    * chore: rename Action `Debug` -> `ScriptExecutor`. [(#308)](https://github.com/tangcent/easy-api/pull/308)
    
    * feat: support property `api.tag.delimiter` [(#223)](https://github.com/tangcent/easy-yapi/pull/223)
    
* 1.8.0 ~

    * enhance:support render yapi desc: [yapi render](http://easyyapi.com/documents/yapi_render.html)  [(#138)](https://github.com/tangcent/easy-api/pull/138)

    * fix type parse for markdown formatter [(#255)](https://github.com/tangcent/easy-api/pull/255)
    
    * addHeaderIfMissed only if the request hasBody  [(#258)](https://github.com/tangcent/easy-api/pull/258)
   
    * fix name of api without any comment  [(#263)](https://github.com/tangcent/easy-api/pull/263)
   
    * recommend config: private_protected_field_only  [(#256)](https://github.com/tangcent/easy-api/pull/256)
   
    * refactor http client [(#257)](https://github.com/tangcent/easy-api/pull/257)
   
    * resolve RequestMapping#params [(#259)](https://github.com/tangcent/easy-api/pull/259)
   
    * resolve RequestMapping#headers [(#260)](https://github.com/tangcent/easy-api/pull/260)
    
    * log saved file path [(#264)](https://github.com/tangcent/easy-api/pull/264)
    
    * opti: [DEBUG ACTION] [(#265)](https://github.com/tangcent/easy-api/pull/265)
    
    * fix HttpRequest querys [(#267)](https://github.com/tangcent/easy-api/pull/267)
    
    * new rule tool: localStorage [(#268)](https://github.com/tangcent/easy-api/pull/268)

* 1.7.0 ~

    * enhance:new rule tool: helper  [(#242)](https://github.com/tangcent/easy-api/pull/242)
    
    * enhance:support rule: method.return  [(#240)](https://github.com/tangcent/easy-api/pull/240)

* 1.5.0 ~

    * enhance:support setting charset for export markdown  [(#211)](https://github.com/tangcent/easy-api/pull/211)
    * enhance:add new method `jsonType` for `method`&`field`  [(#213)](https://github.com/tangcent/easy-api/pull/213)
    * enhance:support scala project   [(#214)](https://github.com/tangcent/easy-api/pull/214)
    * bug-fix: preserving the order of field in infer   [(#216)](https://github.com/tangcent/easy-api/pull/216)

* 1.4.0 ~
    * enhance:support new rule: `api.name`  [(#200)](https://github.com/tangcent/easy-api/pull/200)
    * enhance:new method `contextType` for rule  [(#201)](https://github.com/tangcent/easy-api/pull/201)
    * enhance:cache parsed additional `Header`/`Param`  [(#205)](https://github.com/tangcent/easy-api/pull/205)
    * enhance:ignore param extend HttpServletRequest/HttpServletResponse  [(#206)](https://github.com/tangcent/easy-api/pull/206)
    * enhance:new rule: `method.default.http.method`   [(#207)](https://github.com/tangcent/easy-api/pull/207)
    * enhance:provide recommend config for yapi mock [(#116)](https://github.com/tangcent/easy-yapi/pull/116)
    * enhance:new rule `field.mock` [(#113)](https://github.com/tangcent/easy-yapi/pull/113)
       
* 1.3.0 ~
    * enhance:new rule:`[class.prefix.path]`  [(#181)](https://github.com/tangcent/easy-api/pull/181)
    * enhance:new rule:`[doc.class]`  [(#178)](https://github.com/tangcent/easy-api/pull/178)
    * enhance:new rule:`[param.ignore]`  [(#176)](https://github.com/tangcent/easy-api/pull/176)
    * enhance:import spring properties by recommend [(#181)](https://github.com/tangcent/easy-api/pull/181)
    * enhance:Auto reload the configuration while context switch [(#185)](https://github.com/tangcent/easy-api/pull/185)
   
* 1.2.0 ~
    * enhance:provide more recommended configurations  [(#153)](https://github.com/tangcent/easy-api/issues/153)
    * enhance:support for export&import settings [(#167)](https://github.com/tangcent/easy-api/issues/167)
    * fix: Some icon maybe missing in Windows  [(#164)](https://github.com/tangcent/easy-api/issues/164)
  
* 1.1.0 ~
    * enhance:support rule: `name[filter]=value`  [(#138)](https://github.com/tangcent/easy-api/pull/138)
    * enhance:parse kotlin files in ApiDashboard  [(#141)](https://github.com/tangcent/easy-api/pull/141)
    * fix: support Serializer for Enum  [(#134)](https://github.com/tangcent/easy-api/issues/134)
    * fix: fix error base path for APIs in super class  [(#137)](https://github.com/tangcent/easy-api/issues/137)
    * fix: ApiDashboard not show kotlin module&apis [(#140)](https://github.com/tangcent/easy-api/issues/140)
     
* 1.0.0 ~
    * enhance:support kotlin  [(#125)](https://github.com/tangcent/easy-api/pull/125)

* 0.9.0 ~
    * enhance:support groovy extension  [(#98)](https://github.com/tangcent/easy-api/pull/98)
    * enhance:update toolTip of ApiProjectNode in ApiDashBoard  [(#102)](https://github.com/tangcent/easy-api/pull/102)
    * fix:opti method Infer  [(#103)](https://github.com/tangcent/easy-api/pull/103)
    * enhance:support export method doc(rpc)  [(#107)](https://github.com/tangcent/easy-api/pull/107)
    * fix config search[(#113)](https://github.com/tangcent/easy-api/pull/113)
    * resolve `{@link ...}` in param desc doc[(#117)](https://github.com/tangcent/easy-api/pull/117)
    * Output path params in 'Export Markdown'[(#118)](https://github.com/tangcent/easy-api/pull/118)
    * fix:use json instead of form for add cart of yapi [(#44)](https://github.com/tangcent/easy-yapi/pull/44)

* 0.8.0 ~
    * enhance:process key 'Tab' in request params  [(#85)](https://github.com/tangcent/easy-api/pull/85)
    * enhance:process Deprecated info on class in RecommendConfig  [(#86)](https://github.com/tangcent/easy-api/pull/86)
    * enhance:try parse linked option info for form params  [(#87)](https://github.com/tangcent/easy-api/pull/87)

* 0.7.0 ~
    * enhance:provide logging level Settings  [(#68)](https://github.com/tangcent/easy-api/issues/68)
    * enhance:optimized action interrupt  [(#72)](https://github.com/tangcent/easy-api/pull/72)
    * fix:support org.springframework.http.HttpEntity/org.springframework.http.ResponseEntity  [(#71)](https://github.com/tangcent/easy-api/issues/71)
          
* 0.6.0 ~
    * enhance:support ApiDashboard
    * enhance:optimized ui
    * enhance:auto fix postman collection info
    * enhance:support PopupMenu for Postman Tree [(#42)](https://github.com/tangcent/easy-api/issues/42)
    * enhance:support clear cache in Setting [(#46)](https://github.com/tangcent/easy-api/issues/46)
    * enhance:support generic type of api method[(#48)](https://github.com/tangcent/easy-api/issues/48)
    * enhance:optional form parameters[(#53)](https://github.com/tangcent/easy-api/issues/53)
    * fix:deserialize int numbers correctly [(#49)](https://github.com/tangcent/easy-api/issues/49)
    * fix:fix custom module rule in config [(#54)](https://github.com/tangcent/easy-api/issues/54)
    * fix:support org.springframework.web.bind.annotation.RequestHeader [(#57)](https://github.com/tangcent/easy-api/issues/57)
    * enhance:optimize the inference of the return type of the method [(#60)](https://github.com/tangcent/easy-api/issues/60)
    * enhance:provide http properties settings [(#61)](https://github.com/tangcent/easy-api/issues/61)
    * enhance:set toolTip for postman tree node [(#64)](https://github.com/tangcent/easy-api/pull/64)
    * enhance:support recommend config [(#66)](https://github.com/tangcent/easy-api/pull/66)
    * enhance:support class rule:ignoreField\[json.rule.field.ignore] [(#67)](https://github.com/tangcent/easy-api/pull/67)
    * enhance:support YapiDashBoard [(#66)](https://github.com/tangcent/easy-yapi/issues/5)
    * enhance:try resolve link to yapi [#2](https://github.com/tangcent/easy-yapi/issues/2)

* 0.5.0 ~
    *  fix:auto format xml/html response
    *  fix:set prompt for json response
    *  fix:optimized the cache
    
* 0.4.0 ~
    *  enhance:quick API requests from code`[Alt + Insert -> Call]`
    *  enhance:support request&response header
    *  enhance:support download response
    *  enhance:support host history
    *  enhance:support response auto format
    *  (beta)enhance:Export Api As Markdown\[Code -> ExportMarkdown]
    *  fix:support Post File In `[Call Api Action]`
    
* 0.3.0
    *  enhance:cache api export result
    
* 0.2.0
    *  enhance:support export api to postman`[Code -> ExportPostman]`
