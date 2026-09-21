package dev.cerez.titan.infrastructure.config;

import dev.cerez.titan.infrastructure.user.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InitialDataConfig {

    @Bean
    CommandLineRunner createInitialUser(UserService userService) {
        return args -> {
            if (!userService.existsByUsername("root")) {
                userService.createUser("root", "");
            }
        };
    }
}
