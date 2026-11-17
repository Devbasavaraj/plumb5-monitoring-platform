package com.example.license.config;

import com.example.license.repository.LicenseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(LicenseRepository repository) {
        return args -> {
            // No more hardcoding!
            // All clients are now managed dynamically through the dashboard.
            System.out.println("Developer Backend started. Use the dashboard to manage licenses.");
        };
    }
}
