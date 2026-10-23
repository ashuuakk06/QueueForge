package com.queueforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QueueForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueForgeApplication.class, args);
    }
}

