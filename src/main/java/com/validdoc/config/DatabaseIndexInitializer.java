package com.validdoc.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS users_username_active_unique ON users (username) WHERE active = true"
        );
        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS templates_name_active_unique ON templates (name) WHERE active = true"
        );
    }
}