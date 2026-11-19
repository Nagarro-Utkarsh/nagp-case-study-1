package com.habitloop.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.habitloop")
@EnableScheduling
public class ActivityApplication {
    public static void main(String[] args) { SpringApplication.run(ActivityApplication.class, args); }
}
