package com.logistics.stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogisticsStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsStreamApplication.class, args);
    }
}
