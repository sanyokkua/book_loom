package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Which work may be put in the background: only the long work, the names pass. */
class ActivityKindTest {

    // IF the names pass could not be hidden, THEN a person would wait minutes in front of a dialog.
    @Test
    void isHideable_namesPass_true() {
        assertThat(ActivityKind.NAMES_PASS.isHideable()).isTrue();
    }

    // IF short work could be hidden, THEN a brief suggestion would end behind the person's back.
    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            names = {"SUGGEST_STYLE", "SUGGEST_NAME", "PROVIDER_INFERENCE_TEST", "GLOSSARY_SCAN", "IMPORT", "EXPORT"})
    void isHideable_shortWork_false(final ActivityKind kind) {
        assertThat(kind.isHideable()).isFalse();
    }
}
