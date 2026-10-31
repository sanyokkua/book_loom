package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.pipeline.CallKind;

/** The answer cap and the reasoning reserve stay inside the window beside the prompt, never below the floor. */
class OutputRoomTest {

    @Test
    void split_noWindow_keepsCapAndReserve() {
        assertThat(OutputRoom.split(request(null, 4096, 3000), 3340)).isEqualTo(new OutputRoom.Split(4096, 3340));
    }

    @Test
    void split_fitsTheWindow_keepsCapAndReserve() {
        assertThat(OutputRoom.split(request(32768, 4096, 3000), 3340)).isEqualTo(new OutputRoom.Split(4096, 3340));
    }

    @Test
    void split_promptFillsTheWindow_givesTheFloorWithRoomToThink() {
        final OutputRoom.Split split = OutputRoom.split(request(1024, 500, 6000), 2048);

        assertThat(split.cap()).isNotNull().isPositive();
        assertThat(split.reserve()).isPositive();
        assertThat(split.cap() + split.reserve()).isEqualTo(256);
    }

    @Test
    void room_noWindow_isUnknown() {
        assertThat(OutputRoom.room(request(null, 500, 10))).isNull();
    }

    private static ChatRequest request(final @Nullable Integer window, final int cap, final int promptChars) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "a".repeat(promptChars))),
                null,
                null,
                ReasoningLevel.OFF,
                window,
                null,
                cap,
                null,
                CallKind.DRAFT);
    }
}
