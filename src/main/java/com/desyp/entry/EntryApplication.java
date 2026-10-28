package com.desyp.entry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EntryApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntryApplication.class, args);
    }
}
