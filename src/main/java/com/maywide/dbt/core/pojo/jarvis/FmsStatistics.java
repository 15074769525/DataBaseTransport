package com.maywide.dbt.core.pojo.jarvis;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 文档审核统计表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FmsStatistics {
    /**
    * 流水号
    */
    private String batchId;

    /**
    * 识别时间
    */
    private Integer processTime;

    /**
    * 合同页数
    */
    private Integer docPages;

    /**
    * 创建时间
    */
    private Date createTime;

    /**
    * 结束时间
    */
    private Date endTime;

    /**
    * 识别类型
    */
    private String ocrType;

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
    * 开始识别时间
    */
    private Date startOcrTime;
}