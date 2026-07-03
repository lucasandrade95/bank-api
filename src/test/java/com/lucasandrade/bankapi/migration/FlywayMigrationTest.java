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
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name IN ('USERS', 'ACCOUNTS', 'TRANSACTIONS')")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        }
    }

    @Test
    void migrationsAddOptimisticLockVersionColumn() throws Exception {
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");
            runMigration(st, "db/migration/V2__add_account_version.sql");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name = 'ACCOUNTS' AND column_name = 'VERSION'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void migrationsAddAccountStatusColumn() throws Exception {
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");
            runMigration(st, "db/migration/V2__add_account_version.sql");
            runMigration(st, "db/migration/V3__add_account_status.sql");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name = 'ACCOUNTS' AND column_name = 'STATUS'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    private static Connection freshPostgresLikeDb() throws Exception {
        // Cada teste usa um banco isolado para nao herdar tabelas de outro.
        return DriverManager.getConnection(
                "jdbc:h2:mem:migtest_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa", "");
    }

    private static void runMigration(Statement st, String classpathLocation) throws Exception {
        String raw = StreamUtils.copyToString(
                new ClassPathResource(classpathLocation).getInputStream(), StandardCharsets.UTF_8);
        // Remove comentarios de linha (-- ...) antes de separar por ";".
        String script = raw.replaceAll("(?m)--.*$", "");
        for (String statement : script.split(";")) {
            if (!statement.isBlank()) {
                st.execute(statement);
            }
        }
    }
}
