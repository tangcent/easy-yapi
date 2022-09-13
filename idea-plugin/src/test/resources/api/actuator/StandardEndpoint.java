package com.itangcent.springboot.demo.controller;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Endpoint(id = "standard")
public class StandardEndpoint {

    /**
     * endpointByGet
     *
     * @param username the username to use
     * @param age      age of the user
     * @return a map
     */
    @ReadOperation
    public Map<String, Object> endpointByGet(@Selector String username, @Selector Integer age) {
        Map<String, Object> customMap = new HashMap<>();
        customMap.put("httpMethod", HttpMethod.GET.toString());
        customMap.put("username", username);
        customMap.put("age", age);
        return customMap;
    }

    /**
     * endpointByPost
     *
     * @param id       user id
     * @param username the username to use
     * @param age      age of the user
     * @return a map
     */
    @WriteOperation
    public Map<String, Object> endpointByPost(
            @Selector String id,
            String username,
            Integer age
    ) {
        Map<String, Object> customMap = new HashMap<>();
        customMap.put("httpMethod", HttpMethod.POST.toString());
        customMap.put("id", id);
        customMap.put("username", username);
        customMap.put("age", age);
        return customMap;
    }

    /**
     * endpointByDelete
     *
     * @param id         user id
     * @param completely real delete
     * @return a map
     */
    @DeleteOperation
    public Map<String, Object> endpointByDelete(
            @Selector String id,
            Boolean completely
    ) {
        Map<String, Object> customMap = new HashMap<>();
        customMap.put("httpMethod", HttpMethod.DELETE.toString());
        customMap.put("id", id);
        if (Boolean.TRUE.equals(completely)) {
            customMap.put("completely", "yes");
        } else {
            customMap.put("deleted", true);
        }
        return customMap;
    }
}
