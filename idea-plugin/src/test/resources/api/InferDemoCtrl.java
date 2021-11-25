package com.itangcent.api;


import com.itangcent.api.BaseController;
import com.itangcent.model.Result;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * infer demo
 */
@RestController
@RequestMapping(value = "infer")
public class InferDemoCtrl extends BaseController {

    /**
     * Infer the response that contains the collection
     */
    @RequestMapping(value = "/interWithCollection", method = RequestMethod.POST)
    public Object interWithCollection(@PathVariable("id") Long id) {
        List<Map> list = new LinkedList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "string");//This is the key for the test
        map.put("key2", 666);//This is a test key valued 666
        Map<String, Object> value3 = new HashMap<>();
        value3.put("subKey", "string");//This is the key of the child
        //This is a child for test
        map.put("key3", value3);
        list.add(map);
        return Result.success(list);
    }

}
