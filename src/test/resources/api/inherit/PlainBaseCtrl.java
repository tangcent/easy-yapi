package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;

/**
 * Case 3 base: abstract class with methods but NO mapping annotations.
 */
public abstract class PlainBaseCtrl {

    /**
     * get item
     */
    public abstract Result<UserInfo> getItem();

    /**
     * save item
     */
    public abstract Result<UserInfo> saveItem(UserInfo item);
}
