package com.maywide.dbt.core.pojo.jarvis;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 文档审核模板表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Template {
    /**
    * 模板编号
    */
    private String ctNo;

    /**
    * 模板代码
    */
    private String ctCode;

    /**
    * 编号名称
    */
    private String ctName;

    /**
    * 引擎配置ID
    */
    private String ecId;

    /**
    * 描述
    */
    private String ctDesc;

    /**
    * 创建者
    */
    private String crtUser;

    /**
    * 租户ID
    */
    private String tenantId;

    /**
    * 机构ID
    */
    private String orgId;

    /**
    * 创建时间
    */
    private Date createTime;

    /**
    * 更新时间
    */
    private Date updateTime;
}