package com.maywide.dbt.core.execute;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.maywide.dbt.config.datasource.dynamic.Constants;
import com.maywide.dbt.config.datasource.dynamic.DbContextHolder;
import com.maywide.dbt.core.constant.FolderTypeEnum;
import com.maywide.dbt.core.constant.OcrTypeEnum;
import com.maywide.dbt.core.pojo.hexai.*;
import com.maywide.dbt.core.pojo.jarvis.*;
import com.maywide.dbt.core.services.JdbcUtilServices;
import com.maywide.dbt.util.SpringJdbcTemplate;
import com.maywide.dbt.util.SqlUtil;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class Hexai1139DataTransport {

    @Value("${target.datasource.names}")
    private String targetNames;

    private static final Logger log = LoggerFactory.getLogger(Hexai1139DataTransport.class);
    public static final int WORK_QUE_SIZE = 3000;
    public static final int BATCH_PAGESIZE = 5000;

    public static ConcurrentHashMap<String, JSONObject> successMap = new ConcurrentHashMap<>();

    private static final AtomicLong along = new AtomicLong(0);

    public static ThreadPoolExecutor dataCopyPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 10 + 1, Runtime.getRuntime().availableProcessors() * 15 + 1, 30, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(Hexai1139DataTransport.WORK_QUE_SIZE), new ThreadPoolExecutor.CallerRunsPolicy());

    @Autowired
    private SpringJdbcTemplate springJdbcTemplate;

    @Autowired
    private JdbcUtilServices jdbcUtilServices;

    @Autowired
    private TableTransport tableTransport;

    @Value("${file.rootDir:D:\\exdoc}")
    private String fileRootDir;

    @Value("${userId:JARVIS}")
    private String userId;

    @Value("${orgId:19}")
    private String orgId;

    private String tenantId;


    public void startCopyData() {
        successMap = new ConcurrentHashMap<>();
        new Thread(new BatchDataWork()).start();
        new Thread(new ContractTemplateDataWork()).start();
    }

    //处理批次数据迁移
    //t_ai_fms_batch->t_ai_fms_batch
    //t_ai_fms_file->t_ai_fms_file
    //t_ai_dds_statistics->t_ai_dds_statistics
    //t_ai_dds_docextract_info->t_ai_dds_batch_extend_info
    private class BatchDataWork implements Runnable {

        //批次数据查询sql
        private String hexaiBatchSelectSql = " SELECT BI.* FROM T_AI_FMS_BATCH BI ";

        //批次数据查询sql
        private String storeSelectSql = " SELECT S.* FROM T_AI_FMS_STORE S WHERE ID = 0 ";
        //批次目录查询sql
        private String folderSelectSql = " SELECT * FROM T_AI_FMS_FOLDER WHERE FOLDER_NAME = ? AND DELETED!='1' ";
        private String folderInsertSql = " INSERT INTO T_AI_FMS_FOLDER (FOLDER_ID, FOLDER_NAME," +
                "OBJECT_PATH,ALIASES, DELETED, CRT_USER,CREATE_TIME,TENANT_ID,ORG_ID,STORE_ID) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?) ";

        private FmsFolder fesuploadFolder;
        private FmsFolder ddsuploadFolder;
        private FmsFolder desuploadFolder;
        private FmsStore store;

        public BatchDataWork() {
            OcrTypeEnum[] values = OcrTypeEnum.values();
            //追加过滤条件
            if (ArrayUtil.isNotEmpty(values)) {
                String condition = " where BATCH_TYPE in (";
                for (int i = 0; i < values.length; i++) {
                    condition = condition + " '" + values[i].code() + "' ";
                    if (i != values.length - 1) {
                        condition = condition + ",";
                    }
                }
                condition = condition + ") ";
                hexaiBatchSelectSql = hexaiBatchSelectSql + condition;
            }
        }

        @Override
        public void run() {
            //判断根目录是否存在
            boolean rootDirExist = FileUtil.exist(fileRootDir);
            if (!rootDirExist) {
                log.error("{} 目录不存在，取消批次数据迁移", rootDirExist);
                return;
            }
            log.info("————————————开始批次数据迁移————————————");
            try {
                //1.初始化批次目录
                DbContextHolder.setDBType(targetNames);
                Map<String, Object> storeMap = springJdbcTemplate.queryForMap(storeSelectSql);
                store = JSON.parseObject(JSON.toJSONString(storeMap), FmsStore.class);
                List<Map<String, Object>> folderList = springJdbcTemplate.queryForList(folderSelectSql, FolderTypeEnum.FES.folderName());
                if (CollectionUtil.isNotEmpty(folderList)) {
                    fesuploadFolder = JSON.parseObject(JSON.toJSONString(folderList.get(0)), FmsFolder.class);
                }
                if (Objects.isNull(fesuploadFolder)) {
                    fesuploadFolder = new FmsFolder();
                    fesuploadFolder.setFolderId(IdUtil.nanoId(6));
                    fesuploadFolder.setFolderName(FolderTypeEnum.FES.folderName());
                    fesuploadFolder.setObjectPath("/" + FolderTypeEnum.FES.folderName());
                    fesuploadFolder.setAliases(FolderTypeEnum.FES.folderName());
                    fesuploadFolder.setDeleted(false);
                    fesuploadFolder.setCrtUser(userId);
                    fesuploadFolder.setCreateTime(new Date());
                    fesuploadFolder.setTenantId(tenantId);
                    fesuploadFolder.setOrgId(orgId);
                    fesuploadFolder.setStoreId(0);
                    Object[] values = new Object[10];
                    values[0] = fesuploadFolder.getFolderId();
                    values[1] = fesuploadFolder.getFolderName();
                    values[2] = fesuploadFolder.getObjectPath();
                    values[3] = fesuploadFolder.getAliases();
                    values[4] = fesuploadFolder.isDeleted();
                    values[5] = fesuploadFolder.getCrtUser();
                    values[6] = fesuploadFolder.getCreateTime();
                    values[7] = fesuploadFolder.getTenantId();
                    values[8] = fesuploadFolder.getOrgId();
                    values[9] = fesuploadFolder.getStoreId();
                    int row = springJdbcTemplate.update(folderInsertSql, values);
                }
                folderList = springJdbcTemplate.queryForList(folderSelectSql, FolderTypeEnum.DDS.folderName());
                if (CollectionUtil.isNotEmpty(folderList)) {
                    ddsuploadFolder = JSON.parseObject(JSON.toJSONString(folderList.get(0)), FmsFolder.class);
                }
                if (Objects.isNull(ddsuploadFolder)) {
                    ddsuploadFolder = new FmsFolder();
                    ddsuploadFolder.setFolderId(IdUtil.nanoId(6));
                    ddsuploadFolder.setFolderName(FolderTypeEnum.DDS.folderName());
                    ddsuploadFolder.setObjectPath("/" + FolderTypeEnum.DDS.folderName());
                    ddsuploadFolder.setAliases(FolderTypeEnum.DDS.folderName());
                    ddsuploadFolder.setDeleted(false);
                    ddsuploadFolder.setCrtUser(userId);
                    ddsuploadFolder.setCreateTime(new Date());
                    ddsuploadFolder.setTenantId(tenantId);
                    ddsuploadFolder.setOrgId(orgId);
                    ddsuploadFolder.setStoreId(0);
                    Object[] values = new Object[10];
                    values[0] = ddsuploadFolder.getFolderId();
                    values[1] = ddsuploadFolder.getFolderName();
                    values[2] = ddsuploadFolder.getObjectPath();
                    values[3] = ddsuploadFolder.getAliases();
                    values[4] = ddsuploadFolder.isDeleted();
                    values[5] = ddsuploadFolder.getCrtUser();
                    values[6] = ddsuploadFolder.getCreateTime();
                    values[7] = ddsuploadFolder.getTenantId();
                    values[8] = ddsuploadFolder.getOrgId();
                    values[9] = ddsuploadFolder.getStoreId();
                    int row = springJdbcTemplate.update(folderInsertSql, values);
                }
                folderList = springJdbcTemplate.queryForList(folderSelectSql, FolderTypeEnum.DES.folderName());
                if (CollectionUtil.isNotEmpty(folderList)) {
                    desuploadFolder = JSON.parseObject(JSON.toJSONString(folderList.get(0)), FmsFolder.class);
                }
                if (Objects.isNull(desuploadFolder)) {
                    desuploadFolder = new FmsFolder();
                    desuploadFolder.setFolderId(IdUtil.nanoId(6));
                    desuploadFolder.setFolderName(FolderTypeEnum.DES.folderName());
                    desuploadFolder.setObjectPath("/" + FolderTypeEnum.DES.folderName());
                    desuploadFolder.setAliases(FolderTypeEnum.DES.folderName());
                    desuploadFolder.setDeleted(false);
                    desuploadFolder.setCrtUser(userId);
                    desuploadFolder.setCreateTime(new Date());
                    desuploadFolder.setTenantId(tenantId);
                    desuploadFolder.setOrgId(orgId);
                    desuploadFolder.setStoreId(0);
                    Object[] values = new Object[10];
                    values[0] = desuploadFolder.getFolderId();
                    values[1] = desuploadFolder.getFolderName();
                    values[2] = desuploadFolder.getObjectPath();
                    values[3] = desuploadFolder.getAliases();
                    values[4] = desuploadFolder.isDeleted();
                    values[5] = desuploadFolder.getCrtUser();
                    values[6] = desuploadFolder.getCreateTime();
                    values[7] = desuploadFolder.getTenantId();
                    values[8] = desuploadFolder.getOrgId();
                    values[9] = desuploadFolder.getStoreId();
                    int row = springJdbcTemplate.update(folderInsertSql, values);
                }
                //2.分页查询
                DbContextHolder.setDBType(Constants.DEFAULT_DATA_SOURCE_NAME);
                int count = jdbcUtilServices.count(Constants.DEFAULT_DATA_SOURCE_NAME, hexaiBatchSelectSql);
                log.info("统计出 hexai系统 需要迁移的批次数据总数【" + count + "】,即将插入目标数据库");
                int pageSize = Hexai1139DataTransport.BATCH_PAGESIZE;
                //3.复制数据到指定表（多线程)
                int totalPageNum = (count + pageSize - 1) / pageSize;
                log.info("数据总数【" + count + "】,每页[" + pageSize + "],总共[" + totalPageNum + "]页,执行插入 ");
                if (count > pageSize) {
                    int start = 0;
                    for (int i = 0; i < totalPageNum; i++) {
                        log.info("第" + (i + 1) + "页");
                        start = (i) * pageSize;
                        // end = (i+1)*pageSize;
                        // mysql 的分页 String sql = sourceSql +" limit " + i * pageSize + "," + 1 * pageSize;
                        // oracle 的分页
                        String dbProductName = tableTransport.getDbName(Constants.DEFAULT_DATA_SOURCE_NAME);
                        String sql = SqlUtil.pageSql(dbProductName, hexaiBatchSelectSql, start, pageSize);
                        log.debug("分页 sql : " + sql);
                        dataCopyPoolExecutor.execute(new CopyBatchDataInWork(sql, i + 1, pageSize));
                    }
                } else {
                    log.info("第1页");
                    dataCopyPoolExecutor.execute(new CopyBatchDataInWork(hexaiBatchSelectSql, 1, pageSize));
                }
            } catch (SQLException e) {
                e.getErrorCode();
                log.error("复制数据时出错");
            }

        }


        /***
         * 具体执行的线程类
         */
        private class CopyBatchDataInWork implements Runnable {
            private String sql;
            private Integer page;
            private Integer pageSize;

            //批次扩展数据查询sql
            private String hexaiExtendSelectSql = " SELECT DI.* FROM T_AI_DDS_DOCEXTRACT_INFO DI ";

            //批次统计数据查询sql
            private String hexaiStatisticsSelectSql = " SELECT S.* FROM T_AI_DDS_STATISTICS S ";

            //文件数据查询sql
            private String hexaiFileSelectSql = " SELECT F.* FROM T_AI_FMS_FILE F ";

            private String batchInsertSql = " INSERT INTO T_AI_FMS_BATCH (BATCH_ID, BATCH_NAME,FOLDER_ID, CRT_USER, TENANT_ID, " +
                    "ORG_ID,CREATE_TIME, BATCH_TYPE, BATCH_STATUS,CHANNEL_CODE,EXT_ID) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

            private String fileInsertSql = " INSERT INTO T_AI_FMS_FILE (FILE_ID, FOLDER_ID,BATCH_ID,BATCH_TYPE,FILE_NAME," +
                    "FILE_PATHNAME,SRC_FILENAME,FILE_STATUS,CRT_USER,CREATE_TIME,TENANT_ID,ORG_ID,FILE_PATH_URL) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";

            private String statisticsInsertSql = " INSERT INTO T_AI_DDS_STATISTICS (BATCH_ID,PROCESS_TIME,DOC_PAGES,CREATE_TIME," +
                    "END_TIME,START_OCR_TIME,OCR_TYPE,CRT_USER,TENANT_ID,ORG_ID) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?)";

            private String extendInfoInsertSql = " INSERT INTO T_AI_DDS_BATCH_EXTEND_INFO (BATCH_ID,CT_CODE,EXTRACTWORDS,OCR_STAMP,REMOVE_WATERMARK_SRC," +
                    "REMOVE_WATERMARK_SCAN,REMOVE_STAMP,START_OCR_TIME) " +
                    "VALUES(?,?,?,?,?,?,?,?)";

            private CopyBatchDataInWork(String sql, Integer page, Integer pageSize) {
                this.sql = sql;
                this.page = page;
                this.pageSize = pageSize;
            }

            @Override
            public void run() {

                //log.info("线程 {"+Thread.currentThread().getName()+"},"+ JSON.toJSONString(DataTransport.dataCopyPoolExecutor));
                //1.根据源库和findSql  查询数据
                DbContextHolder.setDBType(Constants.DEFAULT_DATA_SOURCE_NAME);
                List<Map<String, Object>> hexaiBatchList = springJdbcTemplate.queryForList(sql);
                List<HexaiBatch> hexaiBatches = JSON.parseArray(JSON.toJSONString(hexaiBatchList), HexaiBatch.class);
                //2.根据数据，插入目标库
                if (CollectionUtil.isNotEmpty(hexaiBatches)) {
                    //批次迁移
                    List<FmsBatch> batchList = new ArrayList<>();
                    List<Object[]> batchValueList = new ArrayList<>();
                    hexaiExtendSelectSql += " where BATCH_ID in ( ";
                    hexaiStatisticsSelectSql += " where BATCH_ID in ( ";
                    hexaiFileSelectSql += " where BATCH_ID in ( ";
                    for (HexaiBatch hexaiBatch : hexaiBatches) {
                        hexaiExtendSelectSql += " '" + hexaiBatch.getBatchId() + "' ";
                        hexaiStatisticsSelectSql += " '" + hexaiBatch.getBatchId() + "' ";
                        hexaiFileSelectSql += " '" + hexaiBatch.getBatchId() + "' ";
                        if (hexaiBatch != hexaiBatches.get(hexaiBatches.size() - 1)) {
                            hexaiExtendSelectSql += ", ";
                            hexaiStatisticsSelectSql += ", ";
                            hexaiFileSelectSql += ", ";
                        }
                        FmsBatch batch = new FmsBatch();
                        batch.setBatchId(hexaiBatch.getBatchId());
                        batch.setBatchName(hexaiBatch.getBatchName());
                        batch.setBatchType(hexaiBatch.getBatchType());
                        batch.setBatchStatus(hexaiBatch.getBatchStatus());
                        if (OcrTypeEnum.DDS.code().equals(batch.getBatchType())) {
                            batch.setFolderId(ddsuploadFolder.getFolderId());
                        } else if (OcrTypeEnum.DES.code().equals(batch.getBatchType())) {
                            batch.setFolderId(desuploadFolder.getFolderId());
                        } else if (OcrTypeEnum.FES.code().equals(batch.getBatchType())) {
                            batch.setFolderId(fesuploadFolder.getFolderId());
                        }
                        batch.setCrtUser(userId);
                        batch.setTenantId(tenantId);
                        batch.setOrgId(orgId);
                        batch.setCreateTime(hexaiBatch.getCreateTime());
                        batch.setEndTime(hexaiBatch.getEndTime());
                        batch.setChannelCode(hexaiBatch.getChannelCode());
                        batch.setExtId(hexaiBatch.getExtId());
                        Object[] values = new Object[11];
                        values[0] = batch.getBatchId();
                        values[1] = batch.getBatchName();
                        values[2] = batch.getFolderId();
                        values[3] = batch.getCrtUser();
                        values[4] = batch.getTenantId();
                        values[5] = batch.getOrgId();
                        values[6] = batch.getCreateTime();
                        values[7] = batch.getBatchType();
                        values[8] = batch.getBatchStatus();
                        values[9] = batch.getChannelCode();
                        values[10] = batch.getExtId();
                        batchValueList.add(values);
                        batchList.add(batch);
                    }
                    hexaiExtendSelectSql += " ) ";
                    hexaiStatisticsSelectSql += " ) ";
                    hexaiFileSelectSql += " ) ";
                    //文件迁移
                    List<FmsFile> fileList = new ArrayList<>();
                    List<Object[]> fileValueList = new ArrayList<>();
                    List<Map<String, Object>> hexaiFileMapList = springJdbcTemplate.queryForList(hexaiFileSelectSql);
                    List<HexaiFile> hexaiFileList = JSON.parseArray(JSON.toJSONString(hexaiFileMapList), HexaiFile.class);
                    for (HexaiFile hexaiFile : hexaiFileList) {
                        FmsFile file = new FmsFile();
                        file.setFileId(hexaiFile.getFileId());
                        file.setBatchId(hexaiFile.getBatchId());
                        FmsBatch batchValue = batchList.stream().filter(batch -> batch.getBatchId().equals(hexaiFile.getBatchId())).findFirst().orElse(null);
                        file.setFolderId(batchValue.getFolderId());
                        file.setBatchType(batchValue.getBatchType());
                        file.setFileName(hexaiFile.getFileName());
                        file.setFilePathName(hexaiFile.getFilePathname());
                        file.setSrcFileName(hexaiFile.getSrcFilename());
                        file.setFileStatus(hexaiFile.getFileStatus());
                        file.setCrtUser(userId);
                        file.setTenantId(tenantId);
                        file.setOrgId(orgId);
                        file.setCreateTime(hexaiFile.getCreateTime());
                        String hexaiPathUrl = hexaiFile.getFilePathUrl();
                        String suffix = "";
                        if (OcrTypeEnum.DDS.code().equals(file.getBatchType())) {
                            suffix = hexaiPathUrl.substring(hexaiPathUrl.indexOf(FolderTypeEnum.DDS.folderName()));
                        } else if (OcrTypeEnum.DES.code().equals(file.getBatchType())) {
                            suffix = hexaiPathUrl.substring(hexaiPathUrl.indexOf(FolderTypeEnum.DES.folderName()));
                        } else if (OcrTypeEnum.FES.code().equals(file.getBatchType())) {
                            suffix = hexaiPathUrl.substring(hexaiPathUrl.indexOf(FolderTypeEnum.FES.folderName()));
                        }
                        file.setFilePathUrl(store.getStoreUrl() + File.separator + suffix);
                        Object[] values = new Object[13];
                        values[0] = file.getFileId();
                        values[1] = file.getFolderId();
                        values[2] = file.getBatchId();
                        values[3] = file.getBatchType();
                        values[4] = file.getFileName();
                        values[5] = file.getFilePathName();
                        values[6] = file.getSrcFileName();
                        values[7] = file.getFileStatus();
                        values[8] = file.getCrtUser();
                        values[9] = file.getCreateTime();
                        values[10] = file.getTenantId();
                        values[11] = file.getOrgId();
                        values[12] = file.getFilePathUrl();
                        fileValueList.add(values);
                        fileList.add(file);
                    }
                    //扩展数据迁移
                    List<FmsBatchExtendInfo> extendInfoList = new ArrayList<>();
                    List<Object[]> extendInfoValueList = new ArrayList<>();
                    List<Map<String, Object>> hexaiExtendInfoMapList = springJdbcTemplate.queryForList(hexaiExtendSelectSql);
                    for (FmsBatch batch : batchList) {
                        List<HexaiDocextractInfo> hexaiDocextractInfoList = Collections.emptyList();
                        if (CollectionUtil.isNotEmpty(hexaiExtendInfoMapList)) {
                            hexaiDocextractInfoList = JSON.parseArray(JSON.toJSONString(hexaiExtendInfoMapList), HexaiDocextractInfo.class);
                        }
                        HexaiDocextractInfo hexaiDocextractInfo = hexaiDocextractInfoList.stream().filter(hdi -> hdi.getBatchId().equals(batch.getBatchType())).findFirst().orElse(null);
                        FmsBatchExtendInfo extendInfo = new FmsBatchExtendInfo();
                        extendInfo.setBatchId(batch.getBatchId());
                        if (Objects.nonNull(hexaiDocextractInfo)) {
                            extendInfo.setCtCode(hexaiDocextractInfo.getCtCode());
                            extendInfo.setExtractwords(hexaiDocextractInfo.getExtractwords());
                        }
                        extendInfo.setStartOcrTime(batch.getCreateTime());
                        extendInfo.setOcrStamp(String.valueOf(false));
                        extendInfo.setRemoveWatermarkScan(String.valueOf(false));
                        extendInfo.setRemoveWatermarkSrc(String.valueOf(false));
                        extendInfo.setRemoveStamp(String.valueOf(false));
                        Object[] values = new Object[8];
                        values[0] = extendInfo.getBatchId();
                        values[1] = extendInfo.getCtCode();
                        values[2] = extendInfo.getExtractwords();
                        values[3] = extendInfo.getOcrStamp();
                        values[4] = extendInfo.getRemoveWatermarkSrc();
                        values[5] = extendInfo.getRemoveWatermarkScan();
                        values[6] = extendInfo.getRemoveStamp();
                        values[7] = extendInfo.getStartOcrTime();
                        extendInfoValueList.add(values);
                        extendInfoList.add(extendInfo);
                    }
                    //统计数据迁移
                    List<FmsStatistics> statisticsList = new ArrayList<>();
                    List<Object[]> statisticsValueList = new ArrayList<>();
                    List<Map<String, Object>> hexaiStatisticsMapList = springJdbcTemplate.queryForList(hexaiStatisticsSelectSql);
                    for (FmsBatch batch : batchList) {
                        List<HexaiStatistics> hexaiStatisticsList = Collections.emptyList();
                        if (CollectionUtil.isNotEmpty(hexaiStatisticsMapList)) {
                            hexaiStatisticsList = JSON.parseArray(JSON.toJSONString(hexaiStatisticsMapList), HexaiStatistics.class);
                        }
                        HexaiStatistics hexaiStatistics = hexaiStatisticsList.stream().filter(hdi -> hdi.getBatchId().equals(batch.getBatchType())).findFirst().orElse(null);
                        FmsStatistics statistics = new FmsStatistics();
                        statistics.setBatchId(batch.getBatchId());
                        statistics.setProcessTime(0);
                        statistics.setDocPages(0);
                        statistics.setCreateTime(batch.getCreateTime());
                        statistics.setEndTime(batch.getEndTime());
                        statistics.setStartOcrTime(batch.getCreateTime());
                        statistics.setOcrType(batch.getBatchType());
                        statistics.setCrtUser(userId);
                        statistics.setTenantId(tenantId);
                        statistics.setOrgId(orgId);
                        if (Objects.nonNull(hexaiStatistics)) {
                            statistics.setProcessTime(hexaiStatistics.getProcessTime());
                            statistics.setDocPages(hexaiStatistics.getDocPages());
                            statistics.setCreateTime(hexaiStatistics.getCreateTime());
                            statistics.setEndTime(hexaiStatistics.getEndTime());
                        }
                        Object[] values = new Object[10];
                        values[0] = statistics.getBatchId();
                        values[1] = statistics.getProcessTime();
                        values[2] = statistics.getDocPages();
                        values[3] = statistics.getCreateTime();
                        values[4] = statistics.getEndTime();
                        values[5] = statistics.getStartOcrTime();
                        values[6] = statistics.getOcrType();
                        values[7] = statistics.getCrtUser();
                        values[8] = statistics.getTenantId();
                        values[9] = statistics.getOrgId();
                        statisticsValueList.add(values);
                        statisticsList.add(statistics);
                    }


                    log.info("当前页码 {} ，开始执行插入", page);
                    if (CollectionUtil.isNotEmpty(batchValueList)) {
                        jdbcUtilServices.batchInsert(targetNames, "FMS_BATCH", batchInsertSql, batchValueList);
                    }
                    if (CollectionUtil.isNotEmpty(fileValueList)) {
                        jdbcUtilServices.batchInsert(targetNames, "FMS_FILE", fileInsertSql, fileValueList);
                    }
                    if (CollectionUtil.isNotEmpty(extendInfoValueList)) {
                        jdbcUtilServices.batchInsert(targetNames, "T_AI_DDS_BATCH_EXTEND_INFO", extendInfoInsertSql, extendInfoValueList);
                    }
                    if (CollectionUtil.isNotEmpty(statisticsValueList)) {
                        jdbcUtilServices.batchInsert(targetNames, "T_AI_DDS_STATISTICS", statisticsInsertSql, statisticsValueList);
                    }

                    //服务器文件数据迁移
                    log.info("第{}页批次服务器文件迁移", page);
                    for (FmsBatch batch : batchList) {
                        String batchSuffixPath = "";
                        String exportSuffixPath = "";
                        if (OcrTypeEnum.DDS.code().equals(batch.getBatchType())) {
                            batchSuffixPath = FolderTypeEnum.DDS.folderName() + File.separator + DatePattern.PURE_DATE_FORMAT.format(batch.getCreateTime()) + File.separator + batch.getBatchId();
                            exportSuffixPath = FolderTypeEnum.DDS.exportFolder() + File.separator + DatePattern.PURE_DATE_FORMAT.format(batch.getCreateTime()) + File.separator + batch.getBatchId();
                        } else if (OcrTypeEnum.DES.code().equals(batch.getBatchType())) {
                            batchSuffixPath = FolderTypeEnum.DES.folderName() + File.separator + DatePattern.PURE_DATE_FORMAT.format(batch.getCreateTime()) + File.separator + batch.getBatchId();
                            exportSuffixPath = FolderTypeEnum.DES.exportFolder() + File.separator + DatePattern.PURE_DATE_FORMAT.format(batch.getCreateTime()) + File.separator + batch.getBatchId();
                        } else if (OcrTypeEnum.FES.code().equals(batch.getBatchType())) {
                            batchSuffixPath = FolderTypeEnum.FES.folderName() + File.separator + DatePattern.PURE_DATE_FORMAT.format(batch.getCreateTime()) + File.separator + batch.getBatchId();
                            exportSuffixPath = FolderTypeEnum.FES.exportFolder() + File.separator + DatePattern.PURE_DATE_FORMAT.format(batch.getCreateTime()) + File.separator + batch.getBatchId();
                        }
                        File copyBatchPathUrl = Paths.get(fileRootDir, batchSuffixPath).toFile();
                        if (copyBatchPathUrl.exists()) {
                            File targetBatchPathUrl = Paths.get(store.getStoreUrl(), batchSuffixPath).toFile();
                            FileUtil.move(copyBatchPathUrl, targetBatchPathUrl, true);
                            File copyExportPathUrl = Paths.get(fileRootDir, exportSuffixPath).toFile();
                            File targetExportPathUrl = Paths.get(store.getStoreUrl(), exportSuffixPath).toFile();
                            FileUtil.move(copyExportPathUrl, targetExportPathUrl, true);
                            log.info("upload文件迁移记录：{}  -->  {}", copyBatchPathUrl.getAbsolutePath(), targetBatchPathUrl.getAbsolutePath());
                            log.info("export文件迁移记录：{}  -->  {}", copyExportPathUrl.getAbsolutePath(), targetExportPathUrl.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    //处理提取模板数据迁移
    //t_ai_contract_template,t_ai_contract_template_factror,t_ai_contract_factor-->t_ai_dds_template,t_ai_dds_factor
    private class ContractTemplateDataWork implements Runnable {

        //模板数据查询sql
        private String hexaiTemplateSelectSql = " SELECT ct.CT_NO, ct.CT_CODE, ct.CT_NAME, ct.CT_DESC, ct.CREATE_TIME, " +
                "cf.CF_NO, cf.CF_NAME, cf.CF_KEYWORD, cf.CF_REMARK, cf.CF_POSITION, cf.CF_RANGE, cf.CF_RULE, cf.CF_COLOR " +
                "FROM T_AI_CONTRACT_FACTOR cf  " +
                "LEFT JOIN T_AI_CONTRACT_TEMPLATE_FACTROR ctf on ctf.CF_NO = cf.CF_NO " +
                "LEFT JOIN T_AI_CONTRACT_TEMPLATE ct on ct.CT_NO = ctf.CT_NO ";


        @Override
        public void run() {
            log.info("————————————开始模板数据迁移————————————");
            try {
                //分页查询
                DbContextHolder.setDBType(Constants.DEFAULT_DATA_SOURCE_NAME);
                int count = jdbcUtilServices.count(Constants.DEFAULT_DATA_SOURCE_NAME, " SELECT * FROM T_AI_CONTRACT_FACTOR ");
                log.info("统计出 hexai系统 需要迁移的要素数据总数【" + count + "】,即将插入目标数据库");
                int pageSize = Hexai1139DataTransport.BATCH_PAGESIZE;
                //3.复制数据到指定表（多线程)
                int totalPageNum = (count + pageSize - 1) / pageSize;
                log.info("数据总数【" + count + "】,每页[" + pageSize + "],总共[" + totalPageNum + "]页,执行插入 ");
                if (count > pageSize) {
                    int start = 0;
                    for (int i = 0; i < totalPageNum; i++) {
                        log.info("第" + (i + 1) + "页");
                        start = (i) * pageSize;
                        // end = (i+1)*pageSize;
                        // mysql 的分页 String sql = sourceSql +" limit " + i * pageSize + "," + 1 * pageSize;
                        // oracle 的分页
                        String dbProductName = tableTransport.getDbName(Constants.DEFAULT_DATA_SOURCE_NAME);
                        String sql = SqlUtil.pageSql(dbProductName, hexaiTemplateSelectSql, start, pageSize);
                        log.debug("分页 sql : " + sql);
                        dataCopyPoolExecutor.execute(new CopyTemplateDataInWork(sql, i + 1, pageSize));
                    }
                } else {
                    log.info("第1页");
                    dataCopyPoolExecutor.execute(new CopyTemplateDataInWork(hexaiTemplateSelectSql, 1, pageSize));
                }
            } catch (SQLException e) {
                e.getErrorCode();
                log.error("复制数据时出错");
            }

        }


        /***
         * 具体执行的线程类
         */
        private class CopyTemplateDataInWork implements Runnable {
            private String sql;
            private Integer page;
            private Integer pageSize;

            private String templateInsertsql = "insert into T_AI_DDS_TEMPLATE (CT_NO, CT_CODE, CT_NAME, CT_DESC, CRT_USER, " +
                    " TENANT_ID, ORG_ID, CREATE_TIME,  UPDATE_TIME)  " +
                    "values(?,?,?,?,?,?,?,?,?)";
            private String factorInsertMysql = " insert into T_AI_DDS_FACTOR (CF_NO, CT_NO, CF_NAME, CF_KEYWORD, " +
                    "CF_REMARK, CF_POSITION, CF_RANGE, CF_RULE, CF_COLOR, CRT_USER, TENANT_ID,ORG_ID, CREATE_TIME, UPDATE_TIME ) " +
                    " values((SELECT * FROM (SELECT IF(MAX(CF_NO),MAX(CF_NO)+1,1) FROM T_AI_DDS_FACTOR) temp),?,?,?,?,?,?,?,?,?,?,?,?,?) ";

            private String factorInsertOracle = " insert into T_AI_DDS_FACTOR (CF_NO, CT_NO, CF_NAME, CF_KEYWORD, " +
                    "CF_REMARK, CF_POSITION, CF_RANGE, CF_RULE, CF_COLOR, CRT_USER, TENANT_ID,ORG_ID, CREATE_TIME, UPDATE_TIME ) " +
                    " values((SELECT * FROM (SELECT DECODE(MAX(CF_NO),null,1,MAX(CF_NO)+1) FROM T_AI_DDS_FACTOR) temp),?,?,?,?,?,?,?,?,?,?,?,?,?) ";

            private String updateNextIdSql = "UPDATE GF_NEXT_ID SET NEXT_ID= (SELECT MAX(CF_NO)+1 FROM T_AI_DDS_FACTOR), LAST_TIME= ? WHERE SEQ_TYPE = 'DDS.FACTOR'";
            private String insertNextIdSql = "INSERT INTO GF_NEXT_ID(SEQ_TYPE,NEXT_ID,LAST_TIME) SELECT 'DDS.FACTOR', (SELECT MAX(CF_NO)+1 FROM T_AI_DDS_FACTOR), ? FROM dual WHERE NOT EXISTS (SELECT 1 FROM GF_NEXT_ID WHERE SEQ_TYPE = 'DDS.FACTOR')";

            private CopyTemplateDataInWork(String sql, Integer page, Integer pageSize) {
                this.sql = sql;
                this.page = page;
                this.pageSize = pageSize;
            }

            @Override
            public void run() {

                //log.info("线程 {"+Thread.currentThread().getName()+"},"+ JSON.toJSONString(DataTransport.dataCopyPoolExecutor));
                //1.根据源库和findSql  查询数据
                DbContextHolder.setDBType(Constants.DEFAULT_DATA_SOURCE_NAME);
                List<Map<String, Object>> hexaiTemplateFactorMapList = springJdbcTemplate.queryForList(sql);
                List<HexaiContractTemplateFactror> hexaiContractTemplateFactrors = JSON.parseArray(JSON.toJSONString(hexaiTemplateFactorMapList), HexaiContractTemplateFactror.class);
                //2.根据数据，插入目标库
                if (CollectionUtil.isNotEmpty(hexaiContractTemplateFactrors)) {
                    //模板数据迁移
                    List<Template> templateList = new ArrayList<>();
                    List<Factor> factorList = new ArrayList<>();
                    List<Object[]> templateValueList = new ArrayList<>();
                    List<Object[]> factorValueList = new ArrayList<>();
                    hexaiContractTemplateFactrors = hexaiContractTemplateFactrors.stream().filter(ctf -> StrUtil.isNotBlank(ctf.getCtNo())&& !"defaultId".equals(ctf.getCtNo())).collect(Collectors.toList());
                    for (HexaiContractTemplateFactror hexaiCTF : hexaiContractTemplateFactrors) {
                        long count = templateList.stream().filter(t -> t.getCtNo().equals(hexaiCTF.getCtNo())).count();
                        if (count <= 0) {
                            Template template = new Template();
                            template.setCtNo(hexaiCTF.getCtNo());
                            template.setCtCode(hexaiCTF.getCtCode());
                            template.setCtName(hexaiCTF.getCtName());
                            template.setCtDesc(hexaiCTF.getCtDesc());
                            template.setCrtUser(userId);
                            template.setTenantId(tenantId);
                            template.setOrgId(orgId);
                            template.setCreateTime(hexaiCTF.getCreateTime());
                            template.setUpdateTime(hexaiCTF.getCreateTime());
                            Object[] values = new Object[9];
                            values[0] = template.getCtNo();
                            values[1] = template.getCtCode();
                            values[2] = template.getCtName();
                            values[3] = template.getCtDesc();
                            values[4] = template.getCrtUser();
                            values[5] = template.getTenantId();
                            values[6] = template.getOrgId();
                            values[7] = template.getCreateTime();
                            values[8] = template.getUpdateTime();
                            templateValueList.add(values);
                            templateList.add(template);
                        }
                        Factor factor = new Factor();
                        factor.setCtNo(hexaiCTF.getCtNo());
                        factor.setCfName(hexaiCTF.getCfName());
                        factor.setCfKeyword(hexaiCTF.getCfKeyword());
                        factor.setCfRemark(hexaiCTF.getCfRemark());
                        factor.setCfPosition(hexaiCTF.getCfPosition());
                        factor.setCfRange(hexaiCTF.getCfRange());
                        factor.setCfRule(hexaiCTF.getCfRule());
                        factor.setCfColor(hexaiCTF.getCfColor());
                        factor.setCrtUser(userId);
                        factor.setTenantId(tenantId);
                        factor.setOrgId(orgId);
                        factor.setCreateTime(hexaiCTF.getCreateTime());
                        factor.setUpdateTime(hexaiCTF.getCreateTime());
                        Object[] values = new Object[13];
                        values[0] = factor.getCtNo();
                        values[1] = factor.getCfName();
                        values[2] = factor.getCfKeyword();
                        values[3] = factor.getCfRemark();
                        values[4] = factor.getCfPosition();
                        values[5] = factor.getCfRange();
                        values[6] = factor.getCfRule();
                        values[7] = factor.getCfColor();
                        values[8] = factor.getCrtUser();
                        values[9] = factor.getTenantId();
                        values[10] = factor.getOrgId();
                        values[11] = factor.getCreateTime();
                        values[12] = factor.getUpdateTime();
                        factorValueList.add(values);
                        factorList.add(factor);
                    }

                    log.info("当前页码 {} ，开始执行插入", page);
                    if (CollectionUtil.isNotEmpty(templateValueList)) {
                        jdbcUtilServices.batchInsert(targetNames, "T_AI_DDS_TEMPLATE", templateInsertsql, templateValueList);
                    }
                    if (CollectionUtil.isNotEmpty(factorValueList)) {
                        String sql = "";
                        try {
                            String dbProductName = tableTransport.getDbName(targetNames);
                            switch (dbProductName.toLowerCase()) {
                                case "oracle":
                                    sql = factorInsertOracle;
                                    break;
                                case "mysql":
                                    sql = factorInsertMysql;
                                    break;
                                default:
                            }
                        } catch (Exception e) {
                        }
                        for (Object[] values : factorValueList) {
                            springJdbcTemplate.update(sql,values);
                        }
                        Date now = new Date();
                        springJdbcTemplate.update(updateNextIdSql,now);
                        springJdbcTemplate.update(insertNextIdSql, now);
                    }
                }
            }
        }
    }
}
