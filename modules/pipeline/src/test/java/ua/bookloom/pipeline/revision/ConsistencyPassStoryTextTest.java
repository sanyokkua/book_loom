package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.ReviewFixtures;

/** Which paragraphs the pass sends to the model: story text only, and nothing a check already settled (15j.J11). */
class ConsistencyPassStoryTextTest {

    private static final String UNCHANGED = "{\"unchanged\":true}";
    private static final String FRONT = "copyright.xhtml:0";
    private static final String NAV = "aux:nav:1";
    private static final String TITLE = "aux:title";
    private static final String REPAIRED_BODY = "ch01.xhtml:0";
    private static final String FLAGGED_BODY = "ch01.xhtml:1";
    private static final PassOptions MAX_EXPORT = new PassOptions(true, true);
    private static final PassOptions BALANCED_EXPORT = new PassOptions(true, false);

    @TempDir
    private Path tempDir;

    private RevisionBook book;
    private final List<String> sent = new CopyOnWriteArrayList<>();

    @BeforeEach
    void openBook() {
        book = RevisionBook.openWithMatter(
                tempDir, List.of("All rights reserved."), List.of("First line.", "Second line.", "Third line."));
        book.decide(FRONT, "Усі права захищено.", "Усі права захищено.");
        book.decide(NAV, "Перша мітка", "Перша мітка");
        book.decide(TITLE, "Тест", "Тест");
        book.decide(FLAGGED_BODY, "Другий рядок.", "Другий рядок.");
        ReviewFixtures.update(
                book.desk(),
                REPAIRED_BODY,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget("Перший рядок.", "Перший рядок.")
                        .withPath(SegmentPath.REPAIRED));
        book.model().answerTimes(8, Result.ok(new ChatResponse(UNCHANGED, FinishReason.STOP)));
    }

    private ModelCalls recording() {
        return (kind, segmentId, request) -> {
            sent.add(segmentId);
            return book.model().chat(request);
        };
    }

    private void flagEverythingDecidedExceptTheRepaired() {
        List.of(FRONT, NAV, TITLE, FLAGGED_BODY)
                .forEach(id ->
                        ReviewFixtures.update(book.desk(), id, record -> record.withStatus(SegmentStatus.FLAGGED)));
    }

    // IF the pass sent matter to the model, THEN a contents page and a copyright line cost a call each, as the owner's
    // Max export showed; IF it ignored what the run's own backward revision settled, THEN every paragraph is read
    // twice.
    @Test
    void runExport_maxAfterTheBackwardRevision_sendsOnlyTheFlaggedBodyParagraph() {
        ok(book.runWith(recording()));
        assertThat(sent).containsExactly(REPAIRED_BODY);
        sent.clear();
        flagEverythingDecidedExceptTheRepaired();

        ok(book.runExport(recording(), MAX_EXPORT));

        assertThat(sent).containsExactly(FLAGGED_BODY);
    }

    @Test
    void runExport_balancedWithFlaggedMatter_sendsTheFlaggedAndRepairedBodyParagraphsOnly() {
        flagEverythingDecidedExceptTheRepaired();

        ok(book.runExport(recording(), BALANCED_EXPORT));

        assertThat(sent).containsExactlyInAnyOrder(REPAIRED_BODY, FLAGGED_BODY);
    }
}
