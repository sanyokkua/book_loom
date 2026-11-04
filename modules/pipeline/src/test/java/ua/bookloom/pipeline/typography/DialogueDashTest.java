package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DialogueDashTest {

    @Test
    void normalise_gluedCommaDash_splits() {
        assertThat(DialogueDash.apply("Так,-сказав він").text()).isEqualTo("Так, — сказав він");
    }

    @Test
    void normalise_gluedStartAndComma_areBothSpaced() {
        assertThat(DialogueDash.apply("—Іди геть,-сказав він.").text()).isEqualTo("— Іди геть, — сказав він.");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "en dash at the line start|– Привіт.|— Привіт.",
                "question mark then hyphen|Що?-спитав він.|Що? — спитав він.",
                "spaced en dash between words|Він – лікар.|Він — лікар.",
            })
    void normalise_otherForms_becomeTheOneForm(final String name, final String raw, final String expected) {
        assertThat(DialogueDash.apply(raw).text()).isEqualTo(expected);
    }

    @Test
    void normalise_secondLineStartingWithShortDash_isRewritten() {
        assertThat(DialogueDash.apply("— Так.\n–Ні.").text()).isEqualTo("— Так.\n— Ні.");
    }

    @Test
    void normalise_hyphenInsideWord_unchanged() {
        assertThat(DialogueDash.apply("— Будь-хто міг це зробити."))
                .isEqualTo(new Edit("— Будь-хто міг це зробити.", 0));
    }

    @Test
    void normalise_digitsRange_unchanged() {
        assertThat(DialogueDash.apply("У 1914-1918 роках, а також 5 - 3.").count())
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"— …", "— Привіт, — сказав він.", "- пункт списку", "⟦g0⟧– слово"})
    void normalise_alreadyRightOrNotSpeech_unchanged(final String text) {
        assertThat(DialogueDash.apply(text)).isEqualTo(new Edit(text, 0));
    }

    @Test
    void normalise_secondPass_changesNothing() {
        final String once = DialogueDash.apply("—Іди,-сказав він. Що?–спитав.").text();

        assertThat(DialogueDash.apply(once).count()).isZero();
    }
}
