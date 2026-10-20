package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The stored result of an export keeps coherent counts and its own copy of the side files. */
class ExportRecordTest {

    private static final Instant AT = Instant.parse("2026-10-20T10:00:00Z");

    @Test
    void new_sideFiles_areCopied() {
        final List<Path> sides = new ArrayList<>(List.of(Path.of("a.report.md")));
        final ExportRecord record = new ExportRecord(AT, Path.of("a.epub"), 74, 2, 50, 6, 70, 1, 142, sides);
        sides.clear();

        assertThat(record.sideFiles()).containsExactly(Path.of("a.report.md"));
    }

    @Test
    void new_negativeCount_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExportRecord(AT, Path.of("a.epub"), -1, 0, 0, 0, 0, 0, 0, List.of()));
    }
}
