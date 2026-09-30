package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A hover explanation explains a control that has a label: every {@code .tip} key extends a key that exists. */
class TipKeysTest {

    private static final String SUFFIX = ".tip";

    // IF a tip key had no label key, THEN it would explain nothing the interface displays; this names it.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void tipKey_everyOne_hasItsBaseKeyAndNonBlankText(final String language) {
        final Map<String, String> bundle = Catalogues.load(language);

        final var tips =
                bundle.keySet().stream().filter(key -> key.endsWith(SUFFIX)).toList();

        assertThat(tips).isNotEmpty();
        assertThat(tips).allSatisfy(tip -> {
            assertThat(bundle).containsKey(tip.substring(0, tip.length() - SUFFIX.length()));
            assertThat(bundle.get(tip)).isNotBlank();
        });
    }

    // IF a constant named _TIP addressed another key, THEN a screen would show the wrong explanation.
    @ParameterizedTest
    @EnumSource(MessageKey.class)
    void tipConstant_everyKey_isNamedTipExactlyWhenItsKeyEndsInTip(final MessageKey key) {
        assertThat(key.name().endsWith("_TIP")).isEqualTo(key.key().endsWith(SUFFIX));
    }
}
