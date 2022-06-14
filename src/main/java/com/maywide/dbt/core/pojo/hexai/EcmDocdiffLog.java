package com.maywide.dbt.core.pojo.hexai;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class EcmDocdiffLog {
    /**
    * 流水号
    */
    private String objectId;

    /**
    * 识别时间
    */
    private Integer processtime;

    /**
    * 合同页数
    */
    private Integer docPages;

    /**
    * 客户id
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
}