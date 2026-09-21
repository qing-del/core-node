package com.jacolp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling // 必须添加此注解开启定时任务
public class CoreNodeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreNodeServerApplication.class, args);
    }

}
