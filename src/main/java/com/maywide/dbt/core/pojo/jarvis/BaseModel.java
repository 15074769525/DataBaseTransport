package com.maywide.dbt.core.pojo.jarvis;


import cn.hutool.core.util.IdUtil;
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

    public static void main(String[] args) {
        System.out.println(IdUtil.simpleUUID());
    }
}