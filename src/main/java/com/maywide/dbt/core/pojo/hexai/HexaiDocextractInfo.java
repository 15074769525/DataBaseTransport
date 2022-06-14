package com.maywide.dbt.core.pojo.hexai;

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
public class HexaiDocextractInfo {
    /**
    * 批次号
    */
    private String batchId;

    /**
    * 模板CODE
    */
    private String ctCode;

    /**
    * 模板内容
    */
    private String extractwords;
}