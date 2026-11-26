package com.mointer.cloud_health_monitor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Automatically parses Render PostgreSQL DATABASE_URL ("postgres://user:pass@host:port/db")
 * into standard Spring Boot JDBC properties ("spring.datasource.url", etc.)
 * before DataSource beans are initialized.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }

        if (databaseUrl != null && !databaseUrl.isBlank() && !databaseUrl.startsWith("jdbc:")) {
            try {
                URI dbUri = new URI(databaseUrl);
                String userInfo = dbUri.getUserInfo();
                String username = "";
                String password = "";
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts[1];
                } else if (userInfo != null) {
                    username = userInfo;
                }

                int port = dbUri.getPort() > 0 ? dbUri.getPort() : 5432;
                String path = dbUri.getPath();
                if (path != null && path.startsWith("/")) {
                    path = path.substring(1);
                }

                String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + port + "/" + path;
                if (!jdbcUrl.contains("sslmode")) {
                    jdbcUrl += "?sslmode=require";
                }

                Map<String, Object> props = new HashMap<>();
                props.put("spring.datasource.url", jdbcUrl);
                props.put("spring.datasource.driverClassName", "org.postgresql.Driver");
                props.put("spring.datasource.username", username);
                props.put("spring.datasource.password", password);
                props.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");

                environment.getPropertySources().addFirst(new MapPropertySource("renderDatabaseUrlProperties", props));
                log.info("Configured Render PostgreSQL from DATABASE_URL: jdbc:postgresql://{}:{}/{}", dbUri.getHost(), port, path);
            } catch (Exception e) {
                log.error("Failed to parse Render DATABASE_URL: {}", e.getMessage());
            }
        }
    }
}
