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
}
