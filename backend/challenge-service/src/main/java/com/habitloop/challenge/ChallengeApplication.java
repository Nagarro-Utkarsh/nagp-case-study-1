package com.habitloop.challenge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.habitloop")
public class ChallengeApplication {
    public static void main(String[] args) { SpringApplication.run(ChallengeApplication.class, args); }
}
