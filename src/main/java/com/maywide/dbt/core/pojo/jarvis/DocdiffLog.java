package com.maywide.dbt.core.pojo.jarvis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class DocdiffLog {
    private String batchId;

    private Integer processTime;

    private Integer docPages;

    private Date endTime;

    private Date updateTime;

    private Long aclId;

    private String ocrStatus;

    private String ocrType;

    private String crtUser;

    private String tenantId;

    private String orgId;

    private Date createTime;

    private Date startOcrTime;
}