package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.heal.ReflectImprove;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The reflect call reaches the wire with its own output cap, one per dialect: with none, gemma4:e4b once streamed a
 * critique for three minutes until the timeout.
 */
class ReflectWireMockTest {

    private static final ReflectImprove REFLECT_IMPROVE =
            new ReflectImprove(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()), new ObjectMapper());
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String SOURCE = "She walked into the room.";
    private static final String CANDIDATE = "Вона зайшла в кімнату.";
    private static final String ISSUES = "{\"issues\":[{\"note\":\"Too flat.\",\"suggestion\":\"Add a word.\"}]}";

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void reflect_anyDialect_postsItsOutputCap(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(provider.reply(ISSUES, Duration.ZERO)));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});

            final var issues = REFLECT_IMPROVE.reflect(
                    segment(), FRAME, SOURCE, CANDIDATE, (call, id, request) -> model.chat(request));

            assertThat(issues.data()).hasSize(1);
            assertBody(kind, provider.chatBodies().getFirst());
        }
    }

    private static void assertBody(final ProviderKind kind, final String body) {
        switch (kind) {
            case OLLAMA -> assertThat(body).contains("\"num_predict\":600");
            case OPENAI_COMPATIBLE -> assertThat(body).contains("\"max_tokens\":600");
        }
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
