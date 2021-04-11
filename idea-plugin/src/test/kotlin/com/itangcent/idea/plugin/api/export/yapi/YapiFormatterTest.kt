package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.itangcent.common.kit.toJson
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.export.requestOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.ImmutableSystemProvider
import com.itangcent.mock.SettingBinderAdaptor

/**
 * Test case of [YapiFormatter]
 */
internal class YapiFormatterTest : YapiSpringClassExporterBaseTest() {

    @Inject
    private lateinit var yapiFormatter: YapiFormatter

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
        settings.inferEnable = true
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SystemProvider::class) {
            it.toInstance(ImmutableSystemProvider(1618124194123L))
        }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
    }

    /**
     * use json-schema by default
     */
    fun testDoc2Item() {
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        assertEquals("[{\"res_body\":\"{\\\"type\\\":\\\"string\\\",\\\"\$schema\\\":\\\"http://json-schema.org/draft-04/schema#\\\"}\",\"query_path\":{\"path\":\"/user/greeting\",\"params\":[]},\"method\":\"GET\",\"res_body_type\":\"json\",\"index\":0,\"type\":\"static\",\"title\":\"say hello\",\"path\":\"/user/greeting\",\"req_body_is_json_schema\":false,\"__v\":0,\"markdown\":\"say hello\\n not update anything\",\"req_headers\":[],\"edit_uid\":0,\"up_time\":1618124194,\"tag\":[],\"req_query\":[],\"api_opened\":true,\"add_time\":1618124194,\"res_body_is_json_schema\":true,\"status\":\"done\",\"desc\":\"\\u003cp\\u003esay hello\\n not update anything\\u003c/p\\u003e\"}]", yapiFormatter.doc2Item(requests[0])
                .toJson())
        assertEquals("[{\"res_body\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"code\\\":{\\\"type\\\":\\\"integer\\\",\\\"description\\\":\\\"response code\\\"},\\\"msg\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"message\\\"},\\\"data\\\":{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"id\\\":{\\\"type\\\":\\\"integer\\\",\\\"description\\\":\\\"user id\\\"},\\\"type\\\":{\\\"type\\\":\\\"integer\\\",\\\"description\\\":\\\"user type\\\"},\\\"name\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"user name\\\"},\\\"age\\\":{\\\"type\\\":\\\"integer\\\",\\\"description\\\":\\\"user age\\\"},\\\"sex\\\":{\\\"type\\\":\\\"integer\\\"},\\\"birthDay\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"user birthDay\\\"},\\\"regtime\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"user regtime\\\"}},\\\"description\\\":\\\"response data\\\"}},\\\"\$schema\\\":\\\"http://json-schema.org/draft-04/schema#\\\"}\",\"query_path\":{\"path\":\"/user/get/{id}\",\"params\":[]},\"method\":\"GET\",\"res_body_type\":\"json\",\"index\":0,\"type\":\"static\",\"title\":\"get user info\",\"path\":\"/user/get/{id}\",\"req_body_is_json_schema\":false,\"__v\":0,\"markdown\":\"get user info\",\"req_headers\":[{\"name\":\"token\",\"value\":\"\",\"desc\":\"auth token\",\"example\":\"123456\",\"required\":1}],\"edit_uid\":0,\"up_time\":1618124194,\"tag\":[\"deprecated\"],\"req_query\":[{\"name\":\"id\",\"value\":\"0\",\"example\":\"0\",\"desc\":\"user id \",\"required\":0}],\"api_opened\":false,\"add_time\":1618124194,\"res_body_is_json_schema\":true,\"status\":\"undone\",\"desc\":\"\\u003cp\\u003eget user info\\u003c/p\\u003e\"}]", yapiFormatter.doc2Item(requests[1])
                .toJson())
    }

    /**
     * use json5
     */
    fun testDoc2ItemWithJson5() {
        settings.yapiReqBodyJson5 = true
        settings.yapiResBodyJson5 = true
        val requests = ArrayList<Request>()
        classExporter.export(userCtrlPsiClass, requestOnly {
            requests.add(it)
        })
        (classExporter as Worker).waitCompleted()
        assertEquals("[{\"res_body\":\"\\\"\\\"\",\"query_path\":{\"path\":\"/user/greeting\",\"params\":[]},\"method\":\"GET\",\"res_body_type\":\"json\",\"index\":0,\"type\":\"static\",\"title\":\"say hello\",\"path\":\"/user/greeting\",\"req_body_is_json_schema\":false,\"__v\":0,\"markdown\":\"say hello\\n not update anything\",\"req_headers\":[],\"edit_uid\":0,\"up_time\":1618124194,\"tag\":[],\"req_query\":[],\"api_opened\":true,\"add_time\":1618124194,\"res_body_is_json_schema\":false,\"status\":\"done\",\"desc\":\"\\u003cp\\u003esay hello\\n not update anything\\u003c/p\\u003e\"}]", yapiFormatter.doc2Item(requests[0])
                .toJson())
        assertEquals("[{\"res_body\":\"{\\n    \\\"code\\\": 0, //response code\\n    \\\"msg\\\": \\\"success\\\", //message\\n    \\\"data\\\": { //response data\\n        \\\"id\\\": 0, //user id\\n        \\\"type\\\": 0, //user type\\n        \\\"name\\\": \\\"Tony Stark\\\", //user name\\n        \\\"age\\\": 45, //user age\\n        \\\"sex\\\": 0,\\n        \\\"birthDay\\\": \\\"\\\", //user birthDay\\n        \\\"regtime\\\": \\\"\\\" //user regtime\\n    }\\n}\",\"query_path\":{\"path\":\"/user/get/{id}\",\"params\":[]},\"method\":\"GET\",\"res_body_type\":\"json\",\"index\":0,\"type\":\"static\",\"title\":\"get user info\",\"path\":\"/user/get/{id}\",\"req_body_is_json_schema\":false,\"__v\":0,\"markdown\":\"get user info\",\"req_headers\":[{\"name\":\"token\",\"value\":\"\",\"desc\":\"auth token\",\"example\":\"123456\",\"required\":1}],\"edit_uid\":0,\"up_time\":1618124194,\"tag\":[\"deprecated\"],\"req_query\":[{\"name\":\"id\",\"value\":\"0\",\"example\":\"0\",\"desc\":\"user id \",\"required\":0}],\"api_opened\":false,\"add_time\":1618124194,\"res_body_is_json_schema\":false,\"status\":\"undone\",\"desc\":\"\\u003cp\\u003eget user info\\u003c/p\\u003e\"}]", yapiFormatter.doc2Item(requests[1])
                .toJson())
    }
}