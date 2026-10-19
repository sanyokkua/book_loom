package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.NameWindows.Window;

/** The windows the review shows the model: where they come from, how many, and what pronoun each one proves. */
class NameWindowsTest {

    private static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry("id-" + term, "p1", term, null, type, Gender.UNKNOWN, false);
    }

    private static Evidence windowsOf(final GlossaryEntry entry, final List<String> paragraphs) {
        final BookSlice book = GlossaryTestSegments.of(paragraphs);
        return NameWindows.of(book.index(), book.ordinals(), List.of(entry), "en")
                .getOrDefault(entry.term(), Evidence.NONE);
    }

    // "Wren ran." used n times, one paragraph each, the paragraph number in the sentence before.
    private static List<String> paragraphs(final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "Day " + index + " came. Wren ran. The end.")
                .toList();
    }

    // 15i.N4: the window is the book index's, cut inside the use's own paragraph to the review's token budget; the
    // paragraph after is never read.
    @Test
    void of_oneUse_showsItsParagraphWithinTheBudgetAndNoOther() {
        final Evidence evidence = windowsOf(
                entry("Wren", TermType.CHARACTER),
                List.of("It rained. Then Wren ran home. The door shut. Night fell.", "Morning came grey and cold."));

        assertThat(evidence.windows())
                .extracting(Window::text)
                .containsExactly("It rained. Then Wren ran home. The door shut. Night fell.");
    }

    // About the old window's three sentences of up to 200 characters: a long paragraph is trimmed sentence by sentence
    // around the use, the sentence after first.
    @Test
    void of_useInALongParagraph_trimsTheWindowToTheBudgetAroundTheUse() {
        final String filler = "The long road ran on past the empty farms and the old stone walls.";
        final String paragraph = String.join(" ", java.util.Collections.nCopies(6, filler)) + " Then Wren stopped. "
                + String.join(" ", java.util.Collections.nCopies(6, filler.replace("road", "lane"))) + " The end came.";

        final Evidence evidence = windowsOf(entry("Wren", TermType.CHARACTER), List.of(paragraph));

        assertThat(NameWindows.WINDOW_TOKENS).isEqualTo(150);
        assertThat(evidence.windows()).hasSize(1);
        assertThat(evidence.windows().getFirst().text())
                .contains("Then Wren stopped.", filler, filler.replace("road", "lane"))
                .doesNotContain("The end came.");
        assertThat(ua.bookloom.pipeline.chunk.TokenEstimator.estimate(
                        evidence.windows().getFirst().text(), "en"))
                .isLessThanOrEqualTo(NameWindows.WINDOW_TOKENS);
    }

    @ParameterizedTest
    @CsvSource({"CHARACTER,10,4", "CHARACTER,31,6", "CHARACTER,30,4", "PLACE,10,2", "CHARACTER,2,2"})
    void of_uses_showsAsManyWindowsAsTheTypeAndCountAllow(final TermType type, final int uses, final int windows) {
        final Evidence evidence = windowsOf(entry("Wren", type), paragraphs(uses));

        assertThat(evidence.count()).isEqualTo(uses);
        assertThat(evidence.windows()).hasSize(windows);
    }

    // IF the windows were the first four uses, THEN a name would be judged from its opening scene only.
    @Test
    void of_tenUses_showsTheFirstThenOnePerStretchOfTheRest() {
        final Evidence evidence = windowsOf(entry("Wren", TermType.CHARACTER), paragraphs(10));

        assertThat(evidence.windows())
                .extracting(Window::text)
                .containsExactly(
                        "Day 0 came. Wren ran. The end.",
                        "Day 2 came. Wren ran. The end.",
                        "Day 5 came. Wren ran. The end.",
                        "Day 8 came. Wren ran. The end.");
    }

    // 15i.N4: the windows are the book index's choice — the first use, then the middle narration use of each stretch —
    // so a stretch whose middle use is speech shows its narration instead.
    @Test
    void of_stretchesWithSpeech_showTheMiddleNarrationUseOfEach() {
        // Uses: 0 N | 1 N, 2 S | 3 N, 4 S | 5 S, 6 N
        final List<String> book = List.of(
                "Day 0 came. Wren ran. The end.",
                "Day 1 came. Wren ran. The end.",
                "“Wren, come here,” Tovrin said on day 2.",
                "Day 3 came. Wren ran. The end.",
                "“Wren, come here,” Tovrin said on day 4.",
                "“Wren, come here,” Tovrin said on day 5.",
                "Day 6 came. Wren ran. The end.");

        final Evidence evidence = windowsOf(entry("Wren", TermType.CHARACTER), book);

        assertThat(evidence.windows())
                .extracting(Window::text)
                .containsExactly(
                        "Day 0 came. Wren ran. The end.",
                        "Day 1 came. Wren ran. The end.",
                        "Day 3 came. Wren ran. The end.",
                        "Day 6 came. Wren ran. The end.");
    }

    // The review still prefers, within a stretch, a use whose window gives the code a pronoun of the name, so a gender
    // the model claims can be checked against the windows it cites (15i.N1).
    @Test
    void of_aStretchWithANarrationPronoun_prefersThatWindow() {
        // The rest is cut into three stretches of two; the first stretch's middle use would be "Wren slept.".
        final List<String> book = List.of(
                "Wren came first.",
                "Wren stood. She left.",
                "Wren slept.",
                "Wren slept.",
                "Wren slept.",
                "Wren slept.",
                "Wren slept.");

        final Evidence evidence = windowsOf(entry("Wren", TermType.CHARACTER), book);

        assertThat(evidence.windows())
                .extracting(Window::text, Window::pronoun)
                .contains(tuple("Wren stood. She left.", Gender.FEMALE));
    }

    // IF the window ended before the pronoun the code read, THEN it would claim a pronoun it does not show: a sentence
    // of 514 characters is 148 tokens, and the next sentence would take the window past its 150.
    @Test
    void of_pronounBeyondTheWindowsBudget_givesTheWindowNoPronoun() {
        final String sentence =
                "Long ago " + String.join(" ", java.util.Collections.nCopies(123, "far")) + " Wren stopped.";

        final Evidence evidence = windowsOf(entry("Wren", TermType.CHARACTER), List.of(sentence + " She rested."));

        assertThat(sentence).hasSize(514);
        assertThat(evidence.windows()).singleElement().satisfies(window -> {
            assertThat(window.text()).isEqualTo(sentence);
            assertThat(window.pronoun()).isNull();
        });
        assertThat(evidence.pronouns().female()).isEqualTo(1);
    }

    // IF two uses in one short paragraph each gave a window, THEN the model would read the same text twice and a cited
    // pronoun could count twice: the review shows it once.
    @Test
    void of_twoUsesInOneShortParagraph_showOneWindow() {
        final Evidence evidence = windowsOf(
                entry("Wren", TermType.CHARACTER), List.of("Wren came in. Then Wren sat down.", "Wren left."));

        assertThat(evidence.count()).isEqualTo(3);
        assertThat(evidence.windows())
                .extracting(Window::text)
                .containsExactly("Wren came in. Then Wren sat down.", "Wren left.");
    }

    // IF the review's window grew backward first, THEN the scene before a use would crowd out the "He" after it and the
    // window would prove no gender: the 505-character sentence before fits with the use alone, not with "He rested."
    // too (150 tokens are 521 characters), so growing forward first keeps the pronoun.
    @Test
    void of_pronounInTheSentenceAfter_isKeptBeforeTheSentenceBefore() {
        final String before = "Long ago " + String.join(" ", java.util.Collections.nCopies(122, "far")) + " the end.";

        final Evidence evidence =
                windowsOf(entry("Wren", TermType.CHARACTER), List.of(before + " Wren stopped. He rested."));

        assertThat(before).hasSize(505);
        assertThat(evidence.windows())
                .extracting(Window::text, Window::pronoun)
                .containsExactly(tuple("Wren stopped. He rested.", Gender.MALE));
    }

    // The pronoun a window proves is still the code's, read for the use the window is cut around.
    @Test
    void of_windowWithANarrationPronoun_recordsItsGender() {
        final Evidence evidence =
                windowsOf(entry("Wren", TermType.CHARACTER), List.of("Wren came first.", "Wren stood. She left."));

        assertThat(evidence.windows())
                .extracting(Window::text, Window::pronoun)
                .containsExactly(tuple("Wren came first.", null), tuple("Wren stood. She left.", Gender.FEMALE));
    }

    // IF a speaker's pronoun proved a gender, THEN the model could cite Badger's "he" for Ember (Glass Harbour).
    @Test
    void of_pronounOnlyInsideStraightQuotedSpeech_provesNoGender() {
        final Evidence evidence =
                windowsOf(entry("Ember", TermType.CHARACTER), List.of("Ember nodded. \"He never pays,\" said Badger."));

        assertThat(evidence.windows()).extracting(Window::pronoun).containsOnlyNulls();
    }

    @Test
    void of_termTheBookNeverUses_hasNoWindows() {
        assertThat(windowsOf(entry("Quill", TermType.CHARACTER), List.of("Wren slept."))
                        .windows())
                .isEmpty();
    }

    // IF an object form counted against the name's own subject pronouns, THEN "Badger hit him" made Badger a man.
    @Test
    void of_objectFormWhileSubjectPronounsNameTheOtherGender_givesTheWindowNoPronoun() {
        final Evidence evidence = windowsOf(
                entry("Badger", TermType.CHARACTER),
                List.of(
                        "Badger hit him.",
                        "Badger laughed. She ran.",
                        "Badger laughed. She ran.",
                        "Badger laughed. She ran."));

        assertThat(evidence.windows())
                .extracting(Window::pronoun)
                .containsExactly(null, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE);
    }

    @Test
    void of_objectFormWithNoSubjectPronouns_stillGivesTheWindowItsGender() {
        final Evidence evidence = windowsOf(entry("Badger", TermType.CHARACTER), List.of("Badger hit him."));

        assertThat(evidence.windows()).extracting(Window::pronoun).containsExactly(Gender.MALE);
    }
}
