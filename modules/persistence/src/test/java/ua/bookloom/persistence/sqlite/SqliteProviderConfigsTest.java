package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.persistence.contract.ProviderConfigsContractTest;

/** Proves the SQLite provider registry against the shared contract and what only a stored file can show. */
class SqliteProviderConfigsTest extends ProviderConfigsContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:15:30Z"), ZoneOffset.UTC);
    private static final String KEY_VALUE = "sk-live-super-secret-value";

    @TempDir
    Path dir;

    private SqliteDatabase db;
    private SqliteProviderConfigs configs;

    @BeforeEach
    void openDatabase() {
        open();
    }

    @AfterEach
    void closeDatabase() {
        db.close();
    }

    private void open() {
        db = Objects.requireNonNull(
                SqliteDatabase.open(dir.resolve("bookloom.db"), CLOCK).data());
        configs = new SqliteProviderConfigs(db, new JsonCodec());
    }

    @Override
    protected ProviderConfigs configs() {
        return configs;
    }

    @Test
    void all_freshDatabase_isEmpty() {
        assertThat(configs.all()).isEmpty();
        assertThat(configs.find("ollama")).isEmpty();
    }

    @Test
    void save_thenRestart_providersAreStillThere() {
        final var first = sample("a").withDisplayName("A");
        final var second = sample("b").withHeaders(Map.of("Authorization", "${env:LMS_KEY}"));
        configs.save(first);
        configs.save(second);
        configs.delete("a");
        configs.save(first);

        db.close();
        open();

        assertThat(configs.all()).containsExactly(first, second);
    }

    @Test
    void save_envReference_storedRowHasTheReferenceAndNeverAKeyValue() {
        configs.save(sample("lms").withHeaders(Map.of("Authorization", "${env:LMS_KEY}")));
        configs.save(sample("literal").withHeaders(Map.of("Authorization", KEY_VALUE)));

        final List<String> rows = Objects.requireNonNull(db.read(
                        "test rows",
                        h -> h.createQuery("SELECT json FROM providers")
                                .mapTo(String.class)
                                .list())
                .data());

        assertThat(rows).singleElement().satisfies(json -> assertThat(json).contains("${env:LMS_KEY}"));
        assertThat(rows).noneMatch(json -> json.contains(KEY_VALUE));
        assertThat(fileBytes()).doesNotContain(KEY_VALUE);
    }

    private String fileBytes() {
        db.close();
        try {
            final String main = new String(
                    Files.readAllBytes(dir.resolve("bookloom.db")), java.nio.charset.StandardCharsets.ISO_8859_1);
            final Path wal = dir.resolve("bookloom.db-wal");
            return main
                    + (Files.exists(wal)
                            ? new String(Files.readAllBytes(wal), java.nio.charset.StandardCharsets.ISO_8859_1)
                            : "");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            open();
        }
    }
}
