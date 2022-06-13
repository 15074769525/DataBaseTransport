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
public class EcmFile {
    private String objectId;

    private String name;

    private String docId;

    private String creator;

    private Date createTime;

    private String updateUser;

    private Date updateTime;

    private String contentUrl;
}