package com.habitloop.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TenantSeeder {
    @Bean CommandLineRunner seedTenants(TenantRepository tenants) {
        return args -> {
            if (tenants.findBySlug("public").isEmpty()) tenants.save(new Tenant("public", "Public"));
            if (tenants.findBySlug("acme").isEmpty()) tenants.save(new Tenant("acme", "Acme"));
        };
    }
}
