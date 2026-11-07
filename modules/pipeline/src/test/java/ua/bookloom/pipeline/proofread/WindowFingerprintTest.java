package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.context.TranslatePlanner;

/**
 * A proofread window's fingerprint (design D15 "Resume"), taken through the real planner so the map lines are the ones
 * the call would carry: a renamed target, an edited neighbour and the round each give a new one; the same window
 * planned twice gives the same one. Invented ru→uk lines.
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

    private static String fingerprint(final int round, final String ivarTarget, final Map<String, String> targets) {
        final ScriptedChatModel model = new ScriptedChatModel();
        final TranslatePlanner planner = ProofreadFixtures.planner(ProofreadFixtures.TEMPLATES, BOOK, BRIEF, model);
        final ProofreadCall call =
                new ProofreadCall(planner, ProofreadFixtures.calls(model, event -> {}), new ObjectMapper());
        final GlossaryEntry ivar = GlossaryEntry.of(
                ProofreadFixtures.PROJECT + ":Ивар",
                ProofreadFixtures.PROJECT,
                "Ивар",
                ivarTarget,
                TermType.CHARACTER,
                Gender.MALE,
                false);
        final ProofreadCall.Texts texts = texts(targets);
        final ProofreadCall.Planned planned = Objects.requireNonNull(
                call.plan(WINDOW, texts, ProofreadFixtures.map(List.of(ivar), BRIEF))
                        .data(),
                "planned");
        assertThat(planned.mapLines()).as("the call carries Ивар's line").contains(ivarTarget);
        return WindowFingerprint.of(round, WINDOW, texts, planned.mapLines());
    }

    private static ProofreadCall.Texts texts(final Map<String, String> targets) {
        final Map<String, String> sources = new HashMap<>();
        BOOK.units().getFirst().segments().forEach(segment -> sources.put(segment.id(), segment.masked()));
        return new ProofreadCall.Texts(sources, targets);
    }
}
