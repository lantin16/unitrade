package com.lantin.unitrade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@MapperScan("com.lantin.unitrade.mapper")   // 扫描mapper接口
@EnableAspectJAutoProxy(exposeProxy = true)  // 开启AOP代理
@EnableTransactionManagement //开启注解方式的事务管理
@SpringBootApplication
public class UnitradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnitradeApplication.class, args);
    }

}
