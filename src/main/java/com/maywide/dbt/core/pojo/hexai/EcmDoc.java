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
public class EcmDoc {
    private String objectId;

    private String name;

    private String creator;

    private Date createTime;

    private String updateUser;

    private Date updateTime;

    private String ocrstatus;

    private Integer ocrflag;

    private String clabel;

    //"00002007", "合同比较"
    //"00002008", "合同提取"
    //"00002009", "文件提取"
    public String mappingBatchType(){
        String batchType="";
        switch (this.ocrflag){
            case 7:
                batchType="00002007";
                break;
            case 13:
                batchType="00002008";
                break;
            case 15:
                batchType="00002009";
                break;
            default:
        }
        return batchType;
    }

    //"0", "开始"
    //"100", "识别完成"
    //"-20", "失败"
    //"-10140", "服务内部错误"
    public String mappingBatchStatus(){
        String batchStatus="";
        switch (this.ocrstatus){
            case "1":
                batchStatus="0";
                break;
            case "10":
                batchStatus="100";
                break;
            case "20":
                batchStatus="-20";
                break;
            case "30":
                batchStatus="-10140";
                break;
            default:
        }
        return batchStatus;
    }
}
