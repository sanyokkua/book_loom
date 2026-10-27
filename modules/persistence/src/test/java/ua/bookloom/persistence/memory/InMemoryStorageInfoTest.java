package ua.bookloom.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** The in-memory twin has no file, so it reports none and cannot back anything up. */
class InMemoryStorageInfoTest {

    private final InMemoryStorageInfo info = new InMemoryStorageInfo();

    @Test
    void location_inMemory_isEmpty() {
        assertThat(info.location()).isEmpty();
    }

    @Test
    void sizeBytes_inMemory_isZero() {
        assertThat(info.sizeBytes()).isZero();
    }

    @Test
    void backupTo_inMemory_isAValidationError() {
        final Result<Path> result = info.backupTo(Path.of("copy.db"));

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }
}
