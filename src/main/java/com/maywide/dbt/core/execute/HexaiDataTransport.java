package com.maywide.dbt.core.execute;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.maywide.dbt.config.datasource.dynamic.Constants;
import com.maywide.dbt.config.datasource.dynamic.DbContextHolder;
import com.maywide.dbt.core.pojo.hexai.EcmDoc;
import com.maywide.dbt.core.pojo.jarvis.FmsBatch;
import com.maywide.dbt.core.pojo.oracle.TableInfo;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

//    public static ThreadPoolExecutor dataCopyPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2 + 1, Runtime.getRuntime().availableProcessors() * 3 + 1, 30, TimeUnit.SECONDS,
//            new LinkedBlockingDeque<>(DataTransport.WORK_QUE_SIZE),new ThreadPoolExecutor.CallerRunsPolicy());

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


    public void startCopyData(String oriDatasource, String schema, List<String> targetDBlist, String tableName) {
        if (null == targetDBlist || targetDBlist.isEmpty()) {
            log.error("targetDBlist 为空 ");
            return;
        }
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
                //1.判断表在否（不在的话,就调用tableTransprort 复制表）
                //2.查询源数据
                int count = jdbcUtilServices.count(Constants.DEFAULT_DATA_SOURCE_NAME, ecmDocSelectSql);
                log.info("统计出 ecm_doc 需要迁移的数据总数【" + count + "】,即将插入目标数据库");

                int pageSize = HexaiDataTransport.BATCH_PAGESIZE;
                //3.复制数据到指定表（多线程)
                int totalPageNum = (count + pageSize - 1) / pageSize;
                log.info("ecm_doc 数据总数【" + count + "】,每页[" + pageSize + "],总共[" + totalPageNum + "]页,执行插入 ");
                if (count > pageSize) {
                    int start = 0;
                    for (int i = 0; i < totalPageNum; i++) {
                        log.info("第"+(i+1)+"页");
                        start = (i) * pageSize;
                        // end = (i+1)*pageSize;
                        // mysql 的分页 String sql = sourceSql +" limit " + i * pageSize + "," + 1 * pageSize;
                        // oracle 的分页
                        String dbProductName = tableTransport.getDbName(Constants.DEFAULT_DATA_SOURCE_NAME);
                        String sql = SqlUtil.pageSql(dbProductName, ecmDocSelectSql, start, pageSize);
                        log.debug("分页 sql : " + sql);
                        dataCopyPoolExecutor.execute(new CopyBatchDataInWork(sql,i+1,pageSize));
                    }
                } else {
                    log.info("第1页");
                    dataCopyPoolExecutor.execute(new CopyBatchDataInWork(ecmDocSelectSql,1,pageSize));
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

            private CopyBatchDataInWork(String sql,Integer page,Integer pageSize) {
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
                    List<FmsBatch> batchList=new ArrayList<>();
                    for (EcmDoc ecmDoc : ecmDocList) {
                        FmsBatch batch = new FmsBatch();
                        batch.setBatchId(ecmDoc.getObjectId());
                        batch.setBatchName(ecmDoc.getName());
                        batchList.add(batch);
                    }
                }

                // TODO 单个目标库 jdbcUtilServices.batchInsert(targetDatasource,tableName,valueList);
                int nowSuc = ai.addAndGet(valueList.size());
                for (String targetDb : targetDBlist) {
                    long startTime = System.currentTimeMillis();
                    jdbcUtilServices.batchInsert(targetDb, tableName, valueList);
                    log.info("表{" + tableName + "} 从 库{" + oriDatasource + "} 到 目标库{" + targetDb + "} 目标的成功数为 " + nowSuc);
                    log.debug("写统计信息---开始");
                    JSONObject oneResult = new JSONObject();
                    oneResult.put("spend_time", (System.currentTimeMillis() - startTime) / 1000);
                    oneResult.put("deal_count", nowSuc);
                    successMap.put(targetDb + "." + tableName, oneResult);
                    log.debug("写统计信息---完成");
                }
            }
        }
    }
}
