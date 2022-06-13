package com.maywide.dbt.core.pojo.jarvis;


import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class BaseModel {
    private String crtUser;
    private String tenantId;
    private String orgId;
    private Date createTime;

    public BaseModel() {
        this.crtUser = "";
        this.tenantId = "";
        this.orgId = "";
    }
}