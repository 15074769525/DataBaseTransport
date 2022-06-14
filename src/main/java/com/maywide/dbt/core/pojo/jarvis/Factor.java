package com.maywide.dbt.core.pojo.jarvis;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 文档审核模板要素表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Factor {
    /**
    * 主键
    */
    private Integer cfNo;

    /**
    * 模板ID
    */
    private String ctNo;

    /**
    * 要素名称
    */
    private String cfName;

    /**
    * 要素格式
    */
    private String cfFormat;

    /**
    * 要素关键字
    */
    private String cfKeyword;

    private String cfRemark;

    /**
    * 搜索范围
    */
    private String cfPosition;

    /**
    * 关键字剔除
    */
    private String cfRange;

    /**
    * 搜索规则
    */
    private String cfRule;

    /**
    * 要素颜色
    */
    private String cfColor;

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