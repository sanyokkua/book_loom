package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The English and Ukrainian catalogues are complete against each other. */
class MessageBundleParityTest {

    @Test
    void bundles_shippedCatalogues_areNotEmpty() {
        // IF a bundle were empty, THEN the parity comparison below would agree with itself and prove nothing.
        assertThat(Catalogues.load("en")).isNotEmpty();
        assertThat(Catalogues.load("uk")).isNotEmpty();
    }

    @Test
    void keySets_englishAndUkrainian_areIdentical() {
        // IF a key is defined in one catalogue and missing from the other, THEN this names it.
        final Map<String, String> english = Catalogues.load("en");
        final Map<String, String> ukrainian = Catalogues.load("uk");

        assertThat(ukrainian.keySet()).containsExactlyInAnyOrderElementsOf(english.keySet());
    }
}
