package com.maywide.dbt.core.pojo.hexai;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 文件表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class HexaiFile {
    /**
    * 文件
    */
    private String fileId;

    /**
    * 批次ID
    */
    private String batchId;

    /**
    * 目录ID
    */
    private String folderId;

    /**
    * 文件名
    */
    private String fileName;

    /**
    * 原始文件名
    */
    private String srcFilename;

    /**
    * 文件状态(业务系统自己定义)
    */
    private Integer fileStatus;

    /**
    * 机构ID
    */
    private String corpId;

    /**
    * 创建者
    */
    private String creator;

    /**
    * 创建时间
    */
    private Date createTime;

    /**
    * 存储目录名
    */
    private String filePathname;

    /**
    * 批次类型由(业务系统自己定义)
    */
    private String batchType;

    /**
    * 文件保存目录
    */
    private String filePathUrl;
}