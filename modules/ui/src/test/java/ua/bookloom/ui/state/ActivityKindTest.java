package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Which work may be put in the background: only the long work, the names pass and the export's last check. */
class ActivityKindTest {

    // IF the names pass or the export could not be hidden, THEN a person would wait minutes in front of a dialog.
    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            names = {"NAMES_PASS", "EXPORT"})
    void isHideable_namesPassAndExport_true(final ActivityKind kind) {
        assertThat(kind.isHideable()).isTrue();
    }

    // IF short work could be hidden, THEN a brief suggestion would end behind the person's back.
    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            names = {"SUGGEST_STYLE", "SUGGEST_NAME", "PROVIDER_INFERENCE_TEST", "GLOSSARY_SCAN", "IMPORT"})
    void isHideable_shortWork_false(final ActivityKind kind) {
        assertThat(kind.isHideable()).isFalse();
    }
}
