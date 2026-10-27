package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.i18n.MessageKey;

/** Every activity-log entry carries a kind, a status role, a non-colour mark and a catalogue message key. */
class LogKindTest {

    // IF a kind mapped to the wrong role or default message, THEN the log would colour or word an entry wrongly.
    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, OK, LOG_ACCEPTED",
        "REPAIRED, INFO, LOG_MODEL_CALL",
        "MEMORY, INFO, LOG_GLOSSARY_APPLIED",
        "SUMMARY, INFO, LOG_SUMMARY_UPDATED",
        "RETRIED, WARN, LOG_RETRIED",
        "SEGMENT_ERROR, ERR, LOG_SEGMENT_ERROR",
        "MILESTONE, INFO, LOG_MILESTONE"
    })
    void kind_eachOfTheSeven_hasItsRoleAndDefaultCatalogueKey(
            final LogKind kind, final Tone role, final MessageKey key) {
        assertThat(kind.role()).isEqualTo(role);
        assertThat(kind.messageKey()).isEqualTo(key);
    }

    // IF a kind carried the wrong tag or glyph, THEN the log's tag column would mislabel the entry.
    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, ok, ✓",
        "REPAIRED, fix, ↺",
        "MEMORY, mem, ≡",
        "SUMMARY, sum, Σ",
        "RETRIED, retry, ↻",
        "SEGMENT_ERROR, err, ✕",
        "MILESTONE, info, ◆"
    })
    void tagAndMark_eachOfTheSeven_isItsFixedWordAndGlyph(final LogKind kind, final String tag, final String mark) {
        assertThat(kind.tag()).isEqualTo(tag);
        assertThat(kind.mark()).isEqualTo(mark);
    }

    // IF a kind that is not a problem were counted as one, THEN Problems only would list routine lines.
    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, false",
        "REPAIRED, false",
        "MEMORY, false",
        "SUMMARY, false",
        "RETRIED, true",
        "SEGMENT_ERROR, true",
        "MILESTONE, false"
    })
    void isTrouble_eachOfTheSeven_isTrueOnlyForRetriesAndErrors(final LogKind kind, final boolean trouble) {
        assertThat(kind.isTrouble()).isEqualTo(trouble);
    }

    // IF two kinds shared a tag or one were not lower-case, THEN the column could not be read at a glance.
    @Test
    void tag_allKinds_areDistinctAndLowerCase() {
        final List<String> tags =
                Arrays.stream(LogKind.values()).map(LogKind::tag).collect(Collectors.toList());

        assertThat(tags).doesNotHaveDuplicates().allMatch(tag -> tag.equals(tag.toLowerCase(Locale.ROOT)));
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

    // IF a technical line looked like any other, THEN Show technical details could not hide it.
    @Test
    void logEntry_technicalFactory_keepsItsMessageAndIsTechnical() {
        final LogEntry entry = LogEntry.technical(LogKind.MILESTONE, MessageKey.LOG_MODEL_CALL, List.of("draft"));

        assertThat(entry.technical()).isTrue();
        assertThat(entry.messageKey()).isEqualTo(MessageKey.LOG_MODEL_CALL);
        assertThat(entry.wording()).isEqualTo(entry);
        assertThat(new LogEntry(LogKind.MILESTONE, List.of("paused")).technical())
                .isFalse();
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
