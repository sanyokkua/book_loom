package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * What the export's check against the neighbours reads and which paragraphs it reads: the paragraphs around as source
 * and translation, the glossary, the recurring terms, the characters and the summary, in prompt order, and the
 * doubted or — when asked — every paragraph besides the repaired and flagged ones.
 */
class ConsistencyPassNeighbourContextTest {

    private static final String PREVIOUS = "ch01.xhtml:6";
    private static final String NEXT = "ch01.xhtml:8";
    private static final String CONSISTENCY = "consistency";
    private static final String BEFORE = "Сем зустрів Гейла.";
    private static final PassOptions EXPORT = new PassOptions(true, false);

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        book.add(book.character("Sam", "Сем", Gender.FEMALE, false));
        book.decide(PREVIOUS, "Ранок.", "Ранок.");
        book.decide(NEXT, "Вечір.", "Вечір.");
    }

    private void repaired(final String text) {
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(text, text)
                        .withPath(SegmentPath.REPAIRED));
    }

    private void bookContext() {
        ok(book.desk()
                .lexicon()
                .put(new LexiconEntry(book.desk().projectId(), "line", List.of(), "рядок", null, null)));
        ok(book.desk()
                .summaries()
                .save(new RollingSummary(
                        book.desk().projectId(), null, "Sam meets Hale.", "Сем зустрічає Гейла.", 1, null, 0)));
    }

    // IF the check saw only the translations around it, THEN it could not tell a wrong rendering from a right one; the
    // owner asked for source and translation of both neighbours, names, terms, characters and the summary.
    @Test
    void run_repairedParagraph_promptCarriesNeighboursAndTheBookContextInOrder() {
        repaired(BEFORE);
        bookContext();
        book.model().answerTo(CONSISTENCY, reply(BEFORE));

        ok(book.runExport(EXPORT));

        assertThat(book.userMessage(0))
                .containsSubsequence(
                        "[Previous paragraph",
                        "Source: Chapter 1 line 6.\nTranslation: Ранок.",
                        "[Next paragraph",
                        "Source: Chapter 1 line 8.\nTranslation: Вечір.",
                        "[Names and terms]\n- Hale → Гейл, male\n- Sam → Сем, female",
                        "[Established renderings of recurring terms",
                        "line → рядок",
                        "[Characters in this text",
                        "Sam — female",
                        "[Book so far",
                        "Сем зустрічає Гейла.",
                        "<Source>",
                        "<Translation>\n" + BEFORE);
    }

    // IF the live call view got no description, THEN the export's calls would be invisible while the busy card waits.
    @Test
    void run_repairedParagraph_describesTheCallWithItsSectionsInPromptOrder() {
        repaired(BEFORE);
        bookContext();
        book.model().answerTo(CONSISTENCY, reply(BEFORE));
        final List<String> slots = new ArrayList<>();
        final ModelCalls calls = new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return book.model().chat(request);
            }

            @Override
            public Result<ChatResponse> callAbout(
                    final CallKind kind,
                    final List<String> segmentIds,
                    final ChatRequest request,
                    final CallDescriptor descriptor) {
                descriptor.sections().get().stream()
                        .filter(section -> section.origin() == PromptSection.Origin.USER)
                        .map(PromptSection::slot)
                        .forEach(slots::add);
                return book.model().chat(request);
            }
        };

        ok(book.runExport(calls, EXPORT));

        assertThat(slots)
                .containsSubsequence(
                        "previous", "next", "resolvedFacts", "lexiconTerms", "characters", "summary", "source", "text");
    }

    // IF an accepted paragraph the audit doubts were left out, THEN only the run's own trouble would ever be checked.
    @Test
    void run_acceptedParagraphTheAuditDoubts_isChecked() {
        book.decide(SAM_MET_HALE, "Сем зустріла зустріла його.", "Сем зустріла зустріла його.");
        book.model().answerTo(CONSISTENCY, reply(BEFORE));

        ok(book.runExport(EXPORT));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.userMessage(0)).contains("<Translation>\nСем зустріла зустріла його.");
    }

    // IF "every segment" were ignored, THEN a Max export would check only the doubted paragraphs.
    @Test
    void run_everySegment_checksEveryMachineParagraphOnce() {
        book.decide(SAM_MET_HALE, BEFORE, BEFORE);
        book.model()
                .answerTo(CONSISTENCY, reply("Ранок."))
                .answerTo(CONSISTENCY, reply(BEFORE))
                .answerTo(CONSISTENCY, reply("Вечір."));

        final ConsistencyReport report = ok(book.runExport(new PassOptions(true, true)));

        assertThat(book.model().requests()).hasSize(3);
        assertThat(report.checks().neighbourUnchanged()).isEqualTo(3);
    }

    // IF the default check read clean paragraphs, THEN every export would pay one call per paragraph of the book.
    @Test
    void run_exportWithoutEverySegment_leavesCleanParagraphsAlone() {
        book.decide(SAM_MET_HALE, BEFORE, BEFORE);

        ok(book.runExport(EXPORT));

        assertThat(book.model().requests()).isEmpty();
    }
}
