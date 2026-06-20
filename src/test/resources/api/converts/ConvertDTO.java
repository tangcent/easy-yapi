package com.itangcent.converts;

import java.math.BigInteger;
import java.util.Date;

public class ConvertDTO {

    /**
     * The id
     */
    private BigInteger id;

    /**
     * The create time
     */
    private Date createTime;

    /**
     * The name
     */
    private String name;

    public BigInteger getId() { return id; }
    public void setId(BigInteger id) { this.id = id; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
