package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.IdSource;

class UuidIdSourceTest {

    @Test
    void newId_twoCalls_giveDifferentValidUuids() {
        final IdSource source = new UuidIdSource();

        final String first = source.newId();
        final String second = source.newId();

        assertThat(first).isNotEqualTo(second);
        assertThat(UUID.fromString(first)).hasToString(first);
    }
}
