package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

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
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.ReviewFixtures;

/** The consistency pass's "nothing to change" answer and its memory of a checked paragraph (15h.E1, task 15h.B2). */
class ConsistencyPassUnchangedAnswerTest {

    private static final String CONSISTENCY = "consistency";
    private static final String BEFORE = "Сем зустрів Гейла.";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        book.decide("ch01.xhtml:6", "Ранок.", "Ранок.");
        book.decide("ch01.xhtml:8", "Вечір.", "Вечір.");
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(BEFORE, BEFORE)
                        .withPath(SegmentPath.REPAIRED));
    }

    private static Result<ChatResponse> unchanged() {
        return Result.ok(new ChatResponse("{\"unchanged\":true}", FinishReason.STOP));
    }

    // IF "unchanged" were read as a broken answer, THEN every checked paragraph is counted as a refusal.
    @Test
    void run_unchangedAnswer_isCountedAsUnchangedAndNotRefused() {
        book.model().answerTo(CONSISTENCY, unchanged());

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.checks().neighbourUnchanged()).isEqualTo(1);
        assertThat(report.checks().refused()).isEmpty();
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
    }

    // IF the call carried no target, THEN the live panel can only say "No translation" for an answer that kept it.
    @Test
    void run_neighbourCheck_describesTheCallWithTheTargetItChecks() {
        book.model().answerTo(CONSISTENCY, unchanged());
        final List<CallDescriptor> described = new ArrayList<>();
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
                described.add(descriptor);
                return book.model().chat(request);
            }
        };

        ok(book.runWith(calls));

        assertThat(described).hasSize(1);
        assertThat(described.getFirst().targets()).containsExactly(BEFORE);
    }

    // IF a checked paragraph were forgotten, THEN a second export of an unchanged book pays for the same calls again.
    @Test
    void run_secondPassOverAnUnchangedBook_sendsNoModelCall() {
        book.model().answerTo(CONSISTENCY, unchanged()).answerTo(CONSISTENCY, unchanged());
        ok(book.run(true));
        final int firstPass = book.model().requests().size();

        ok(book.run(true));

        assertThat(firstPass).isEqualTo(1);
        assertThat(book.model().requests()).hasSize(firstPass);
    }
}
