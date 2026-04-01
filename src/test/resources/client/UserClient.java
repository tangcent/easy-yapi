package com.itangcent.client;

import com.itangcent.model.UserInfo;

/**
 * rpc apis about user
 * access user info
 *
 * @module user
 */
public interface UserClient {

    /**
     * say hello
     * not update anything
     */
    public String greeting();


    /**
     * update username
     *
     * @param id      user id
     * @param newName new user name
     * @param slogon  personal slogon
     * @deprecated use {@link #update(UserInfo)}
     */
    public UserInfo set(long id, String newName,
                        String slogon,
                        long times);


    /**
     * get user info
     *
     * @param id user id
     * @folder update-apis
     * @undone
     * @path /user/get
     */
    @Deprecated
    public UserInfo get(Long id);

    /**
     * create new use
     */
    public UserInfo add(UserInfo userInfo);

    /**
     * update user info
     */
    public void update(UserInfo userInfo);
}
