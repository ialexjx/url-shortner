package com.akshat.shortener.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * ==============================================================================
 * Multi-Cloud & Render Resilient Database Configuration
 * ==============================================================================
 * 
 * Kyun banaya?
 * Render.com jab PostgreSQL database provide karta hai, toh wo standard env var:
 * DATABASE_URL="postgres://user:password@host:port/database" deta hai.
 * 
 * JDBC Driver (HikariCP) isko direct parse nahi kar pata kyunki JDBC format:
 * "jdbc:postgresql://host:port/database" hona chahiye!
 * 
 * Ye config automatically check karta hai:
 * 1. Agar Render ka 'postgres://' ya 'postgresql://' URL mila, toh cleanly JDBC format
 *    me convert karke username, password aur host extract kar leta hai.
 * 2. Agar standard JDBC URL mila (jaise local H2 ya MySQL), toh bina छेड़छाड़ ke chalne deta hai.
 * 3. Isse Render par 100% zero-configuration deployment guaranteed ho jati hai!
 */
@Slf4j
@Configuration
public class DatabaseConfig {

    @Value("${SPRING_DATASOURCE_URL:${DATABASE_URL:}}")
    private String databaseUrl;

    @Value("${spring.datasource.driver-class-name:}")
    private String driverClassName;

    @Value("${spring.datasource.username:}")
    private String defaultUsername;

    @Value("${spring.datasource.password:}")
    private String defaultPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            log.info("Render PostgreSQL DATABASE_URL detect hua! Converting to JDBC format...");
            try {
                URI uri = new URI(databaseUrl);
                String userInfo = uri.getUserInfo();
                String username = defaultUsername;
                String password = defaultPassword;

                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts[1];
                }

                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();

                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
                config.setDriverClassName("org.postgresql.Driver");
                log.info("Successfully configured Render PostgreSQL DataSource: {}", jdbcUrl);
            } catch (Exception e) {
                log.error("Failed to parse Render DATABASE_URL: {}. Falling back to default datasource.", e.getMessage());
                applyDefaultConfig(config);
            }
        } else {
            applyDefaultConfig(config);
        }

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(20000);

        return new HikariDataSource(config);
    }

    private void applyDefaultConfig(HikariConfig config) {
        String url = (databaseUrl != null && !databaseUrl.isBlank()) 
                ? databaseUrl 
                : "jdbc:h2:mem:shortenerdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL";
        
        config.setJdbcUrl(url);
        if (driverClassName != null && !driverClassName.isBlank()) {
            config.setDriverClassName(driverClassName);
        } else if (url.contains("h2")) {
            config.setDriverClassName("org.h2.Driver");
        } else if (url.contains("postgresql")) {
            config.setDriverClassName("org.postgresql.Driver");
        }
        
        config.setUsername(defaultUsername != null && !defaultUsername.isBlank() ? defaultUsername : "sa");
        config.setPassword(defaultPassword != null ? defaultPassword : "");
    }
}
