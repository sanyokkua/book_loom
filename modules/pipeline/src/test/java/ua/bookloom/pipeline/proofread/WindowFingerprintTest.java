package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.context.BookMap;

/**
 * A proofread window's fingerprint (design D15 "Resume"), taken from the map lines the window names with no planner, so
 * the stored project's tally takes the same one: a renamed target, an edited neighbour, a new mark, the narrator's
 * gender and the round each give a new one; the same window taken twice gives the same one. Invented ru→uk lines.
 */
class WindowFingerprintTest {

    private static final String BEFORE = "ch02.md:15";
    private static final String FOCUS = "ch02.md:16";
    private static final String AFTER = "ch02.md:17";
    private static final BookBrief BRIEF = ProofreadFixtures.brief("ru", "uk");
    private static final Document BOOK = ProofreadFixtures.chapter(
            "ch02.md",
            ProofreadFixtures.lines(
                    18, "15", "Тарн молчал у двери.", "16", "Ивар сказал, что вернётся.", "17", "Никто не ответил."));
    private static final ProofreadWindow WINDOW = new ProofreadWindow(List.of(FOCUS), Map.of(), BEFORE, AFTER);
    private static final Map<String, String> TARGETS = Map.of(
            BEFORE, "Тарн мовчав біля дверей.", FOCUS, "Івар сказав, що повернеться.", AFTER, "Ніхто не відповів.");

    @Test
    void fingerprint_sameWindowPlannedTwice_equal() {
        assertThat(fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", TARGETS))
                .isEqualTo(fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", TARGETS));
    }

    // The person changes the target of Ивар after the window was read: the map line the call carries changes.
    @Test
    void fingerprint_targetRenamed_changes() {
        assertThat(fingerprint(WindowFingerprint.ROUND_MARKED, "Іван", TARGETS))
                .isNotEqualTo(fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", TARGETS));
    }

    @Test
    void fingerprint_neighbourEdited_changes() {
        final Map<String, String> edited = new HashMap<>(TARGETS);
        edited.put(AFTER, "Ніхто так і не відповів.");

        assertThat(fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", edited))
                .isNotEqualTo(fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", TARGETS));
    }

    // The same text read in round 1 and again in Max's whole-book round is two different questions.
    @Test
    void fingerprint_round_differs() {
        final String first = fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", TARGETS);
        final String whole = fingerprint(WindowFingerprint.ROUND_WHOLE_BOOK, "Івар", TARGETS);

        assertThat(whole).isNotEqualTo(first);
        assertThat(WindowFingerprint.isWholeBook(whole)).isTrue();
        assertThat(WindowFingerprint.isWholeBook(first)).isFalse();
    }

    // A new mark on the window's segment changes what block 13 asks, so the window is read again.
    @Test
    void fingerprint_newReason_changes() {
        final ProofreadWindow marked = new ProofreadWindow(
                List.of(FOCUS), Map.of(FOCUS, List.of("gender now known: Івар — female")), BEFORE, AFTER);

        assertThat(fingerprint(WindowFingerprint.ROUND_MARKED, marked, BRIEF, "Івар", TARGETS))
                .isNotEqualTo(fingerprint(WindowFingerprint.ROUND_MARKED, "Івар", TARGETS));
    }

    @Test
    void fingerprint_narratorGenderSet_changes() {
        final BookBrief female = BRIEF.withNarrator(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
        final BookBrief male = BRIEF.withNarrator(new Narrator(NarratorPerson.FIRST, Gender.MALE));

        assertThat(fingerprint(WindowFingerprint.ROUND_MARKED, WINDOW, female, "Івар", TARGETS))
                .isNotEqualTo(fingerprint(WindowFingerprint.ROUND_MARKED, WINDOW, male, "Івар", TARGETS));
    }

    private static String fingerprint(final int round, final String ivarTarget, final Map<String, String> targets) {
        return fingerprint(round, WINDOW, BRIEF, ivarTarget, targets);
    }

    private static String fingerprint(
            final int round,
            final ProofreadWindow window,
            final BookBrief brief,
            final String ivarTarget,
            final Map<String, String> targets) {
        final GlossaryEntry ivar = GlossaryEntry.of(
                ProofreadFixtures.PROJECT + ":Ивар",
                ProofreadFixtures.PROJECT,
                "Ивар",
                ivarTarget,
                TermType.CHARACTER,
                Gender.MALE,
                false);
        return WindowFingerprint.of(
                round, window, texts(targets), BookMap.of(List.of(ivar), List.of(), brief, BOOK.units()), brief);
    }

    private static ProofreadCall.Texts texts(final Map<String, String> targets) {
        final Map<String, String> sources = new HashMap<>();
        BOOK.units().getFirst().segments().forEach(segment -> sources.put(segment.id(), segment.masked()));
        return new ProofreadCall.Texts(sources, targets);
    }
}
