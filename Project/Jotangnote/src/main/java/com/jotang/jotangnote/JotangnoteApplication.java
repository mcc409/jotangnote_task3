package com.jotang.jotangnote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jotang.jotangnote.mapper")
public class JotangnoteApplication {
    public static void main(String[] args) {
        SpringApplication.run(JotangnoteApplication.class, args);
    }
}