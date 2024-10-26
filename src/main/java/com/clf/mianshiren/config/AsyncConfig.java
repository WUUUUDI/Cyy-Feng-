package com.clf.mianshiren.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 创建线程池
 * @author clf
 * @version 1.0
 */
@Configuration
public class AsyncConfig {

    /**
     * 处理批量插入的线程池
     * @return
     */
    @Bean(name = "batchAddExecutor")
    public ThreadPoolExecutor batchAddExecutor() {

        return new ThreadPoolExecutor(
                4,
                8,
                1,
                TimeUnit.HOURS,
                new ArrayBlockingQueue<>(50),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

}
