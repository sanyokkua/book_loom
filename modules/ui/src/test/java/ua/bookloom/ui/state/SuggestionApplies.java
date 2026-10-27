package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Takes the person's last step of the brief suggestion: since the answer is held until Apply, a test that wants the
 * fields written presses Apply as the dialog's button does.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionApplies {

    /** Applies the answer the suggestion holds, if it holds one, and waits for the brief to be written. */
    static void applyPending(final BookBriefViewModel brief) {
        onFx(() -> {
            final StyleSuggestion suggestion = brief.styleSuggestion();
            if (suggestion.pending().get() != null) {
                suggestion.apply();
            }
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
