package com.maywide.dbt.core.pojo.jarvis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
    * 存储根目录表
    */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FmsStore {
    /**
    * 关键字ID
    */
    private Integer id;

    /**
    * 名称
    */
    private String name;

    /**
    * 存储类型
    */
    private String storeType;

    /**
    * 存储目录
    */
    private String storeUrl;
}