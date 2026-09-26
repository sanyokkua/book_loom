package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The keys the application addresses and the keys the catalogues define are the same set, in both directions. */
class MessageKeyRegistryTest {

    private static List<String> registeredKeys() {
        return Arrays.stream(MessageKey.values()).map(MessageKey::key).toList();
    }

    // IF a MessageKey has no entry in a catalogue, THEN it would display as a raw key; this fails and names it.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void registry_everyMessageKey_isDefinedInBundle(final String language) {
        final Map<String, String> bundle = Catalogues.load(language);

        assertThat(bundle.keySet()).containsAll(registeredKeys());
    }

    // IF a catalogue defines a key no MessageKey addresses, THEN it is dead text nothing displays; this names it.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void bundle_everyDefinedKey_isAMessageKey(final String language) {
        final Map<String, String> bundle = Catalogues.load(language);

        assertThat(registeredKeys()).containsAll(bundle.keySet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void registry_keys_areUniqueAndMatchBundleSize(final String language) {
        // IF two constants shared one bundle key, THEN one screen's label would silently change with another's.
        assertThat(registeredKeys())
                .doesNotHaveDuplicates()
                .hasSameSizeAs(Catalogues.load(language).keySet());
    }
}
