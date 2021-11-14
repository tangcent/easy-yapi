package com.itangcent.quarkus;

import com.itangcent.annotation.Public;
import com.itangcent.api.quarkus.UserDTO;
import com.itangcent.constant.UserType;
import com.itangcent.dto.IResult;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import com.itangcent.quarkus.MyGet;
import com.itangcent.quarkus.MyPut;

import javax.ws.rs.BeanParam;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * apis about user
 * access user info
 *
 * @module users
 */
@Path("/user")
public class UserResource {

    /**
     * say hello
     * not update anything
     */
    @Public
    @GET
    @Path("/greeting")
    public String greeting() {
        return "hello world";
    }

    /**
     * update user name
     *
     * @param id      current user id
     * @param newName new user name
     * @param slogon  personal slogon
     * @deprecated replace with {@link #update(UserInfo)}
     */
    @PUT
    @Path(value = "/set")
    public Object set(@CookieParam(value = "currentId") @DefaultValue("123") long id,
                      String newName,
                      @QueryParam("slogon") String slogon,
                      @QueryParam("times") @DefaultValue("10") long times) {

        UserInfo userInfo = new UserInfo();

        userInfo.setId(id);
        userInfo.setName(newName);
        userInfo.setAge(45);
        return Result.success(userInfo);
    }

    /**
     * get user info
     *
     * @param id user id
     * @folder update-apis
     * @undone
     */
    @Deprecated
    @GET
    @Path("/get/{id}")
    public IResult get(@PathParam("id") Long id) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setName("Tony Stark");
        userInfo.setAge(45);
        return Result.success(userInfo);
    }

    /**
     * get detail for a single user
     *
     * @param id user id
     * @undone
     */
    @GET
    @Path(value = "/get")
    public IResult getById(@QueryParam("id") Long id) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setName("Tony Stark");
        userInfo.setAge(45);
        return Result.success(userInfo);
    }

    /**
     * create an user
     */
    @POST
    @Path("/add")
    public Result<UserInfo> add(@FormParam UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * update user info
     */
    @MyPut
    @Path("update")
    public IResult update(@BeanParam UserDTO userDTO) {
        return Result.success(userDTO);
    }

    /**
     * list user of special type
     *
     * @param type user type {@link com.itangcent.constant.UserType}
     * @prerequest groupA
     */
    @GET
    @Path(value = "/list")
    public IResult list(@QueryParam("type") Integer type) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(1L);
        userInfo.setName("Tom");
        userInfo.setAge(25);
        return Result.success(Collections.singletonList(userInfo));
    }

    /**
     * list user of special type
     *
     * @param type user type {@link com.itangcent.constant.UserType}
     */
    @GET
    @Path(value = "/list/{type}")
    public IResult listTypeInPath(@PathParam("type") Integer type) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(1l);
        userInfo.setName("Tom");
        userInfo.setAge(25);
        userInfo.setType(type);
        return Result.success(Collections.singletonList(userInfo));
    }

    /**
     * delete user
     *
     * @param id user id
     */
    @DELETE
    @Path("/{id}")
    public Object delete(@PathParam("id") Long id) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setName("Tony Stark");
        userInfo.setAge(45);
        return Result.success(userInfo);
    }

    /**
     * get current user type
     *
     * @return {@link com.itangcent.constant.UserTypeConstant}
     */
    @MyGet
    @Path("/type")
    public Result<Integer> currUserType() {
        return Result.success(UserType.values()[new Random(System.currentTimeMillis()).nextInt(UserType.values().length)].getType());
    }

    /**
     * get all user type
     *
     * @return {@link com.itangcent.constant.UserType#getType()}
     */
    @GET
    @Path("/types")
    public Result<List<Integer>> types() {
        final List<Integer> types = Stream.of(UserType.values()).map(UserType::getType).collect(Collectors.toList());
        return Result.success(types);
    }

    /**
     * update user name
     *
     * @param id      current user id
     * @param newName new user name
     * @param slogon  personal slogon
     * @tag a&zs, b, c
     * @deprecated replace with {@link #update(UserInfo)}
     */
    @PUT
    @Path("/set")
    public Object setName(@CookieParam(value = "currentId") @DefaultValue("123") long id,
                          @QueryParam("newName") String newName,
                          @QueryParam("slogon") String slogon,
                          @HeaderParam("userId") String userId
    ) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setName(newName);
        userInfo.setAge(45);
        return Result.success(userInfo);
    }
}
