package com.c2cagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the locally hosted C2C Job Application Agent (modular monolith). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class C2cAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(C2cAgentApplication.class, args);
    }
}
