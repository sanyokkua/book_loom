package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code Deferral} keeps both proposal forms.
 */
class DeferralTest {

    @Test
    void constructor_bothProposalForms_areKept() {
        final Deferral deferral =
                new Deferral("d1", "p1", "ch1:0", DeferralReason.TERM, null, "Хата", "Дім", "⟦g0⟧Дім⟦g1⟧");

        assertThat(deferral.proposal()).isEqualTo("Дім");
        assertThat(deferral.maskedProposal()).isEqualTo("⟦g0⟧Дім⟦g1⟧");
    }
}
