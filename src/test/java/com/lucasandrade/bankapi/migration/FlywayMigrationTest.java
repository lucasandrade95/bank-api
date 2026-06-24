package com.lucasandrade.bankapi.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que a migracao Flyway do profile "postgres" e um SQL valido e
 * executavel num banco compativel com PostgreSQL, sem precisar de um Postgres
 * real no CI (usa H2 em MODE=PostgreSQL). Protege contra DDL quebrado entrar
 * no repositorio.
 */
class FlywayMigrationTest {

    @Test
    void initSchemaCreatesCoreTables() throws Exception {
        String raw = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V1__init_schema.sql").getInputStream(),
                StandardCharsets.UTF_8);
        // Remove comentarios de linha (-- ...) antes de separar por ";".
        String script = raw.replaceAll("(?m)--.*$", "");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:migtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {

            for (String statement : script.split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name IN ('USERS', 'ACCOUNTS', 'TRANSACTIONS')")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        }
    }
}
