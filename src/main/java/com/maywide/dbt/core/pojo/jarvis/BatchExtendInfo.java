package com.maywide.dbt.core.pojo.jarvis;

import lombok.Data;

import java.util.Date;

/**
 * 文档审核补充信息表
 *
 * @author qinwu.zhu
 * @since 2020/11/15 17:07
 */
@Data
public class BatchExtendInfo {
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
     * 模板内容
     */
    private String extractWords;
    /**
     * 是否识别印章
     */
    private String ocrStamp;
    /**
     * 是否去除水印
     */
    private String removeWatermarkSrc;

    /**
     * 扫描件去除水印
     */
    private String removeWatermarkScan;
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
     * 审核详情，格式为：[{"arId":"","status":"","riskType":""}]，arId为规则表主键，status，0：不通过，1：通过,riskType为风险类型
     */
    private String auditItems;
    /**
     * 是否去除印章
     */
    private String removeStamp;
}