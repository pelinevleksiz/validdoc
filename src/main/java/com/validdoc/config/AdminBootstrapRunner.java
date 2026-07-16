package com.validdoc.config;

import com.validdoc.model.User;
import com.validdoc.model.enums.UserRole;
import com.validdoc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapEmail;

    public AdminBootstrapRunner(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap-admin.username}") String bootstrapUsername,
                                @Value("${app.bootstrap-admin.password}") String bootstrapPassword,
                                @Value("${app.bootstrap-admin.email}") String bootstrapEmail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = new User();
        admin.setUsername(bootstrapUsername);
        admin.setPassword(passwordEncoder.encode(bootstrapPassword));
        admin.setEmail(bootstrapEmail);
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        log.warn("Bootstrap admin hesabi olusturuldu: username={}. Ilk girisin ardindan sifreyi degistir.", bootstrapUsername);
    }
}