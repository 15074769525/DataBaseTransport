package com.maywide.dbt.core.execute;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.maywide.dbt.config.datasource.dynamic.Constants;
import com.maywide.dbt.config.datasource.dynamic.DbContextHolder;
import com.maywide.dbt.core.pojo.hexai.EcmDoc;
import com.maywide.dbt.core.pojo.hexai.EcmFile;
import com.maywide.dbt.core.pojo.jarvis.FmsBatch;
import com.maywide.dbt.core.pojo.jarvis.FmsFile;
import com.maywide.dbt.core.pojo.jarvis.FmsFolder;
import com.maywide.dbt.core.services.JdbcUtilServices;
import com.maywide.dbt.util.SpringJdbcTemplate;
import com.maywide.dbt.util.SqlUtil;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class HexaiDataTransport {

    @Value("target.mysql.datasource.names")
    private String targetNames;

    private static final Logger log = LoggerFactory.getLogger(HexaiDataTransport.class);
    public static final int WORK_QUE_SIZE = 3000;
    public static final int BATCH_PAGESIZE = 5000;

    public static ConcurrentHashMap<String, JSONObject> successMap = new ConcurrentHashMap<>();

    private static final AtomicLong along = new AtomicLong(0);

    public static ThreadPoolExecutor dataCopyPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 10 + 1, Runtime.getRuntime().availableProcessors() * 15 + 1, 30, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(HexaiDataTransport.WORK_QUE_SIZE), new ThreadPoolExecutor.CallerRunsPolicy());

    @Autowired
    private SpringJdbcTemplate springJdbcTemplate;

    @Autowired
    private JdbcUtilServices jdbcUtilServices;

    @Autowired
    private TableTransport tableTransport;

    @Value("file.rootDir:/hexdata/exdoc")
    private String fileRootDir;

    @Value("userId:JARVIS")
    private String userId;

    @Value("orgId:19")
    private String orgId;

    @Value("tenantId")
    private String tenantId;


    public void startCopyData() {
        successMap = new ConcurrentHashMap<>();
        new Thread(new BatchDataWork()).start();
    }

    //处理批次数据迁移
    //ecm_doc->t_ai_fms_batch
    //ecm_file->t_ai_fms_file
    //t_ai_dds_docdiff_log->t_ai_dds_statistics
    //t_ai_dds_batch_extend_info
    private class BatchDataWork implements Runnable {
        private AtomicInteger ai = new AtomicInteger(0);

        //ocr类型匹配
        //7，合同比对；13，合同提取；15，全文识别
        private List<Integer> ocrFlags = Arrays.asList(7, 13, 15);

        //批次数据查询sql
        private String ecmDocSelectSql = " select * from ecm_doc";

        //批次目录查询sql
        private String folderSelectSql = " SELECT * FROM T_AI_FMS_FOLDER WHERE FOLDER_NAME = ? AND DELETED!='1' ";
        private String folderInsertSql = " INSERT INTO T_AI_FMS_FOLDER (FOLDER_ID, FOLDER_NAME," +
                "OBJECT_PATH,ALIASES, DELETED, CRT_USER,CREATE_TIME,TENANT_ID,ORG_ID,STORE_ID) " +
                "VALUES (?,?,?,?,?,?,?,?,?) ";

        private FmsFolder fesuploadFolder;
        private FmsFolder ddsuploadFolder;
        private FmsFolder desuploadFolder;

        {
            //追加过滤条件
            if (CollectionUtil.isNotEmpty(ocrFlags)) {
                String condition = " where ocrflag in (";
                for (int i = 0; i < ocrFlags.size(); i++) {
                    condition = condition + ocrFlags.get(i);
                    if (i != ocrFlags.size() - 1) {
                        condition = condition + ",";
                    }
                }
                condition = condition + ") ";
                ecmDocSelectSql = ecmDocSelectSql + condition;
            }
        }

        public BatchDataWork() {
        }

        @Override
        public void run() {
            try {
                //1.初始化批次目录
                DbContextHolder.setDBType(targetNames);
                fesuploadFolder = springJdbcTemplate.queryForObject(folderSelectSql, FmsFolder.class, "fesupload");
                if (Objects.isNull(fesuploadFolder)) {
                    fesuploadFolder = new FmsFolder();
                    fesuploadFolder.setFolderId(IdUtil.nanoId(6));
                    fesuploadFolder.setFolderName("fesupload");
                    fesuploadFolder.setObjectPath("/fesupload");
                    fesuploadFolder.setAliases("fesupload");
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
                ddsuploadFolder = springJdbcTemplate.queryForObject(folderSelectSql, FmsFolder.class, "ddsupload");
                if (Objects.isNull(ddsuploadFolder)) {
                    ddsuploadFolder = new FmsFolder();
                    ddsuploadFolder.setFolderId(IdUtil.nanoId(6));
                    ddsuploadFolder.setFolderName("ddsupload");
                    ddsuploadFolder.setObjectPath("/ddsupload");
                    ddsuploadFolder.setAliases("ddsupload");
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
                desuploadFolder = springJdbcTemplate.queryForObject(folderSelectSql, FmsFolder.class, "desupload");
                if (Objects.isNull(desuploadFolder)) {
                    desuploadFolder = new FmsFolder();
                    desuploadFolder.setFolderId(IdUtil.nanoId(6));
                    desuploadFolder.setFolderName("desupload");
                    desuploadFolder.setObjectPath("/desupload");
                    desuploadFolder.setAliases("desupload");
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
                int count = jdbcUtilServices.count(Constants.DEFAULT_DATA_SOURCE_NAME, ecmDocSelectSql);
                log.info("统计出 ecm_doc 需要迁移的数据总数【" + count + "】,即将插入目标数据库");
                int pageSize = HexaiDataTransport.BATCH_PAGESIZE;
                //3.复制数据到指定表（多线程)
                int totalPageNum = (count + pageSize - 1) / pageSize;
                log.info("ecm_doc 数据总数【" + count + "】,每页[" + pageSize + "],总共[" + totalPageNum + "]页,执行插入 ");
                if (count > pageSize) {
                    int start = 0;
                    for (int i = 0; i < totalPageNum; i++) {
                        log.info("第" + (i + 1) + "页");
                        start = (i) * pageSize;
                        // end = (i+1)*pageSize;
                        // mysql 的分页 String sql = sourceSql +" limit " + i * pageSize + "," + 1 * pageSize;
                        // oracle 的分页
                        String dbProductName = tableTransport.getDbName(Constants.DEFAULT_DATA_SOURCE_NAME);
                        String sql = SqlUtil.pageSql(dbProductName, ecmDocSelectSql, start, pageSize);
                        log.debug("分页 sql : " + sql);
                        dataCopyPoolExecutor.execute(new CopyBatchDataInWork(sql, i + 1, pageSize));
                    }
                } else {
                    log.info("第1页");
                    dataCopyPoolExecutor.execute(new CopyBatchDataInWork(ecmDocSelectSql, 1, pageSize));
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

            //文件数据查询sql
            private String ecmFileSelectSql = " select * from ecm_file";

            private String batchInsertSql = " INSERT INTO T_AI_FMS_BATCH (BATCH_ID, BATCH_NAME,FOLDER_ID, CRT_USER, TENANT_ID, " +
                    "ORG_ID,CREATE_TIME, BATCH_TYPE, BATCH_STATUS,EXT_ID) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)";

            private String fileInsertSql = " INSERT INTO T_AI_FMS_FILE (FILE_ID, FOLDER_ID,BATCH_ID,BATCH_TYPE,FILE_NAME," +
                    "FILE_PATHNAME,SRC_FILENAME,FILE_STATUS,CRT_USER,CREATE_TIME,TENANT_ID,ORG_ID,FILE_PATH_URL) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";

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
                List<EcmDoc> ecmDocList = springJdbcTemplate.queryForList(sql, EcmDoc.class);
                //2.根据数据，插入目标库
                if (CollectionUtil.isNotEmpty(ecmDocList)) {
                    List<FmsBatch> batchList = new ArrayList<>();
                    List<Object[]> batchValueList = new ArrayList<>();
                    ecmFileSelectSql += " where doc_id in ( ";
                    for (EcmDoc ecmDoc : ecmDocList) {
                        ecmFileSelectSql += " '" + ecmDoc.getObjectId() + "' ";
                        if (ecmDoc != ecmDocList.get(ecmDocList.size() - 1)) {
                            ecmFileSelectSql += ", ";
                        }
                        FmsBatch batch = new FmsBatch();
                        batch.setBatchId(ecmDoc.getObjectId());
                        batch.setBatchName(ecmDoc.getName());
                        batch.setBatchType(ecmDoc.mappingBatchType());
                        batch.setBatchStatus(ecmDoc.mappingBatchStatus());
                        if ("7".equals(ecmDoc.getOcrflag() + "")) {
                            batch.setFolderId(ddsuploadFolder.getFolderId());
                            if (StrUtil.isNotBlank(ecmDoc.getClabel())) {
                                batch.setBatchName(batch.getBatchName() + "|" + ecmDoc.getClabel());
                            }
                        } else if ("13".equals(ecmDoc.getOcrflag() + "")) {
                            batch.setFolderId(desuploadFolder.getFolderId());
                        } else if ("15".equals(ecmDoc.getOcrflag() + "")) {
                            batch.setFolderId(fesuploadFolder.getFolderId());
                        }
                        batch.setCrtUser(userId);
                        batch.setTenantId(tenantId);
                        batch.setOrgId(orgId);
                        batch.setCreateTime(ecmDoc.getCreateTime());
                        batch.setEndTime(ecmDoc.getUpdateTime());
                        batch.setExtId(IdUtil.simpleUUID());
                        Object[] values = new Object[10];
                        values[0] = batch.getBatchId();
                        values[1] = batch.getBatchName();
                        values[2] = batch.getFolderId();
                        values[3] = batch.getCrtUser();
                        values[4] = batch.getTenantId();
                        values[5] = batch.getOrgId();
                        values[6] = batch.getCreateTime();
                        values[7] = batch.getBatchType();
                        values[8] = batch.getBatchStatus();
                        values[9] = batch.getExtId();
                        batchValueList.add(values);
                        batchList.add(batch);
                    }
                    ecmFileSelectSql += " ) ";
                    List<FmsFile> fileList = new ArrayList<>();
                    List<Object[]> fileValueList = new ArrayList<>();
                    List<EcmFile> ecmFileList = springJdbcTemplate.queryForList(ecmFileSelectSql, EcmFile.class);
                    for (EcmFile ecmFile : ecmFileList) {
                        FmsFile file = new FmsFile();
                        file.setFileId(ecmFile.getObjectId());
                        file.setBatchId(ecmFile.getDocId());
                        FmsBatch batchValue = batchList.stream().filter(batch -> batch.getBatchId().equals(ecmFile.getDocId())).findFirst().orElse(null);
                        file.setFolderId(batchValue.getFolderId());
                        file.setBatchType(batchValue.getBatchType());
                        file.setFileName(ecmFile.getName());
                        file.setFilePathName("src");
                        file.setSrcFileName(ecmFile.getName());
                        file.setFileStatus(0);
                        file.setCrtUser(userId);
                        file.setTenantId(tenantId);
                        file.setOrgId(orgId);
                        file.setCreateTime(ecmFile.getCreateTime());
                        file.setFilePathUrl(ecmFile.getContentUrl());
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
                    if (CollectionUtil.isNotEmpty(batchValueList)){
                        jdbcUtilServices.batchInsert(targetNames, Constants.DEFAULT_DATA_SOURCE_NAME, batchInsertSql,batchValueList);
                    }
                    if (CollectionUtil.isNotEmpty(fileValueList)){
                        jdbcUtilServices.batchInsert(targetNames, Constants.DEFAULT_DATA_SOURCE_NAME, fileInsertSql,fileValueList);
                    }
                }
            }
        }
    }
}
