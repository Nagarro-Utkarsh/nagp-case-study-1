package com.habitloop.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CommonConfiguration {
    @Bean
    RestClient.Builder restClientBuilder() { return RestClient.builder(); }

    @Bean
    WebMvcConfigurer corsConfigurer(org.springframework.core.env.Environment environment) {
        String origin = environment.getProperty("FRONTEND_ORIGIN", "http://localhost:5173");
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type", "X-Api-Key", "X-Internal-Key").allowCredentials(false);
            }
        };
    }
}
