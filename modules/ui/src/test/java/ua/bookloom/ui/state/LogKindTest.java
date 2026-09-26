package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.i18n.MessageKey;

/** Every activity-log entry carries a kind, a status role, a non-colour mark and a catalogue message key. */
class LogKindTest {

    // IF a kind mapped to the wrong role or catalogue message, THEN the log would colour or word an entry wrongly.
    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, SUCCESS, LOG_ACCEPTED",
        "REPAIRED, INFO, LOG_REPAIRED",
        "GLOSSARY_APPLIED, INFO, LOG_GLOSSARY_APPLIED",
        "SUMMARY_UPDATED, INFO, LOG_SUMMARY_UPDATED",
        "RETRIED, WARNING, LOG_RETRIED",
        "SEGMENT_ERROR, DANGER, LOG_SEGMENT_ERROR",
        "MILESTONE, INFO, LOG_MILESTONE"
    })
    void kind_eachOfTheSeven_hasItsRoleAndCatalogueKey(
            final LogKind kind, final StatusRole role, final MessageKey key) {
        assertThat(kind.role()).isEqualTo(role);
        assertThat(kind.messageKey()).isEqualTo(key);
    }

    // IF a kind had no glyph, THEN its meaning would rest on colour alone, which a colour-blind reader cannot use.
    @ParameterizedTest
    @EnumSource(LogKind.class)
    void mark_everyKind_isANonBlankGlyph(final LogKind kind) {
        assertThat(kind.mark()).isNotBlank();
    }

    // IF two kinds shared a glyph, THEN they could only be told apart by colour.
    @Test
    void mark_allKinds_areDistinct() {
        final List<String> marks =
                Arrays.stream(LogKind.values()).map(LogKind::mark).collect(Collectors.toList());

        assertThat(LogKind.values()).hasSize(7);
        assertThat(marks).doesNotHaveDuplicates();
    }

    // IF the entry took its own role instead of the kind's, THEN one kind could render in two roles.
    @ParameterizedTest
    @EnumSource(LogKind.class)
    void logEntry_anyKind_delegatesRoleAndMessageKeyToTheKind(final LogKind kind) {
        final LogEntry entry = new LogEntry(kind, List.of("s-1"));

        assertThat(entry.role()).isEqualTo(kind.role());
        assertThat(entry.messageKey()).isEqualTo(kind.messageKey());
        assertThat(entry.kind()).isEqualTo(kind);
    }

    // IF the entry kept the caller's list, THEN a later edit would rewrite a line already in the log.
    @Test
    void logEntry_callerMutatesArgsAfterwards_entryKeepsItsOwnCopy() {
        final List<String> args = new ArrayList<>(List.of("s-1"));
        final LogEntry entry = new LogEntry(LogKind.ACCEPTED, args);

        args.add("s-2");

        assertThat(entry.args()).containsExactly("s-1");
        assertThatThrownBy(() -> entry.args().add("s-3")).isInstanceOf(UnsupportedOperationException.class);
    }

    // IF a null were accepted, THEN a bad entry would fail later in the FX thread instead of at the boundary.
    @Test
    @SuppressWarnings("NullAway")
    void logEntry_nullKindOrArgs_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new LogEntry(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new LogEntry(LogKind.ACCEPTED, null));
    }

    // IF two entries with equal parts were unequal, THEN a list assertion or de-duplication would misbehave.
    @Test
    void logEntry_sameKindAndArgs_areEqual() {
        assertThat(new LogEntry(LogKind.MILESTONE, List.of("paused")))
                .isEqualTo(new LogEntry(LogKind.MILESTONE, List.of("paused")))
                .isNotEqualTo(new LogEntry(LogKind.MILESTONE, List.of("resumed")));
    }
}
