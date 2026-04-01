package com.itangcent.api;


import com.itangcent.api.BaseController;
import com.itangcent.constant.UserType;
import com.itangcent.model.CustomMap;
import com.itangcent.model.Node;
import com.itangcent.model.PageRequest;
import com.itangcent.model.Result;
import com.itangcent.model.Root;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * test apis
 */
@RestController
@RequestMapping(value = "/test")
public class TestCtrl extends BaseController {

    /**
     * test RequestHeader
     *
     * @param token input token
     * @return token output
     * @module test-only
     * @real_return {@link Result<UserDto>}
     */
    @RequestMapping("/header")
    public String header(
            @RequestHeader("x-token") String token) {
        return token;
    }

    /**
     * test query with array parameters
     *
     * @param strings string array
     * @param ints    integer array
     * @param fake    it was only a joke
     */
    @RequestMapping("/arrays")
    public String header(@RequestParam(name = "string", required = false) String[] strings,
                         @RequestParam(name = "int", defaultValue = "1") int[] ints,
                         @RequestParam(name = "none", defaultValue = "1") int fake
    ) {
        return "ok";
    }

    /**
     * test ignored method
     *
     * @ignore
     */
    @RequestMapping("/ignore")
    public String ignore() {
        return "ignore";
    }

    /**
     * test query with javax.servlet.http.HttpServletRequest
     */
    @RequestMapping("/httpServletRequest")
    public String request(HttpServletRequest httpServletRequest) {
        httpServletRequest.getAuthType();
        return "javax.servlet.http.HttpServletRequest";
    }

    /**
     * test query with javax.servlet.http.HttpServletResponse
     */
    @RequestMapping("/httpServletResponse")
    public String response(HttpServletResponse httpServletResponse) {
        return "javax.servlet.http.HttpServletResponse";
    }

    /**
     * test api return void
     */
    @RequestMapping("/return/void")
    public void returnvoid() {
        return;
    }

    /**
     * test api return Void
     */
    @RequestMapping("/return/Void")
    public Void returnVoid() {
        return null;
    }

    /**
     * test api return Result<Void>
     */
    @RequestMapping("/return/result/Void")
    public Result<Void> returnResultVoid() {
        return null;
    }

    /**
     * test api return Enum
     */
    @RequestMapping("/return/enum")
    public UserType returnEnum() {
        return null;
    }

    /**
     * test api return Result<Enum>
     */
    @RequestMapping("/return/result/enum")
    public Result<UserType> returnResultEnum() {
        return null;
    }

    /**
     * test api return Enum field
     *
     * @return user type {@link com.itangcent.constant.UserType}
     */
    @RequestMapping("/return/enum/field")
    public int returnEnumField() {
        return null;
    }


    /**
     * test api return Result<Enum field>
     *
     * @return user type {@link com.itangcent.constant.UserType}
     */
    @RequestMapping("/return/result/enum/field")
    public Result<Integer> returnResultEnumField() {
        return null;
    }

    /**
     * return nested node
     */
    @RequestMapping("/return/node")
    public Result<Node> returnNode(Node node) {
        return null;
    }

    /**
     * return root with nested nodes
     */
    @GetMapping("/return/root")
    public Result<Root> returnRoot(Root root) {
        return null;
    }

    /**
     * return customMap
     */
    @GetMapping("/return/customMap")
    public Result<CustomMap> returnAjaxResult(CustomMap customMap) {
        return null;
    }

    /**
     * user page query
     */
    @GetMapping("/call/page/user")
    public Result<CustomMap> pageRequest(PageRequest<UserInfo> userInfoPageRequest) {
        return null;
    }

    /**
     * user page query with ModelAttribute
     */
    @PostMapping("/call/page/user/form")
    public Result<CustomMap> pageRequestWithModelAttribute(@ModelAttribute PageRequest<UserInfo> userInfoPageRequest) {
        return null;
    }

    /**
     * user page query with POST
     */
    @PostMapping("/call/page/user/post")
    public Result<CustomMap> pageRequestWithPost(PageRequest<UserInfo> userInfoPageRequest) {
        return null;
    }

    /**
     * user page query with array
     */
    @GetMapping("/call/page/user/array")
    public Result<UserInfo[]> listPageRequestWithArray(UserInfo[] userInfoPageRequest) {
        return null;
    }
}
