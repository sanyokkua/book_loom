package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ThemeSetting;
import ua.bookloom.persistence.contract.SettingsContractTest;

/** Proves the SQLite settings adapter against the shared contract, and that a stored setting survives a restart. */
class SqliteSettingsContractTest extends SettingsContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private SqliteDatabase db;
    private SqliteSettingsRepository repository;

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
        repository = new SqliteSettingsRepository(db);
    }

    @Override
    protected SettingsRepository settings() {
        return repository;
    }

    @Test
    void put_thenRestart_valueIsStillThere() {
        repository.put(AppSetting.THEME, ThemeSetting.DARK);
        repository.put(AppSetting.LANGUAGE, "uk");

        db.close();
        open();

        assertThat(repository.get(AppSetting.THEME).data()).hasValue(ThemeSetting.DARK);
        assertThat(repository.get(AppSetting.LANGUAGE).data()).hasValue("uk");
    }

    @Test
    void put_storesTheTypeNextToTheValue() {
        repository.put(AppSetting.KEEP_AWAKE, false);

        final String row = Objects.requireNonNull(db.read(
                        "test row",
                        h -> h.createQuery(
                                        "SELECT value || '/' || type FROM settings WHERE key = 'automation.keepAwake'")
                                .mapTo(String.class)
                                .one())
                .data());
        assertThat(row).startsWith("false/").doesNotEndWith("/");
    }
}
