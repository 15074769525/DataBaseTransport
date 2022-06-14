package com.maywide.dbt.core.constant;

/**
 * @author qinwu.zhu
 * @since 2020/4/30 15:07
 */
public enum OcrTypeEnum {
    /**
     * 类型定义
     */
    DDS("00002007", "合同比对"),
    DES("00002008", "要素提取"),
    FES("00002009", "文件提取");

    private final String code;
    private final String desc;

    OcrTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }


    public String code() {
        return this.code;
    }

    public static OcrTypeEnum codeOf(String code) {
        for (OcrTypeEnum enums : OcrTypeEnum.values()) {
            if (enums.code().equalsIgnoreCase(code)) {
                return enums;
            }
        }
        return null;
    }
}

