* 2.7.3
	* feat: support Jackson JsonView  [(#1162)](https://github.com/tangcent/easy-yapi/pull/1162)

	* feat: add 'yapi.no_update.description' to stop api description updates  [(#1170)](https://github.com/tangcent/easy-yapi/pull/1170)

	* feat: omit content-type header when no parameters are present  [(#1169)](https://github.com/tangcent/easy-yapi/pull/1169)

	* build: update IntelliJ plugin version from 1.14.2 to 1.17.1  [(#1166)](https://github.com/tangcent/easy-yapi/pull/1166)

	* feat(script): add support for 'mavenId()' in 'class'  [(#1155)](https://github.com/tangcent/easy-yapi/pull/1155)

	* chore: add missing space in log message for setting Postman privateToken  [(#1148)](https://github.com/tangcent/easy-yapi/pull/1148)
* 2.7.2
	* fix: remove Non-extendable interface usage  [(#1146)](https://github.com/tangcent/easy-yapi/pull/1146)

	* chore: polish Postman API helper classes and cache management  [(#1145)](https://github.com/tangcent/easy-yapi/pull/1145)

	* fix: correct content-type for Postman API export  [(#1144)](https://github.com/tangcent/easy-yapi/pull/1144)

	* test: add test case to verify rendering of non-English content in BundledMarkdownRender  [(#1142)](https://github.com/tangcent/easy-yapi/pull/1142)
* 2.7.1
	* fix: resolve 'module_path' property resolution issue  [(#1137)](https://github.com/tangcent/easy-yapi/pull/1137)

	* feat: added functionality to choose between apache and okHttp for sending HTTP requests via settings  [(#1132)](https://github.com/tangcent/easy-yapi/pull/1132)

	* docs: remove Travis CI build status badge from README  [(#1136)](https://github.com/tangcent/easy-yapi/pull/1136)
* 2.7.0
	* feat: add rules `param.name`, `param.type`  [(#1128)](https://github.com/tangcent/easy-yapi/pull/1128)

	* feat: add support for io.swagger.v3.oas.annotations.media.Schema annotation  [(#1127)](https://github.com/tangcent/easy-yapi/pull/1127)

	* fix: fix issue where the configuration was not being loaded before actions were performed  [(#1123)](https://github.com/tangcent/easy-yapi/pull/1123)

	* feat: Replace gson and jsoup with IntelliJ code style for JSON/XML/HTML formatting  [(#1119)](https://github.com/tangcent/easy-yapi/pull/1119)
* 2.6.9
	* fix: implement error handling in remoteConfigContent function  [(#1115)](https://github.com/tangcent/easy-yapi/pull/1115)

	* fix: resolve comment for custom enum field  [(#1112)](https://github.com/tangcent/easy-yapi/pull/1112)

	* fix: resolve configuration loading issue in several actions  [(#1111)](https://github.com/tangcent/easy-yapi/pull/1111)

	* feat: optimize configuration loading logic for enhanced efficiency and reliability  [(#1107)](https://github.com/tangcent/easy-yapi/pull/1107)

* 2.6.8
	* feat: Enhance support for enum fields in 'field'  [(#1083)](https://github.com/tangcent/easy-yapi/pull/1083)

	* amend: refactor ResolveMultiPath enum to encapsulate url selection logic  [(#1079)](https://github.com/tangcent/easy-yapi/pull/1079)

	* amend: Restrict api selection to single item in ApiCall  [(#1078)](https://github.com/tangcent/easy-yapi/pull/1078)
* 2.6.7

	* feat: Add support for exporting APIs as .http files  [(#1076)](https://github.com/tangcent/easy-yapi/pull/1076)

	* fix: Fix auto-collapse for popup items on select channel  [(#1075)](https://github.com/tangcent/easy-yapi/pull/1075)

	* feat: support search in several dialogs  [(#1073)](https://github.com/tangcent/easy-yapi/pull/1073)

	* Refactor: Preventing runtime.channel() from throwing exceptions on missing implementations  [(#1074)](https://github.com/tangcent/easy-yapi/pull/1074)

* 2.6.6
	* feat: Add dialog size memory feature  [(#1069)](https://github.com/tangcent/easy-yapi/pull/1069)

	* feat: Improve Layout and Responsiveness of Several UI Forms  [(#1068)](https://github.com/tangcent/easy-yapi/pull/1068)
* 2.6.5

    * feat: Support configuring doc.source.disable to disable documentation reading  [(#1059)](https://github.com/tangcent/easy-yapi/pull/1059)

* 2.6.4

	* feat: Add recommended configuration for Jackson JsonPropertyOrder  [(#1048)](https://github.com/tangcent/easy-yapi/pull/1048)

	* feat: Add new rule field.order.with  [(#1047)](https://github.com/tangcent/easy-yapi/pull/1047)

	* feat: Apply field rules to getter and setter methods  [(#1044)](https://github.com/tangcent/easy-yapi/pull/1044)

	* chore: polish YapiFormatter  [(#1043)](https://github.com/tangcent/easy-yapi/pull/1043)

* 2.6.3

	* amend: remove CompensateRateLimiter  [(#1034)](https://github.com/tangcent/easy-yapi/pull/1034)

    * feat: add rules `postman.format.after`, `yapi.format.after`  [(#1033)](https://github.com/tangcent/easy-yapi/pull/1033)

    * chore: remove deprecated utils  [(#1024)](https://github.com/tangcent/easy-yapi/pull/1024)

* 2.6.2
	* refactor: remove usage of KV  [(#1020)](https://github.com/tangcent/easy-yapi/pull/1020)

	* fix: fix issue with SessionStorage not works  [(#1016)](https://github.com/tangcent/easy-yapi/pull/1016)

	* fix: fix thread warning  [(#1012)](https://github.com/tangcent/easy-yapi/pull/1012)

	* fix: added support for strict check in jakarta.validation and javax.validation  [(#1013)](https://github.com/tangcent/easy-yapi/pull/1013)
* 2.6.1
	* feat: ignore some common classes  [(#1009)](https://github.com/tangcent/easy-yapi/pull/1009)

	* fix: refined config behavior for mocking enum fields  [(#1008)](https://github.com/tangcent/easy-yapi/pull/1008)
* 2.6.0
	* chore: update pluginDescription  [(#1002)](https://github.com/tangcent/easy-yapi/pull/1002)

	* fix: process correct class when clicking class in multi-class Kotlin file  [(#1000)](https://github.com/tangcent/easy-yapi/pull/1000)

	* fix: fix support for com.fasterxml.jackson.annotation.JsonUnwrapped  [(#999)](https://github.com/tangcent/easy-yapi/pull/999)
* 2.5.9

	* fix: fix swagger3.config  [(#996)](https://github.com/tangcent/easy-yapi/pull/996)

	* doc: declare Requirements & Compatibility  [(#994)](https://github.com/tangcent/easy-yapi/pull/994)

	* build: update workflows name  [(#995)](https://github.com/tangcent/easy-yapi/pull/995)

	* build: update workflows  [(#989)](https://github.com/tangcent/easy-yapi/pull/989)

	* amend: remove support of GroovyActionExt  [(#991)](https://github.com/tangcent/easy-yapi/pull/991)

	* build: update scripts  [(#988)](https://github.com/tangcent/easy-yapi/pull/988)

	* amend: clear uiWeakReference  [(#983)](https://github.com/tangcent/easy-yapi/pull/983)

	* amend: modify several parameters in RequestRuleWrap nullable  [(#984)](https://github.com/tangcent/easy-yapi/pull/984)
* 2.5.8

	* amend: remove deprecated API usages [(#961)](https://github.com/tangcent/easy-yapi/pull/961)

* 2.5.7

	* feat: output field.demo value to postman/markdown [(#958)](https://github.com/tangcent/easy-yapi/pull/958)

	* chore: update version of intellij-kotlin to 1.5.0 [(#957)](https://github.com/tangcent/easy-yapi/pull/957)

	* chore: polish SettingBinder [(#956)](https://github.com/tangcent/easy-yapi/pull/956)

	* feat: read yapi token from config [(#955)](https://github.com/tangcent/easy-yapi/pull/955)

	* fix: set _id back after save api to yapi success [(#952)](https://github.com/tangcent/easy-yapi/pull/952)

* 2.5.6

	* feat: add several icons to resources [(#947)](https://github.com/tangcent/easy-yapi/pull/947)

	* feat: YapiApiHelper.copyApi support copy by module instead of token [(#945)](https://github.com/tangcent/easy-yapi/pull/945)

	* feat: new script method 'runtime.async' [(#943)](https://github.com/tangcent/easy-yapi/pull/943)

	* feat: new method YapiApiHelper.copyApi [(#941)](https://github.com/tangcent/easy-yapi/pull/941)

	* feat: new script methods for 'runtime' [(#940)](https://github.com/tangcent/easy-yapi/pull/940)

	* build: change min support ide version to 212 [(#938)](https://github.com/tangcent/easy-yapi/pull/938)

* 2.5.5

	* build: use Kotlin DSL script build project [(#933)](https://github.com/tangcent/easy-yapi/pull/933)

	* feat: support config ignore_irregular_api_method [(#929)](https://github.com/tangcent/easy-yapi/pull/929)

* 2.5.4

	* feat: provide aliases for several rule tools [(#925)](https://github.com/tangcent/easy-yapi/pull/925)

	* chore: update version of intellij-kotlin to 1.4.5 [(#924)](https://github.com/tangcent/easy-yapi/pull/924)

	* fix: fix api response in postman [(#923)](https://github.com/tangcent/easy-yapi/pull/923)

	* feat: new rule tool: runtime [(#922)](https://github.com/tangcent/easy-yapi/pull/922)

	* feat: support jakarta.validation [(#918)](https://github.com/tangcent/easy-yapi/pull/918)

	* feat: support export apis from groovy [(#912)](https://github.com/tangcent/easy-yapi/pull/912)

* 2.5.3

	* chore: update swagger config [(#904)](https://github.com/tangcent/easy-yapi/pull/904)

	* feat: new script method `class.toObject` [(#903)](https://github.com/tangcent/easy-yapi/pull/903)

	* fix: fix helper.resolveLink(s) [(#900)](https://github.com/tangcent/easy-yapi/pull/900)

* 2.5.2

	* feat: support swagger3 [(#897)](https://github.com/tangcent/easy-yapi/pull/897)

	* feat: refresh button in [EasyApi > Remote] [(#895)](https://github.com/tangcent/easy-yapi/pull/895)

	* fix: unescape api.tag.delimiter [(#894)](https://github.com/tangcent/easy-yapi/pull/894)

	* fix: wrap annotation array parameter in script rule execute [(#893)](https://github.com/tangcent/easy-yapi/pull/893)

* 2.5.1

	* chore: polish ContextualPsiClassHelper [(#889)](https://github.com/tangcent/easy-yapi/pull/889)

	* fix: support ConfigurationProperties("prefix") [(#888)](https://github.com/tangcent/easy-yapi/pull/888)

	* fix: Gson no longer always parse number to double [(#885)](https://github.com/tangcent/easy-yapi/pull/885)

	* chore: remove unnecessary log [(#881)](https://github.com/tangcent/easy-yapi/pull/881)

* 2.5.0

	* fix: check group with `javax.validation.groups.Default` [(#877)](https://github.com/tangcent/easy-yapi/pull/877)

	* chore: update log [(#878)](https://github.com/tangcent/easy-yapi/pull/878)

	* amend: group actions in pop [(#876)](https://github.com/tangcent/easy-yapi/pull/876)

* 2.4.9

	* fix: fix the icons and buttons disappeared [(#869)](https://github.com/tangcent/easy-yapi/pull/869)

	* fix: remove invalid attributes for create postman collections [(#868)](https://github.com/tangcent/easy-yapi/pull/868)

	* fix: open FileChooser at AWT Thread [(#863)](https://github.com/tangcent/easy-yapi/pull/863)

* 2.4.8

	* polish: remove usage of AutoComputer in ApiCallDialog [(#860)](https://github.com/tangcent/easy-yapi/pull/860)

	* fix: YapiFormatter#parseObject resolve attrs as Map [(#857)](https://github.com/tangcent/easy-yapi/pull/857)

	* polish: update icons [(#855)](https://github.com/tangcent/easy-yapi/pull/855)

	* feat: highlight new content in Settings->Recommend [(#853)](https://github.com/tangcent/easy-yapi/pull/853)

	* feat: support remote config setting [(#852)](https://github.com/tangcent/easy-yapi/pull/852)

	* release v2.4.7 [(#849)](https://github.com/tangcent/easy-yapi/pull/849)

* 2.4.7

	* feat: provider json.rule.enum.convert in recommendation [(#848)](https://github.com/tangcent/easy-yapi/pull/848)

	* feat: populate field from mock [(#847)](https://github.com/tangcent/easy-yapi/pull/847)

	* feat: new rule `yapi.save.before` & `yapi.save.after` [(#842)](https://github.com/tangcent/easy-yapi/pull/842)

* 2.4.6

	* amend: use :Log() instead of define [(#837)](https://github.com/tangcent/easy-yapi/pull/837)

	* fix: parse FeignClient#path [(#835)](https://github.com/tangcent/easy-yapi/pull/835)

	* feat: support export apis from actuator [(#834)](https://github.com/tangcent/easy-yapi/pull/834)

	* fix: convert java.math.BigInteger to java.lang.Long [(#828)](https://github.com/tangcent/easy-yapi/pull/828)

	* test: DefaultYapiApiHelperTest [(#824)](https://github.com/tangcent/easy-yapi/pull/824)

* 2.4.5

	* fix: set resolveMulti to first in javax.validation.config [(#821)](https://github.com/tangcent/easy-yapi/pull/821)

	* feat: add an option to update or skip existed apis in yapi [(#817)](https://github.com/tangcent/easy-yapi/pull/817)

	* feat: asks how to convert enum on the first use [(#813)](https://github.com/tangcent/easy-yapi/pull/813)

* 2.4.4

	* fix: resolve desc of return type [(#807)](https://github.com/tangcent/easy-yapi/pull/807)

	* fix: support parse apis in several modules to one collection [(#806)](https://github.com/tangcent/easy-yapi/pull/806)

* 2.4.3

	* feat: new setting  `Postman > build example` [(#802)](https://github.com/tangcent/easy-yapi/pull/802)

* 2.4.2

	* feat: enum auto select field by type [(#797)](https://github.com/tangcent/easy-yapi/pull/797)

	* fix: resolve doc of fields for param annotated with @BeanParam [(#796)](https://github.com/tangcent/easy-yapi/pull/796)

	* chore: remove DataEventCollector.getData(DataKey) [(#792)](https://github.com/tangcent/easy-yapi/pull/792)

* 2.4.1

	* feat: resolve suspend function in kotlin [(#790)](https://github.com/tangcent/easy-yapi/pull/790)

* 2.4.0

	* feat: get resource with timeout [(#786)](https://github.com/tangcent/easy-yapi/pull/786)

	* chore: remove usages of KitUtils.safe [(#785)](https://github.com/tangcent/easy-yapi/pull/785)

	* feat: support new setting 'export selected method only' [(#783)](https://github.com/tangcent/easy-yapi/pull/783)

* 2.3.9

	* fix: not use '0' as example [(#771)](https://github.com/tangcent/easy-yapi/pull/771)

	* fix: fix resize of ApiCallDialog [(#770)](https://github.com/tangcent/easy-yapi/pull/770)

	* fix: fix  rule `field.default.value` [(#765)](https://github.com/tangcent/easy-yapi/pull/765)

	* feat: not require confirmation to export apis from directory [(#766)](https://github.com/tangcent/easy-yapi/pull/766)

* 2.3.8

	* feat: wrap script result [(#762)](https://github.com/tangcent/easy-yapi/pull/762)

	* fix: refactor the thread model [(#760)](https://github.com/tangcent/easy-yapi/pull/760)

	* fix: interval sleep during parsing [(#757)](https://github.com/tangcent/easy-yapi/pull/757)

* 2.3.7

	* chore: update docs [(#752)](https://github.com/tangcent/easy-yapi/pull/752)

	* feat: rename module quarkus to jaxrs [(#751)](https://github.com/tangcent/easy-yapi/pull/751)

* 2.3.6

	* chore: add Fastjson support [(#745)](https://github.com/tangcent/easy-yapi/pull/745)

	* feat: init recommend config even if no module be switched [(#744)](https://github.com/tangcent/easy-yapi/pull/744)

	* fix: set `readTimeout` for read resource by url [(#743)](https://github.com/tangcent/easy-yapi/pull/743)

	* feat: new method for rule context `class` [(#739)](https://github.com/tangcent/easy-yapi/pull/739)

	* feat: resolve RequestMapping from interfaces [(#738)](https://github.com/tangcent/easy-yapi/pull/738)

* 2.3.5

	* chore: update version of intellij-kotlin to 1.1.7 [(#731)](https://github.com/tangcent/easy-yapi/pull/731)

	* fix: fix Writer [(#726)](https://github.com/tangcent/easy-yapi/pull/726)

	* feat: provide rules to customize tables in markdown [(#724)](https://github.com/tangcent/easy-yapi/pull/724)

	* chore: update markdown.cn.config [(#721)](https://github.com/tangcent/easy-yapi/pull/721)

* 2.3.4

	* test: add test case for DataEventCollector [(#719)](https://github.com/tangcent/easy-yapi/pull/719)

	* build: upgrade idea SDK version [(#718)](https://github.com/tangcent/easy-yapi/pull/718)

	* test: add test case for CustomLogConfig [(#717)](https://github.com/tangcent/easy-yapi/pull/717)

	* feat: provide rules to customize markdown [(#716)](https://github.com/tangcent/easy-yapi/pull/716)

* 2.3.3

	* chore: update swagger.config [(#712)](https://github.com/tangcent/easy-yapi/pull/712)

	* fix: resolve mapping annotation for feign client [(#711)](https://github.com/tangcent/easy-yapi/pull/711)

	* feat: export apis from implements [(#709)](https://github.com/tangcent/easy-yapi/pull/709)

	* feat:  support change log charset by settings [(#708)](https://github.com/tangcent/easy-yapi/pull/708)

	* test: DefaultFileSaveHelperTest [(#707)](https://github.com/tangcent/easy-yapi/pull/707)

* 2.3.2

	* feat: [generic] auto fix http method [(#697)](https://github.com/tangcent/easy-yapi/pull/697)

	* feat: generic.spring.demo.config [(#696)](https://github.com/tangcent/easy-yapi/pull/696)

	* chore: fix warnings [(#695)](https://github.com/tangcent/easy-yapi/pull/695)

	* fix: use `any` instead of `forEach` to export api from superMethods [(#694)](https://github.com/tangcent/easy-yapi/pull/694)

	* fix: remove usage of  ObjectTypeAdapter.FACTORY [(#693)](https://github.com/tangcent/easy-yapi/pull/693)

	* feat: new Action FieldsToProperties [(#686)](https://github.com/tangcent/easy-yapi/pull/686)

	* chore: update docs [(#680)](https://github.com/tangcent/easy-yapi/pull/680)

* 2.3.1

	* feat: support @RequestLine、@Headers、@Param、@Body [(#676)](https://github.com/tangcent/easy-yapi/pull/676)

	* test: Test case of [DefaultDocParseHelper] [(#675)](https://github.com/tangcent/easy-yapi/pull/675)

	* feat: try parse api info from super methods [(#674)](https://github.com/tangcent/easy-yapi/pull/674)

	* fix: [json5] remove lead blank lines [(#673)](https://github.com/tangcent/easy-yapi/pull/673)

	* feat: improve Settings UI [(#672)](https://github.com/tangcent/easy-yapi/pull/672)

	* feat: [recommend] converter of reactivestreams.Publisher [(#667)](https://github.com/tangcent/easy-yapi/pull/667)

	* chore: update docs [(#665)](https://github.com/tangcent/easy-yapi/pull/665)

	* fix: resolve multi-line root desc as block comments [(#664)](https://github.com/tangcent/easy-yapi/pull/664)

	* feat: support custom annotation with Spring-RequestMapping [(#663)](https://github.com/tangcent/easy-yapi/pull/663)

	* feat: convert [java.sql.Date]&[java.sql.Time] as java.lang.String [(#662)](https://github.com/tangcent/easy-yapi/pull/662)

* 2.3.0

	* feat: recommend config for `Jackson-JsonUnwrapped` [(#658)](https://github.com/tangcent/easy-yapi/pull/658)

	* fix: remove redundant table header in markdown [(#654)](https://github.com/tangcent/easy-yapi/pull/654)

	* feat: new rule `json.additional.field` [(#652)](https://github.com/tangcent/easy-yapi/pull/652)

	* fix: read desc of class before compute by rule [(#649)](https://github.com/tangcent/easy-yapi/pull/649)

	* feat: support quarkus [(#648)](https://github.com/tangcent/easy-yapi/pull/648)

	* feat: support spring-feign [(#643)](https://github.com/tangcent/easy-yapi/pull/643)

	* feat: support Condition [(#641)](https://github.com/tangcent/easy-yapi/pull/641)

* 2.2.9

	* fix: fix regex rule [(#620)](https://github.com/tangcent/easy-yapi/pull/620)

	* feat: new event rules [(#616)](https://github.com/tangcent/easy-yapi/pull/616)

	* feat: [ScriptExecutor] explicit class [(#615)](https://github.com/tangcent/easy-yapi/pull/615)

	* feat: mock field of  java_date_types as datetime [(#610)](https://github.com/tangcent/easy-yapi/pull/610)

	* feat: support jackson-JsonFormat & spring-DateTimeFormat [(#609)](https://github.com/tangcent/easy-yapi/pull/609)

* 2.2.8

	* feat: new rule `field.advanced` [(#605)](https://github.com/tangcent/easy-yapi/pull/605)

	* fix: resolve Object as {} [(#603)](https://github.com/tangcent/easy-yapi/pull/603)

	* chore: fix action text [(#601)](https://github.com/tangcent/easy-yapi/pull/601)

	* feat: change action groups [(#600)](https://github.com/tangcent/easy-yapi/pull/600)

	* feat: recommend configs of Jackson JsonNaming(namingStrategy) [(#595)](https://github.com/tangcent/easy-yapi/pull/595)

	* test: add test case of RecommendConfigReader [(#591)](https://github.com/tangcent/easy-yapi/pull/591)

	* test: add test case for ProjectHelperTest [(#589)](https://github.com/tangcent/easy-yapi/pull/589)

	* chore: ignore `*Dialog`&`*Configurable`&`*Action.kt` for codecov [(#588)](https://github.com/tangcent/easy-yapi/pull/588)

	* chore: ignore `*Dialog`&`*Configurable`&`*Action.kt` for codecov [(#587)](https://github.com/tangcent/easy-yapi/pull/587)

* 2.2.7

	* feat: `method&field` support  containingClass&defineClass [(#585)](https://github.com/tangcent/easy-yapi/pull/585)

	* feat: new recommend config to support [not ignore `static final` field] [(#583)](https://github.com/tangcent/easy-yapi/pull/583)

	* fix: apply setting not work [(#582)](https://github.com/tangcent/easy-yapi/pull/582)

* 2.2.6

	* fix: AbstractEasyApiConfigurable checkUI after createComponent [(#579)](https://github.com/tangcent/easy-yapi/pull/579)

	* feat: update gui [(#578)](https://github.com/tangcent/easy-yapi/pull/578)

	* fix: [curl] escape [&\] [(#577)](https://github.com/tangcent/easy-yapi/pull/577)

	* feat: support export apis to specified postman collection [(#575)](https://github.com/tangcent/easy-yapi/pull/575)

	* feat: save yapi tokens in project scope [(#572)](https://github.com/tangcent/easy-yapi/pull/572)

	* feat: show workspace with type [(#571)](https://github.com/tangcent/easy-yapi/pull/571)

	* feat: support copy curl command from ApiDashBoard(YapiDashBoard) [(#569)](https://github.com/tangcent/easy-yapi/pull/569)

	* fix: resolve LazilyParsedNumber as Integer [(#568)](https://github.com/tangcent/easy-yapi/pull/568)

	* feat: add host to cache in exporting curl command [(#567)](https://github.com/tangcent/easy-yapi/pull/567)

	* feat: select workspace by comboBox [(#566)](https://github.com/tangcent/easy-yapi/pull/566)

	* feat: support export requests as curl command [(#565)](https://github.com/tangcent/easy-yapi/pull/565)

* 2.2.5

	* chore: add test case of PostmanApiHelper [(#562)](https://github.com/tangcent/easy-yapi/pull/562)

	* feat: refactor Task [(#561)](https://github.com/tangcent/easy-yapi/pull/561)

	* feat: disable [sync] for the node which is not loaded [(#560)](https://github.com/tangcent/easy-yapi/pull/560)

	* feat: optimize loading of postman collections [(#557)](https://github.com/tangcent/easy-yapi/pull/557)

	* feat: support export to postman customize workspace [(#524)](https://github.com/tangcent/easy-yapi/pull/524)

	* feat: add default keyboard-shortcut of ApiCallAction [(#553)](https://github.com/tangcent/easy-yapi/pull/553)

	* feat: trust [localhost]&[127.0.0.1] by default [(#552)](https://github.com/tangcent/easy-yapi/pull/552)

	* feat: keep changes in ApiCallDialog [(#551)](https://github.com/tangcent/easy-yapi/pull/551)

	* feat: populate default value or demo as field value [(#548)](https://github.com/tangcent/easy-yapi/pull/548)

	* fix: use "" instead of null in format request headers [(#547)](https://github.com/tangcent/easy-yapi/pull/547)

* 2.2.4

	* fix: Ignore SSL certificate in ApacheHttpClient [(#532)](https://github.com/tangcent/easy-yapi/pull/532)

	* feat: read default value as param's value [(#541)](https://github.com/tangcent/easy-yapi/pull/541)

	* fix: MarkdownFormatter#AbstractObjectFormatter [(#536)](https://github.com/tangcent/easy-yapi/pull/536)

	* feat: show error dialog on the active Dialog instead of project [(#533)](https://github.com/tangcent/easy-yapi/pull/533)

	* feat: bundled markdown render [(#530)](https://github.com/tangcent/easy-yapi/pull/530)

	* feat: remove recommend config of enum [(#521)](https://github.com/tangcent/easy-yapi/pull/521)

* 2.2.3
  
    * fix: not infer methods that return an interface type [(#518)](https://github.com/tangcent/easy-yapi/pull/518)
      
	* fix: configure FAIL_ON_EMPTY_BEANS as false [(#517)](https://github.com/tangcent/easy-yapi/pull/517)
      
	* perf: add several methods of RequestRuleWrap [(#509)](https://github.com/tangcent/easy-yapi/pull/509)

	* opti: remove api name from the prefix of attr [(#508)](https://github.com/tangcent/easy-yapi/pull/508)

	* build: collect feat/opti/perf as enhancements [(#504)](https://github.com/tangcent/easy-yapi/pull/504)

* 2.2.2
  
	* feat: update CachedResourceResolverTest [(#494)](https://github.com/tangcent/easy-yapi/pull/494)

	* chore: update dubbo.config [(#490)](https://github.com/tangcent/easy-yapi/pull/490)

	* fix: process the body as query if the httpMethod is GET [(#489)](https://github.com/tangcent/easy-yapi/pull/489)

	* feat: support queryExpanded [(#488)](https://github.com/tangcent/easy-yapi/pull/488)

* 2.2.1

	* feat: double click yapi node in YapiDashboardDialog to open yapi url [(#480)](https://github.com/tangcent/easy-yapi/pull/480)

	* feat: support generic export for several actions [(#478)](https://github.com/tangcent/easy-yapi/pull/478)

	* opti: trustHostsTextArea scrollable [(#477)](https://github.com/tangcent/easy-yapi/pull/477)

	* feat: support generic export [(#476)](https://github.com/tangcent/easy-yapi/pull/476)

	* opti: change some rule type to `Event` [(#473)](https://github.com/tangcent/easy-yapi/pull/473)

	* opti: close http response stream [(#472)](https://github.com/tangcent/easy-yapi/pull/472)

	* fix: resolveCycle in KVKit [(#471)](https://github.com/tangcent/easy-yapi/pull/471)

	* feat: always try close http response [(#470)](https://github.com/tangcent/easy-yapi/pull/470)

	* feat: remind to use login mode for yapi before 1.6.0 [(#465)](https://github.com/tangcent/easy-yapi/pull/465)

	* chore: fix release-rc [(#457)](https://github.com/tangcent/easy-yapi/pull/457)

	* fix: compute param type info between PARAM_BEFORE&PARAM_AFTER [(#455)](https://github.com/tangcent/easy-yapi/pull/455)

	* feat: it(class) isPrimitive/isPrimitiveWrapper [(#452)](https://github.com/tangcent/easy-yapi/pull/452)

	* chore: add test case of [ScriptClassContext] [(#449)](https://github.com/tangcent/easy-yapi/pull/449)

	* chore: update version of intellij-kotlin to 0.9.9-SNAPSHOT [(#450)](https://github.com/tangcent/easy-yapi/pull/450)

	* chore: add test case of rules [(#447)](https://github.com/tangcent/easy-yapi/pull/447)

	* chore: update test case of [*SettingHelper] [(#444)](https://github.com/tangcent/easy-yapi/pull/444)

	* chore: add test case of [Settings] [(#443)](https://github.com/tangcent/easy-yapi/pull/443)

	* chore: update HttpSettingsHelperTest [(#442)](https://github.com/tangcent/easy-yapi/pull/442)

	* feat: forbidden http access to distrust hosts [(#441)](https://github.com/tangcent/easy-yapi/pull/441)

	* feat: separate RequestBuilderListener from ClassExporter [(#438)](https://github.com/tangcent/easy-yapi/pull/438)

	* chore: remove deprecated usage [(#437)](https://github.com/tangcent/easy-yapi/pull/437)

	* fix: always try addMock in parseByJson5 [(#436)](https://github.com/tangcent/easy-yapi/pull/436)

	* opti: custom ConnectionManager for build httpClient [(#434)](https://github.com/tangcent/easy-yapi/pull/434)

* 2.2.0

	* feat: [methodDoc-yapi] parse params as query for `GET` [(#432)](https://github.com/tangcent/easy-yapi/pull/432)

	* fix: alway split tags with \n [(#431)](https://github.com/tangcent/easy-yapi/pull/431)

	* feat: support yapi json5 [(#430)](https://github.com/tangcent/easy-yapi/pull/430)

	* feat: init dbBeanBinderFactory by lazy [(#426)](https://github.com/tangcent/easy-yapi/pull/426)

	* opti: add the specified sqlite dependency [(#427)](https://github.com/tangcent/easy-yapi/pull/427)

	* feat: init dao by lazy [(#423)](https://github.com/tangcent/easy-yapi/pull/423)

	* fix: fromJson compatible with the old version [(#422)](https://github.com/tangcent/easy-yapi/pull/422)

* 2.1.9

	* fix: DefaultMethodDocHelper#appendDesc [(#406)](https://github.com/tangcent/easy-yapi/pull/406)

	* fix: block recursive call getValue [(#395)](https://github.com/tangcent/easy-yapi/pull/395)

	* fix: parse `Header`&`Param` by `ExtensibleKit.fromJson` [(#384)](https://github.com/tangcent/easy-yapi/pull/384)

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
