package com.maywide.dbt.core.pojo.hexai;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
    * 模板要素与模板关联表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class HexaiContractTemplateFactror {

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
     * 描述
     */
    private String ctDesc;

    /**
     * 企业ID
     */
    private Integer corpId;

    private Date createTime;

    /**
     * 自增主键
     */
    private Integer cfNo;

    /**
     * 要素名称
     */
    private String cfName;

    /**
     * 要素关键字
     */
    private String cfKeyword;

    /**
     * 备注
     */
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
}