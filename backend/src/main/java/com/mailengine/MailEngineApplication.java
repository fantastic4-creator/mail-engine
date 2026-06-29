package com.mailengine;

import com.mailengine.config.MailEngineRuntimeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(MailEngineRuntimeProperties.class)
public class MailEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailEngineApplication.class, args);
    }
}
