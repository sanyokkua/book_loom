package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;

class ProjectTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant OPENED = Instant.parse("2026-02-01T10:00:00Z");

    private static Project named(final String name) {
        return new Project(
                "p1",
                Path.of("book.txt"),
                BookFormat.TXT,
                "hash",
                BookBrief.defaults("en"),
                name,
                NameOrigin.USER,
                CREATED,
                OPENED);
    }

    @Test
    void nameKey_sameNameInTwoCases_isOneKey() {
        assertThat(named("Земля").nameKey()).isEqualTo(named("земля").nameKey()).isEqualTo("земля");
    }

    @Test
    void nameKey_surroundingSpaces_areStripped() {
        assertThat(named("  Second Draft ").nameKey()).isEqualTo("second draft");
    }

    @Test
    void withBrief_replacementBrief_keepsNameOriginAndTimes() {
        final Project changed = named("Draft").withBrief(BookBrief.defaults("uk"));

        assertThat(changed.brief()).isEqualTo(BookBrief.defaults("uk"));
        assertThat(changed.name()).isEqualTo("Draft");
        assertThat(changed.nameOrigin()).isEqualTo(NameOrigin.USER);
        assertThat(changed.createdAt()).isEqualTo(CREATED);
        assertThat(changed.lastOpenedAt()).isEqualTo(OPENED);
    }
}
