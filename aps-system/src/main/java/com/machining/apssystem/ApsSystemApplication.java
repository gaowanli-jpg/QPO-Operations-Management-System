package com.machining.apssystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.machining.apssystem.mapper")
public class ApsSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApsSystemApplication.class, args);
    }

}
