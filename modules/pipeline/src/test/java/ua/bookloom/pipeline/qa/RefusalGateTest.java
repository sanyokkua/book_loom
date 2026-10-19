package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.DisplayText;

/** {@link RefusalGate}: an apology or a task comment must never reach the book, whatever its confidence score. */
class RefusalGateTest {

    private static final String BENIGN_SOURCE = "Original line.";
    private static final String OPEN_DOOR_SOURCE = "He opened the old door.";
    private static final String EN = "en";
    private static final String UK = "uk";
    private static final String EN_REFUSAL = "I'm sorry, but I can't translate this text.";
    private static final String UK_REFUSAL = "Вибачте, я не можу перекласти цей текст.";
    private static final String UK_UNRELATED_OPENER = "Ось переклад: Він відчинив старі двері.";
    private static final String CURLY_APOSTROPHE_REFUSAL = "I’m sorry, but I can’t translate this.";

    // The masked reply the gate actually reads a display text from — the placeholder tokens must not shield the
    // phrase they flank.
    private static final String DISPLAY_TEXT_REFUSAL = DisplayText.of("⟦g0⟧I cannot translate this.⟦g1⟧");

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusingCases")
    void run_refusalPhraseOrEmptyTarget_failsHardGate(
            final String name,
            final String source,
            final String target,
            @Nullable final String sourceLang,
            final String targetLang) {
        final CheckResult result = RefusalGate.run(SoftCheckFixtures.refusal(source, target, sourceLang, targetLang));

        assertThat(result.check()).isEqualTo(CheckName.REFUSAL);
        assertThat(result.passed()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().severity()).isEqualTo(Severity.HIGH);
        assertThat(result.finding().kind()).isEqualTo("meaning");
        assertThat(result.finding().raisedBy()).isEqualTo("refusal");
    }

    private static Stream<Arguments> refusingCases() {
        return Stream.of(
                Arguments.of("an English refusal opener fails", BENIGN_SOURCE, EN_REFUSAL, null, UK),
                Arguments.of("a Ukrainian refusal opener fails", BENIGN_SOURCE, UK_REFUSAL, EN, UK),
                Arguments.of("an unrelated Ukrainian opener fails", BENIGN_SOURCE, UK_UNRELATED_OPENER, EN, UK),
                Arguments.of(
                        "a curly-apostrophe refusal still fails", BENIGN_SOURCE, CURLY_APOSTROPHE_REFUSAL, null, UK),
                Arguments.of("an empty target with a non-empty source fails", OPEN_DOOR_SOURCE, "", EN, UK),
                Arguments.of(
                        "the gate reads the display text, not the masked text",
                        OPEN_DOOR_SOURCE,
                        DISPLAY_TEXT_REFUSAL,
                        EN,
                        UK));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonRefusingCases")
    void run_noRefusalPhrase_passes(
            final String name,
            final String source,
            final String target,
            @Nullable final String sourceLang,
            final String targetLang) {
        final CheckResult result = RefusalGate.run(SoftCheckFixtures.refusal(source, target, sourceLang, targetLang));

        assertThat(result.passed()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.finding()).isNull();
    }

    private static Stream<Arguments> nonRefusingCases() {
        return Stream.of(
                Arguments.of(
                        "a real apology in context passes",
                        "Sorry, I can't go with you.",
                        "Вибачте, я не можу піти з вами.",
                        EN,
                        UK),
                Arguments.of("an unrelated regret passes", BENIGN_SOURCE, "Мені шкода, — сказав він.", EN, UK),
                Arguments.of(
                        "a Ukrainian word boundary keeps Як ШІ from matching",
                        BENIGN_SOURCE,
                        "Як шість років тому він пішов.",
                        EN,
                        UK),
                Arguments.of(
                        "an English word boundary keeps As an AI from matching",
                        BENIGN_SOURCE,
                        "As an aide, she opened the door.",
                        EN,
                        UK),
                Arguments.of(
                        "a source that itself opens with a listed phrase skips the target",
                        "Translation: the art of carrying meaning.",
                        "Переклад: мистецтво перенесення змісту.",
                        EN,
                        UK));
    }
}
