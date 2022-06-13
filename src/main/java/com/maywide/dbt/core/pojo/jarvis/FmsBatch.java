package com.maywide.dbt.core.pojo.jarvis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class FmsBatch extends BaseModel {
    private String batchId;
    private String batchName;
    private Date endTime;
    private String batchType;
    private String channelCode;
    private String batchStatus;
    private String folderId;
    private String extId;
}