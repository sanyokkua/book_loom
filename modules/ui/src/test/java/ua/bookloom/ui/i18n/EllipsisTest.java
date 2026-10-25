package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every ellipsis of the interface is three full stops: the system font's single ellipsis character drew its third dot
 * faint at the interface's sizes on macOS, so "Change…" read as "Change..". One form in both languages.
 */
class EllipsisTest {

    private static Set<String> keysWithThreeDots(final String language) {
        return Catalogues.load(language).entrySet().stream()
                .filter(entry -> entry.getValue().contains(Messages.ELLIPSIS))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // IF a value kept the single ellipsis character, THEN its last dot would be drawn faint.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void values_eachLanguage_haveNoEllipsisCharacter(final String language) {
        final var hits = Catalogues.load(language).entrySet().stream()
                .filter(entry -> entry.getValue().contains("…"))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(hits).as("keys still holding U+2026").isEmpty();
    }

    // IF one language ended a label with the dots and the other did not, THEN the same command would read differently.
    @Test
    void keysWithThreeDots_englishAndUkrainian_areTheSame() {
        assertThat(keysWithThreeDots("uk")).containsExactlyInAnyOrderElementsOf(keysWithThreeDots("en"));
    }

    @Test
    void ellipsis_shared_isThreeFullStops() {
        assertThat(Messages.ELLIPSIS).isEqualTo("...");
    }
}
