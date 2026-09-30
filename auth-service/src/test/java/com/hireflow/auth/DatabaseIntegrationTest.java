package com.hireflow.auth;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DatabaseIntegrationTest {

    private static final String DATABASE_NAME = "hireflow_auth";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName(DATABASE_NAME);

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Flyway flyway;

    @Test
    void connectsToServiceOwnedDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains(DATABASE_NAME);
        }
    }

    @Test
    void jpaInitializesAgainstPostgres() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            assertThat(entityManager.createNativeQuery("SELECT 1").getSingleResult()).isNotNull();
        } finally {
            entityManager.close();
        }
    }

    @Test
    void migrationFrameworkInitializes() {
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Object::toString)
                .contains("classpath:db/migration");
        assertThat(flyway.info().applied()).isEmpty();
    }
}
