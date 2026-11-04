package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LeadingDashTest {

    @Test
    void restore_targetStartsWithLetter_prependsDash() {
        assertThat(LeadingDash.apply("— Where is the key?", "Де ключ?")).isEqualTo(new Edit("— Де ключ?", 1));
    }

    @Test
    void restore_hyphenSource_countsAsADash() {
        assertThat(LeadingDash.apply("- Come in.", "Заходьте.").text()).isEqualTo("— Заходьте.");
    }

    @Test
    void restore_targetStartsWithQuote_raisesNote() {
        assertThat(LeadingDash.apply("— Where is the key?", "«Де ключ?»")).isEqualTo(new Edit("«Де ключ?»", 0));
        assertThat(LeadingDash.isLostBeforeQuote("— Where is the key?", "«Де ключ?»"))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Where is the key?", "-Where", "Mother-in-law came."})
    void restore_sourceWithoutALeadingDash_isUnchanged(final String source) {
        assertThat(LeadingDash.apply(source, "Де ключ?")).isEqualTo(new Edit("Де ключ?", 0));
    }

    @Test
    void restore_targetAlreadyHasTheDash_isUnchanged() {
        assertThat(LeadingDash.apply("— Hi", "— Привіт").count()).isZero();
    }
}
