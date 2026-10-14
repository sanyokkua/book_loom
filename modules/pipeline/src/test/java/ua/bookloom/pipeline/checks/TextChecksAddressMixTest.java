package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The address-mix check runs among the deterministic text checks for a Ukrainian target, soft, and for no other. */
class TextChecksAddressMixTest {

    private static final String SOURCE = "“You are tired, Nell. Sit down, you need rest, and I will bring you tea.”";
    private static final String MIXED = "«Ти втомилася, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»";
    private static final String INFORMAL = "«Ти втомилася, Нелл. Сядь, тобі треба відпочити, а я принесу тобі чаю.»";
    private static final String FORMAL = "«Ви втомилися, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»";

    private static List<CheckFinding> addressMix(final String target, final String targetLanguage) {
        return TextChecks.run(SOURCE, target, "en", targetLanguage, List.of(), List.of()).stream()
                .filter(finding -> finding.kind() == FindingKind.ADDRESS_MIX)
                .toList();
    }

    @Test
    void run_ukrainianTargetMixingTheForms_raisesOneSoftAddressMix() {
        assertThat(addressMix(MIXED, "uk"))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.ADDRESS_MIX, false));
    }

    @Test
    void run_ukrainianTargetMixingTheForms_addsNoBlockingFinding() {
        assertThat(TextChecks.run(SOURCE, MIXED, "en", "uk", List.of(), List.of()))
                .noneMatch(CheckFinding::blocking);
    }

    @Test
    void run_ukrainianTargetAddressedOneWay_raisesNoAddressMix() {
        assertThat(addressMix(INFORMAL, "uk")).isEmpty();
        assertThat(addressMix(FORMAL, "uk")).isEmpty();
    }

    @Test
    void run_polishTargetWithTheSameText_raisesNoAddressMix() {
        assertThat(addressMix(MIXED, "pl")).isEmpty();
    }
}
