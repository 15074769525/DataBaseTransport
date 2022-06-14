package com.maywide.dbt.core.constant;

/**
 * @author qinwu.zhu
 * @since 2020/4/30 15:07
 */
public enum FolderTypeEnum {
    /**
     * 身份证
     */
    FES("fesupload", "fesexport", "文件提取"),
    DDS("ddsupload", "ddsexport", "合同对比"),
    DES("desupload", "desexport", "要素提取");


    private final String folderName;
    private final String exportFolder;
    private final String desc;

    FolderTypeEnum(String folderName, String exportFolder,String desc) {
        this.folderName = folderName;
        this.exportFolder = exportFolder;
        this.desc = desc;
    }


    public String folderName() {
        return this.folderName;
    }
    public String exportFolder(){return this.exportFolder;}

    public static FolderTypeEnum codeOf(String code) {
        for (FolderTypeEnum enums : FolderTypeEnum.values()) {
            if (enums.folderName().equalsIgnoreCase(code)) {
                return enums;
            }
        }
        return null;
    }
}

