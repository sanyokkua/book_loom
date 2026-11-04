package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.FOUR;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.FOUR_IDS;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.FOUR_SOURCES;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.entries;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.reply;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.scriptedDrafter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;

/** The adaptive batch size never rises above the size it started at (redesign group 2). */
class BatchDrafterCeilingTest {

    @Test
    void size_startedAtEightAfterThirtyCleanBatches_staysEight() {
        final String clean = entries(
                "1", "Вона відчинила двері.", "2", "Він нічого не казав.", "3", "Дощ вщух.", "4", "Настала ніч.");
        final List<Result<ChatResponse>> answers = Collections.nCopies(30, reply(clean));
        final BatchDrafter drafter = scriptedDrafter(answers, new ArrayList<>());
        assertThat(drafter.size()).isEqualTo(8);

        IntStream.range(0, 30)
                .forEach(round -> drafter.record(Objects.requireNonNull(
                        drafter.draft(BatchContext.empty(), FOUR, FOUR_IDS).data(), "reply")));

        assertThat(drafter.size()).isEqualTo(8);
        assertThat(FOUR_SOURCES).hasSize(4);
    }

    @Test
    void size_afterHalvingThreeCleanBatches_growsBackOnlyToProfileSize() {
        final String clean = entries(
                "1", "Вона відчинила двері.", "2", "Він нічого не казав.", "3", "Дощ вщух.", "4", "Настала ніч.");
        final String lostOne = entries("1", "Вона відчинила двері.", "2", "Він нічого не казав.", "3", "Дощ вщух.");
        final List<Result<ChatResponse>> answers = new ArrayList<>(List.of(reply(lostOne)));
        answers.addAll(Collections.nCopies(9, reply(clean)));
        final BatchDrafter drafter = scriptedDrafter(answers, new ArrayList<>(), 4);

        final List<Integer> sizes = IntStream.range(0, 10)
                .mapToObj(round -> {
                    drafter.record(Objects.requireNonNull(
                            drafter.draft(BatchContext.empty(), FOUR, FOUR_IDS).data(), "reply"));
                    return drafter.size();
                })
                .toList();

        assertThat(sizes).containsExactly(2, 2, 2, 3, 3, 3, 4, 4, 4, 4);
    }

    @Test
    void size_noReply_unchanged() {
        final Result<ChatResponse> failed = Result.err(AppError.of(ErrorCode.timeout, "Timed out", "No reply."));
        final BatchDrafter drafter = scriptedDrafter(List.of(failed), new ArrayList<>(), 4);

        final Result<BatchReply> result = drafter.draft(BatchContext.empty(), FOUR, FOUR_IDS);

        assertThat(result.isErr()).isTrue();
        assertThat(drafter.size()).isEqualTo(4);
    }
}
