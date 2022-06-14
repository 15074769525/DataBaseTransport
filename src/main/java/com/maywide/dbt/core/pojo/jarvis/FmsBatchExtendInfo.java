package com.maywide.dbt.core.pojo.jarvis;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 文档审核补充信息表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FmsBatchExtendInfo {
    /**
    * 批次号
    */
    private String batchId;

    /**
    * 模板CODE
    */
    private String ctCode;

    /**
    * 审核模板ID
    */
    private String atId;

    /**
    * 是否识别印章
    */
    private String ocrStamp;

    private String removeWatermarkSrc;

    /**
    * 模板内容
    */
    private String extractwords;

    /**
    * 开始识别时间
    */
    private Date startOcrTime;

    /**
    * 重新识别时间
    */
    private Date againOcrTime;

    /**
    * 审核结论，1：待审核，2：通过，3：不通过
    */
    private String auditStatus;

    /**
    * 审核备注
    */
    private String auditRemark;

    /**
    * 审核详情，格式为：[{"arId":"","status":""}]，arId为规则表主键，status，0：不通过，1：通过
    */
    private String auditItems;

    /**
    * 是否去除印章
    */
    private String removeStamp;

    /**
    * 扫描件是否祛除水印
    */
    private String removeWatermarkScan;
}