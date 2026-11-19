package com.habitloop.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.habitloop")
public class RecommendationApplication {
    public static void main(String[] args) { SpringApplication.run(RecommendationApplication.class, args); }
}
