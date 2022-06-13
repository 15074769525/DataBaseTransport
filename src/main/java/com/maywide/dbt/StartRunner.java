package com.maywide.dbt;

import com.maywide.dbt.core.execute.HexaiDataTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动程序执行代码
 */
@Component
public class StartRunner implements CommandLineRunner {

    @Autowired
    private HexaiDataTransport hexaiDataTransport;

    @Override
    public void run(String... args) throws Exception {
        hexaiDataTransport.startCopyData();
    }
}