package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The code's triage of the name candidates (15i.N3) seen from the names suite: the candidates the model name scan is
 * shown, and what Names &amp; style leaves of a book, with the scripted model of {@link NamesFakeModel}.
 */
class NamesTriageEvalTest {

    private static final Pattern CANDIDATE = Pattern.compile("^(.+?) — ");
    private static final List<String> HEADINGS =
            List.of("Power Supply Unit", "Signal Path", "Gain Stage", "Output Stage");

    @TempDir
    private Path dir;

    /** A scripted model that remembers the candidate terms each model name scan listed. */
    private static final class Recording implements ChatModel {

        private final NamesFakeModel inner;
        private final List<String> candidates = new CopyOnWriteArrayList<>();

        Recording(final NamesBook book) {
            this.inner = new NamesFakeModel(book);
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            final boolean isNameScan = request.responseFormat() != null
                    && request.responseFormat().name().equals(PromptName.PRESCAN.responseFormatName());
            if (isNameScan) {
                candidates.addAll(candidateTerms(request.messages().getLast().content()));
            }
            return inner.chat(request);
        }

        List<String> candidates() {
            return List.copyOf(candidates);
        }
    }

    // The scan prompt lists "[Candidates ...", then one "term — first sentence" line per candidate, then a blank line.
    private static List<String> candidateTerms(final String user) {
        final List<String> terms = new ArrayList<>();
        boolean listed = false;
        for (final String line : (Iterable<String>) user.lines()::iterator) {
            if (line.startsWith("[Candidates")) {
                listed = true;
            } else if (line.isBlank()) {
                listed = false;
            } else if (listed) {
                final Matcher matcher = CANDIDATE.matcher(line);
                if (matcher.find()) {
                    terms.add(matcher.group(1));
                }
            }
        }
        return terms;
    }

    /**
     * The committed technical book, plus a third run of its four headings and one sentence per heading that names the
     * phrase with capitals in running text, as a manual does ("the manual names the Gain Stage"): each phrase is then
     * mentioned three times as a heading and once in the story, so the scan counts it once in running text.
     */
    private static NamesBook technicalBookWithPhrasesInText() {
        final NamesBook committed = NamesBook.byId("technical-headings");
        final List<String> paragraphs =
                new ArrayList<>(committed.chapters().getFirst().paragraphs());
        HEADINGS.forEach(heading -> {
            paragraphs.add(heading);
            paragraphs.add("The manual names the " + heading + " as the next thing to check.");
        });
        return new NamesBook(
                committed.id(),
                committed.about(),
                committed.title(),
                committed.author(),
                List.of(new Fb2Fixture.Section(committed.chapters().getFirst().title(), paragraphs)),
                committed.gold(),
                committed.model(),
                false,
                null);
    }

    // IF the headings of a technical chapter are also written with capitals once in running text, THEN today the model
    // name scan is shown five candidates (the woman and the four phrases); with the triage it is shown the woman only,
    // and the gold name is not lost.
    @Test
    void run_technicalHeadingsAlsoNamedInText_showsTheModelScanOnlyTheRealName() {
        final NamesBook book = technicalBookWithPhrasesInText();
        final Recording model = new Recording(book);

        final NamesScore score = new NamesRunner(model).run(book, dir).score();

        assertThat(model.candidates()).containsExactly("Iolanthe");
        assertThat(score.lostNames()).isEmpty();
        assertThat(score.junkKept()).isZero();
    }

    // IF the committed technical book is scanned, THEN its only candidate is still the woman: the title-line
    // paragraphs were never candidates, and the triage must not lose her.
    @Test
    void run_committedTechnicalHeadings_showsTheModelScanOnlyTheWomanAndLosesNoName() {
        final NamesBook book = NamesBook.byId("technical-headings");
        final Recording model = new Recording(book);

        final NamesScore score = new NamesRunner(model).run(book, dir).score();

        assertThat(model.candidates()).containsExactly("Iolanthe");
        assertThat(score.lostNames()).isEmpty();
    }

    // IF a shop is written "Orrin & Vale" in nearly every use and a camera brand in capitals and in mixed case, THEN
    // the shop is one name, the camera is one name with the capitals as its alias, and no half is left as junk. The
    // book stays flagged as a known failure for the coder to unflag; this asserts the end state independently.
    @Test
    void run_brandAndCamera_keepsTheShopAsOneNameAndTheCameraOnceWithNoJunk() {
        final NamesBook book = NamesBook.byId("brand-and-camera");

        final NamesScore score =
                new NamesRunner(new NamesFakeModel(book)).run(book, dir).score();

        assertThat(score.lostNames()).isEmpty();
        assertThat(score.junkKept()).isZero();
    }

    // IF the shop and the camera are triaged by code, THEN the model name scan is shown them as two candidates and
    // not as their halves or their capitals variant.
    @Test
    void run_brandAndCamera_showsTheModelScanTheShopAndTheCameraNotTheirHalves() {
        final NamesBook book = NamesBook.byId("brand-and-camera");
        final Recording model = new Recording(book);

        new NamesRunner(model).run(book, dir);

        assertThat(model.candidates()).contains("Anneke", "Orrin & Vale", "Kessel Optik");
        assertThat(model.candidates()).doesNotContain("Orrin", "Vale", "KESSEL OPTIK");
    }
}
