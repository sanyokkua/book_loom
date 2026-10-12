package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.FOUR_IDS;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.entries;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.firstReply;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.reasked;
import static ua.bookloom.pipeline.batch.BatchDrafterTest.reply;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;

/** Which first replies move the adaptive batch size: a stop after item one and a cut reply do not. */
class BatchSizeSignalTest {

    private final List<List<String>> segments = new ArrayList<>();

    private BatchDrafter scripted(final List<Result<ChatResponse>> answers) {
        return BatchDrafterTest.scriptedDrafter(answers, segments);
    }

    // IF the model keeps answering only the first item, THEN the size neither shrinks nor creeps up as for a clean run.
    @Test
    void record_firstItemOnlyRepliesInARow_neitherShrinkNorGrowTheSize() {
        final BatchDrafter drafter = scripted(List.of(
                reply(entries("1", "Вона відчинила двері.")),
                reply(entries("1", "Вона відчинила двері.")),
                reply(entries("1", "Вона відчинила двері.")),
                reply(entries("1", "Вона відчинила двері."))));

        drafter.record(firstReply(drafter));
        drafter.record(firstReply(drafter));
        drafter.record(firstReply(drafter));
        drafter.record(firstReply(drafter));

        assertThat(drafter.size()).isEqualTo(8);
    }

    // IF the reply was cut at the length cap, THEN the missing ids are asked again as for any omission, and the size
    // stays: the cap is sized from the items, so a cut is a loop in the reply, not too many items.
    @Test
    void record_replyCutAtTheLengthCap_keepsTheSizeAndTheMissingIdsAreStillAskedAgain() {
        final BatchDrafter drafter = scripted(List.of(
                Result.ok(new ChatResponse(
                        entries("1", "Вона відчинила двері.", "2", "Він мовчав."), FinishReason.LENGTH)),
                reply(entries("3", "Дощ припинився.", "4", "Настала ніч."))));
        final BatchReply first = firstReply(drafter);

        final BatchReply merged = reasked(drafter, first);
        drafter.record(first);

        assertThat(segments).containsExactly(FOUR_IDS, List.of("s3", "s4"));
        assertThat(merged.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(drafter.size()).isEqualTo(8);
    }

    // IF a reply names an id the batch never held, THEN the numbering is lost and the size halves, as before.
    @Test
    void record_replyWithAnExtraId_halvesTheSize() {
        final BatchDrafter drafter = scripted(List.of(reply(entries(
                "1", "Вона відчинила двері.",
                "2", "Він мовчав.",
                "3", "Дощ припинився.",
                "4", "Настала ніч.",
                "9", "Зайве."))));

        drafter.record(firstReply(drafter));

        assertThat(drafter.size()).isEqualTo(4);
    }
}
