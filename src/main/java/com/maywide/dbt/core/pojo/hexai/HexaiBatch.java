package com.maywide.dbt.core.pojo.hexai;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 批次表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class HexaiBatch {
    /**
    * 批次ID
    */
    private String batchId;

    /**
    * 批次名
    */
    private String batchName;

    /**
    * 目录ID
    */
    private String folderId;

    /**
    * 创建者
    */
    private String creator;

    /**
    * 机构ID
    */
    private String corpId;

    /**
    * 创建时间
    */
    private Date createTime;

    /**
    * 批次完成时间
    */
    private Date endTime;

    /**
    * 批次状态(业务系统自己定义)
    */
    private String batchStatus;

    /**
    * 批次类型由(业务系统自己定义)
    */
    private String batchType;

    /**
    * 外部渠道码
    */
    private String channelCode;

    /**
    * 外部关联ID
    */
    private String extId;
}