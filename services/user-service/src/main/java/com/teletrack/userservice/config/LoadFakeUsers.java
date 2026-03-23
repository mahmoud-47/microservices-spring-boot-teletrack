package com.teletrack.userservice.config;

import com.teletrack.userservice.entity.User;
import com.teletrack.userservice.entity.UserRole;
import com.teletrack.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class LoadFakeUsers implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@admin.com";

        // check if user already exists
        if (userRepository.findByEmail(adminEmail).isEmpty()) {

            User admin = User.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode("admin"))
                    .firstName("Admin")
                    .lastName("User")
                    .role(UserRole.ADMIN)
                    .active(true)
                    .approved(true)
                    .build();

            userRepository.save(admin);

            System.out.println("✅ Default admin user created");
        } else {
            System.out.println("ℹ️ Admin user already exists");
        }
    }
}