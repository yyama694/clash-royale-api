package com.example.clashroyaleapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClashRoyaleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClashRoyaleApiApplication.class, args);
    }
}
