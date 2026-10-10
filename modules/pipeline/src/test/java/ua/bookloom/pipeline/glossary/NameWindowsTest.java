package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Segment;
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
        final List<Segment> book = GlossaryTestSegments.of(paragraphs);
        return NameWindows.of(book, List.of(entry), "en").getOrDefault(entry.term(), Evidence.NONE);
    }

    // "Wren ran." used n times, one paragraph each, the paragraph number in the sentence before.
    private static List<String> paragraphs(final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "Day " + index + " came. Wren ran. The end.")
                .toList();
    }

    @Test
    void of_oneUse_showsTheSentenceWithOneBeforeAndOneAfter() {
        final Evidence evidence = windowsOf(
                entry("Wren", TermType.CHARACTER),
                List.of("It rained. Then Wren ran home. The door shut. Night fell."));

        assertThat(evidence.windows())
                .extracting(Window::text)
                .containsExactly("It rained. Then Wren ran home. The door shut.");
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

    // IF a speaker's pronoun proved a gender, THEN the model could cite Tiger's "he" for Chrome (Burning Chrome).
    @Test
    void of_pronounOnlyInsideStraightQuotedSpeech_provesNoGender() {
        final Evidence evidence = windowsOf(
                entry("Chrome", TermType.CHARACTER), List.of("Chrome nodded. \"He never pays,\" said Tiger."));

        assertThat(evidence.windows()).extracting(Window::pronoun).containsOnlyNulls();
        assertThat(evidence.shows(List.of(1), Gender.MALE)).isFalse();
    }

    @Test
    void shows_citedWindowWithThatGender_isTrueAndOtherNumbersAreIgnored() {
        final Evidence evidence =
                windowsOf(entry("Wren", TermType.CHARACTER), List.of("Wren slept.", "Wren stood. She left."));

        assertThat(evidence.shows(List.of(2), Gender.FEMALE)).isTrue();
        assertThat(evidence.shows(List.of(1, 7, 0), Gender.FEMALE)).isFalse();
        assertThat(evidence.shows(List.of(2), Gender.MALE)).isFalse();
    }

    @Test
    void of_termTheBookNeverUses_hasNoWindows() {
        assertThat(windowsOf(entry("Quill", TermType.CHARACTER), List.of("Wren slept."))
                        .windows())
                .isEmpty();
    }

    // IF an object form counted against the name's own subject pronouns, THEN "Tiger hit him" made Tiger a man.
    @Test
    void of_objectFormWhileSubjectPronounsNameTheOtherGender_givesTheWindowNoPronoun() {
        final Evidence evidence = windowsOf(
                entry("Tiger", TermType.CHARACTER),
                List.of(
                        "Tiger hit him.",
                        "Tiger laughed. She ran.",
                        "Tiger laughed. She ran.",
                        "Tiger laughed. She ran."));

        assertThat(evidence.windows())
                .extracting(Window::pronoun)
                .containsExactly(null, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE);
        assertThat(evidence.shows(List.of(1), Gender.MALE)).isFalse();
    }

    @Test
    void of_objectFormWithNoSubjectPronouns_stillGivesTheWindowItsGender() {
        final Evidence evidence = windowsOf(entry("Tiger", TermType.CHARACTER), List.of("Tiger hit him."));

        assertThat(evidence.windows()).extracting(Window::pronoun).containsExactly(Gender.MALE);
    }
}
