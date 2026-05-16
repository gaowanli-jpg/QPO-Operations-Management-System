package com.machining.quote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.machining.quote.mapper")
public class QuoteSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuoteSystemApplication.class, args);
    }

}
