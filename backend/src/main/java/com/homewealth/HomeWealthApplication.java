package com.homewealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HomeWealthApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomeWealthApplication.class, args);
    }
}
