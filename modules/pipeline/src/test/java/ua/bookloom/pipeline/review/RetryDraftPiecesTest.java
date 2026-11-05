package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.review.RetryDraftTest.DRAFT;
import static ua.bookloom.pipeline.review.RetryDraftTest.NOTE;
import static ua.bookloom.pipeline.review.RetryDraftTest.SNAPSHOT;
import static ua.bookloom.pipeline.review.RetryDraftTest.formatOf;
import static ua.bookloom.pipeline.review.RetryDraftTest.ok;
import static ua.bookloom.pipeline.review.RetryDraftTest.reply;
import static ua.bookloom.pipeline.review.RetryDraftTest.userMessage;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** A segment a run drafts in pieces is retried in the same pieces, each carrying the person's note. */
class RetryDraftPiecesTest {

    // A piece draft: the draft prompt at the lower temperature.
    private static final Tuple PIECE = tuple(DRAFT, 0.1);

    @TempDir
    private Path tempDir;

    @Test
    void retry_segmentTheRunDraftsInPieces_draftsEachPieceWithTheNoteAndJoinsThem() {
        // 400 sentences are above the small tier's current part of 600 tokens: six piece drafts,
        // each at the lower temperature with the note, joined into one target that the reviewer then lets stand
        final String source = IntStream.range(100, 500)
                .mapToObj(number -> "The night number " + number + " was calm.")
                .collect(Collectors.joining(" "));
        final Desk desk = ReviewFixtures.markdown(tempDir, source + "\n");
        latestRun(desk, JobState.PAUSED);
        flag(desk, "Book.md:0", "Ніч.");
        withContext(desk, "Book.md:0", SNAPSHOT);
        final PieceTranslatingModel model = new PieceTranslatingModel();

        final SegmentRecord record =
                ok(desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), "Book.md:0", NOTE, true, model));

        assertThat(model.requests)
                .extracting(RetryDraftTest::formatOf, ChatRequest::temperature)
                .containsExactly(PIECE, PIECE, PIECE, PIECE, PIECE, PIECE, tuple("reviewer", 0.0));
        assertThat(model.requests.subList(0, 6))
                .allSatisfy(draft -> assertThat(userMessage(draft)).contains("[Extra instruction]\n" + NOTE));
        assertThat(userMessage(model.requests.getFirst())).contains("The night number 100 was calm.");
        assertThat(userMessage(model.requests.get(5))).contains("The night number 499 was calm.");
        assertThat(record)
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::reviewed)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.DRAFT, true);
        assertThat(record.machineTarget())
                .isEqualTo(IntStream.range(100, 500)
                        .mapToObj(number -> "Ніч номер " + number + " була дуже тихою.")
                        .collect(Collectors.joining(" ")));
    }

    /** Translates each night sentence of whatever text a draft shows it, and accepts whatever the reviewer is shown. */
    private static final class PieceTranslatingModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<s id=\"1\">(.*?)</s>", Pattern.DOTALL);
        private static final Pattern NIGHT = Pattern.compile("The night number (\\d+) was calm\\.");
        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final Matcher body = TEXT.matcher(userMessage(request));
            return "reviewer".equals(formatOf(request)) || !body.find()
                    ? Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP))
                    : reply(NIGHT.matcher(body.group(1)).replaceAll("Ніч номер $1 була дуже тихою."));
        }
    }
}
