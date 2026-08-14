package com.smartparkingmanagementsystem.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing setup. Uses BCrypt so passwords are never stored in plain
 * text. A dedicated bean keeps the design ready for a full security layer
 * (e.g. Spring Security + JWT) in a later phase.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
