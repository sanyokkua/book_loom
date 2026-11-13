package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.TabPane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ReviewDeskFixtures;

/** "What the model saw" names a context Try again rebuilt as the one sent for that retry, in both languages. */
class WhatTheModelSawTest extends ReviewDeskTestBase {

    // IF a retried segment carried the first draft's label, THEN the person would take the rebuilt context for the
    // first draft's; the tab says it is the context as it was sent for this retry.
    @ParameterizedTest
    @CsvSource({"en, as it was sent for this retry", "uk, як її надіслали для цієї повторної спроби"})
    void retry_showsRebuiltLabel(final String language, final String label) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        openDeskWith(List.of(ReviewDeskFixtures.retried()), List.of());

        onFx(() -> ((TabPane) required("review-tabs")).getSelectionModel().select(1));

        assertThat(textOf("review-context-intro")).contains(label);
        assertThat(textOf("review-sent")).contains("Hale", "Гейл", "Formal, past tense.");
    }
}
