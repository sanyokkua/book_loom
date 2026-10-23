package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The pure rules behind the protected marks: which marks are missing or doubled, and the two spellings. */
class MarkStatusTest {

    private static final String SOURCE = "a ⟦0⟧b⟦1⟧";

    // IF a mark were not counted per occurrence, THEN a repeated mark would look complete.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "a b⟦1⟧|⟦0⟧||false",
                "⟦0⟧⟦1⟧⟦1⟧||⟦1⟧|false",
                "⟦0⟧⟦0⟧b|⟦1⟧|⟦0⟧|false",
                "plain words|⟦0⟧ ⟦1⟧||false",
                "⟦1⟧⟦0⟧|||true",
                "a ⟦0⟧b⟦1⟧|||true",
                "⟦0⟧⟦0⟧⟦1⟧⟦1⟧||⟦0⟧ ⟦1⟧|false"
            })
    void of_editorAgainstTheSource_namesTheMissingAndTheDoubledMarks(
            final String editor, final String missing, final String doubled, final boolean complete) {
        final MarkStatus status = MarkStatus.of(SOURCE, editor);

        assertThat(status.missing()).isEqualTo(split(missing));
        assertThat(status.doubled()).isEqualTo(split(doubled));
        assertThat(status.isComplete()).isEqualTo(complete);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no marks here", "⟦0⟧ typed anyway ⟦0⟧"})
    void of_sourceWithoutMarks_isCompleteForAnyText(final String editor) {
        assertThat(MarkStatus.of("Plain source.", editor).isComplete()).isTrue();
    }

    // A typed ⟦g1⟧ is plain text here; the pipeline net refuses it, so it is neither a mark nor a doubled one.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"⟦0⟧b⟦1⟧⟦g1⟧|", "⟦g0⟧b⟦1⟧|⟦0⟧"})
    void of_storedFormMarkTyped_countsAsPlainText(final String editor, final String missing) {
        assertThat(MarkStatus.of(SOURCE, editor).missing()).isEqualTo(split(missing));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"⟦g0⟧ text ⟦g1⟧|⟦0⟧ text ⟦1⟧", "a ⟦g12⟧b⟦g3⟧|a ⟦12⟧b⟦3⟧", "no marks at all|no marks at all"})
    void showAndStore_maskedText_roundTripsThroughTheDisplayForm(final String masked, final String display) {
        assertThat(MarkStatus.show(masked)).isEqualTo(display);
        assertThat(MarkStatus.store(display)).isEqualTo(masked);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"a ⟦0⟧b⟦1⟧⟦1⟧|⟦0⟧ ⟦1⟧ ⟦1⟧", "none|", "⟦7⟧x⟦2⟧|⟦7⟧ ⟦2⟧"})
    void marksOf_text_listsEveryMarkInOrderCountingRepeats(final String text, final String marks) {
        assertThat(MarkStatus.marksOf(text)).isEqualTo(split(marks));
    }

    private static List<String> split(final String joined) {
        return joined == null || joined.isEmpty() ? List.of() : List.of(joined.split(" "));
    }
}
