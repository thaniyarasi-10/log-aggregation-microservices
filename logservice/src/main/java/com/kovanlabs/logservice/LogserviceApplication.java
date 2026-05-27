package com.kovanlabs.logservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableScheduling
public class LogserviceApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(LogserviceApplication.class, args);
        Environment env = context.getEnvironment();

        System.out.println("Logservice started successfully");
        System.out.println("Port: " + env.getProperty("server.port"));
    }

}
