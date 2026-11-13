package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;

/** A repeat's seed reaches every request that has none, and a request's own seed is never overwritten. */
class SeededModelTest {

    private static final ChatRequest UNSEEDED = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate.")));

    @Test
    void chat_requestWithoutSeed_isSentWithBasePlusRepeat() {
        final Recording inner = new Recording(null);

        SeededModel.of(inner, 1000, 2).chat(UNSEEDED);

        assertThat(inner.seeds).containsExactly(1002);
    }

    @Test
    void chat_requestWithSeed_keepsItsOwn() {
        final Recording inner = new Recording(null);

        SeededModel.of(inner, 1000, 2).chat(UNSEEDED.withSeed(7));

        assertThat(inner.seeds).containsExactly(7);
    }

    @Test
    void contextWindowOverride_innerModelHasOne_isForwarded() {
        assertThat(SeededModel.of(new Recording(8192), 1000, 0).contextWindowOverride())
                .isEqualTo(8192);
    }

    /** Keeps the seed of every request it is sent and answers each with one fixed reply. */
    private static final class Recording implements ChatModel {

        private final List<Integer> seeds = new CopyOnWriteArrayList<>();
        private final @Nullable Integer window;

        Recording(@Nullable final Integer window) {
            this.window = window;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            seeds.add(Objects.requireNonNullElse(request.seed(), -1));
            return Result.ok(new ChatResponse("ok", FinishReason.STOP));
        }

        @Override
        public @Nullable Integer contextWindowOverride() {
            return window;
        }
    }
}
