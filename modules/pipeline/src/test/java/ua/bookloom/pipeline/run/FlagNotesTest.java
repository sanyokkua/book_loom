package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;

class FlagNotesTest {

    private static final String MESSAGE = "The request is too large for the selected model.";

    // A person reads the flag: it says what happened in words, with the counts when the provider gave them.
    @ParameterizedTest
    @CsvSource(
            nullValues = "-",
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "contextWindow    | CONTEXT_OVERFLOW | 696 | 512 | This passage did not fit the model's window (696 of 512 tokens). It was flagged.",
                "contextWindow    | CONTEXT_OVERFLOW | -   | -   | This passage did not fit the model's window. It was flagged.",
                "contextWindow    | -                | -   | -   | This passage did not fit the model's window. It was flagged.",
                "upstream         | REPEAT_LIMIT     | -   | -   | The model kept repeating itself here.",
                "emptyCompletion  | -                | -   | -   | The request is too large for the selected model."
            })
    void note_reasonAndCounts_isTheSentence(
            final ErrorCode code,
            @Nullable final FailureReason reason,
            @Nullable final Integer prompt,
            @Nullable final Integer window,
            final String expected) {
        assertThat(FlagNotes.note(error(code, reason, prompt, window))).isEqualTo(expected);
    }

    private static AppError error(
            final ErrorCode code,
            @Nullable final FailureReason reason,
            @Nullable final Integer prompt,
            @Nullable final Integer window) {
        final SafeDetails reasoned =
                reason == null ? SafeDetails.empty() : SafeDetails.empty().withReason(reason);
        final SafeDetails counted =
                prompt == null || window == null ? reasoned : reasoned.withTokenCounts(prompt, window);
        return AppError.of(code, "Failed", MESSAGE, counted.render(), null);
    }
}
