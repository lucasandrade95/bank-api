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

    @Test
    void migrationsAddIdempotencyKeysTable() throws Exception {
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");
            runMigration(st, "db/migration/V2__add_account_version.sql");
            runMigration(st, "db/migration/V3__add_account_status.sql");
            runMigration(st, "db/migration/V4__add_idempotency_keys.sql");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.tables " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name = 'IDEMPOTENCY_KEYS'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void migrationsAddIdempotencyRequestFingerprintColumn() throws Exception {
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");
            runMigration(st, "db/migration/V2__add_account_version.sql");
            runMigration(st, "db/migration/V3__add_account_status.sql");
            runMigration(st, "db/migration/V4__add_idempotency_keys.sql");
            runMigration(st, "db/migration/V5__add_idempotency_request_fingerprint.sql");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name = 'IDEMPOTENCY_KEYS' " +
                            "AND column_name = 'REQUEST_FINGERPRINT'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    /**
     * O expurgo das chaves vencidas filtra por {@code created_at}; sem indice ele
     * varreria a tabela inteira a cada execucao.
     */
    @Test
    void migrationsAddIdempotencyCreatedAtIndex() throws Exception {
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");
            runMigration(st, "db/migration/V2__add_account_version.sql");
            runMigration(st, "db/migration/V3__add_account_status.sql");
            runMigration(st, "db/migration/V4__add_idempotency_keys.sql");
            runMigration(st, "db/migration/V5__add_idempotency_request_fingerprint.sql");
            runMigration(st, "db/migration/V6__add_idempotency_keys_created_at_index.sql");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM information_schema.indexes " +
                            "WHERE table_schema = 'PUBLIC' " +
                            "AND table_name = 'IDEMPOTENCY_KEYS' " +
                            "AND index_name = 'IDX_IDEMPOTENCY_KEYS_CREATED_AT'")) {
                rs.next();
                assertThat(rs.getInt(1)).isPositive();
            }
        }
    }

    /**
     * O extrato pede "uma conta, do mais recente para o mais antigo, uma pagina":
     * o indice so por {@code account_id} da V1 nao serve a ordenacao nem ao recorte
     * por periodo. A V7 troca os dois — o composto entra e o redundante sai.
     */
    @Test
    void migrationsReplaceTransactionsIndexWithCompositeOne() throws Exception {
        try (Connection conn = freshPostgresLikeDb();
             Statement st = conn.createStatement()) {

            runMigration(st, "db/migration/V1__init_schema.sql");
            runMigration(st, "db/migration/V2__add_account_version.sql");
            runMigration(st, "db/migration/V3__add_account_status.sql");
            runMigration(st, "db/migration/V4__add_idempotency_keys.sql");
            runMigration(st, "db/migration/V5__add_idempotency_request_fingerprint.sql");
            runMigration(st, "db/migration/V6__add_idempotency_keys_created_at_index.sql");
            runMigration(st, "db/migration/V7__replace_transactions_account_index.sql");

            assertThat(indexColumns(st, "IDX_TRANSACTIONS_ACCOUNT_CREATED_AT"))
                    .containsExactly("ACCOUNT_ID", "CREATED_AT", "ID");
            assertThat(indexColumns(st, "IDX_TRANSACTIONS_ACCOUNT_ID")).isEmpty();
        }
    }

    /** Colunas de um indice de {@code transactions}, na ordem em que ele as usa. */
    private static java.util.List<String> indexColumns(Statement st, String indexName) throws Exception {
        java.util.List<String> columns = new java.util.ArrayList<>();
        try (ResultSet rs = st.executeQuery(
                "SELECT column_name FROM information_schema.index_columns " +
                        "WHERE table_schema = 'PUBLIC' AND table_name = 'TRANSACTIONS' " +
                        "AND index_name = '" + indexName + "' ORDER BY ordinal_position")) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        return columns;
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
