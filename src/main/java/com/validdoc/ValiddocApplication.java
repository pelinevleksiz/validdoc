package com.validdoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ValiddocApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValiddocApplication.class, args);
    }

}