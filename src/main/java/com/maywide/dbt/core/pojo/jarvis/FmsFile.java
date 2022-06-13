package com.maywide.dbt.core.pojo.jarvis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FmsFile extends BaseModel {
    private String fileId;
    private String batchId;
    private String batchType;
    private String folderId;
    private String fileName;
    private String srcFileName;
    private String filePathName;
    private Integer fileStatus;
    private String resolve;
    private String filePathUrl;
    private String parentId;
}