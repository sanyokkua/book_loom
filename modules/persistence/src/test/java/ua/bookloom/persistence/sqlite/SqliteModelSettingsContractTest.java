package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.persistence.contract.ModelSettingsContractTest;

/** Proves the SQLite per-model settings adapter against the shared contract, and that refusals survive a restart. */
class SqliteModelSettingsContractTest extends ModelSettingsContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private SqliteDatabase db;
    private SqliteModelSettingsRepository repository;

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
        repository = new SqliteModelSettingsRepository(db, new JsonCodec());
    }

    @Override
    protected ModelSettingsRepository repository() {
        return repository;
    }

    @Test
    void save_thenRestart_keepsRefusalsAndMeasuredSpeed() {
        final ModelSettings saved = new ModelSettings(
                "ollama",
                "gemma",
                TemperatureChoice.VALUE,
                0.4,
                8192,
                1024,
                OutputLengthField.MAX_COMPLETION_TOKENS,
                List.of(new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "left it out")),
                2.5,
                MeasuredFrom.TEST);
        repository.save(saved);

        db.close();
        open();

        assertThat(repository.find("ollama", "gemma").data()).hasValue(saved);
        assertThat(repository.all().data()).containsExactly(saved);
    }
}
