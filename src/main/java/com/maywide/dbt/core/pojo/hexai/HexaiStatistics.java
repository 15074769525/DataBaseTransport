package com.maywide.dbt.core.pojo.hexai;

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
public class HexaiStatistics {
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
    * 企业ID
    */
    private Integer corpId;

    /**
    * 创建用户
    */
    private String creator;

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
}