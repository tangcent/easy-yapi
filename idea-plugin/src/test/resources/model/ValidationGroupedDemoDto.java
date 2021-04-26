package com.itangcent.model;

import com.itangcent.constant.Add;
import com.itangcent.constant.Update;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class ValidationGroupedDemoDto {

    @NotNull(groups = Add.class)
    private String strForAdd;

    @NotEmpty(groups = Update.class)
    private String notEmptyForUpdate;

    public String getStrForAdd() {
        return strForAdd;
    }

    public void setStrForAdd(String strForAdd) {
        this.strForAdd = strForAdd;
    }

    public String getNotEmptyForUpdate() {
        return notEmptyForUpdate;
    }

    public void setNotEmptyForUpdate(String notEmptyForUpdate) {
        this.notEmptyForUpdate = notEmptyForUpdate;
    }
}
