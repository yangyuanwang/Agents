package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.example.rag.persistence.mapper")
public class RagApplication {
    public static void main(String[] args) { SpringApplication.run(RagApplication.class, args); }
}
