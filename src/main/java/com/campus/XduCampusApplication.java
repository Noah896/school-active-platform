package com.campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.campus.mapper")
@EnableScheduling
@SpringBootApplication
public class XduCampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(XduCampusApplication.class, args);
    }

}
