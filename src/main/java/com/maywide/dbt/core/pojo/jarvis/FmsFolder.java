package com.maywide.dbt.core.pojo.jarvis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FmsFolder extends BaseModel {
    private String folderId;
    private String parentId;
    private String folderName;
    private String aliases;
    private String objectPath;
    private String seqNo;
    private boolean deleted;
    private int storeId;
    private String saveFormat;
}