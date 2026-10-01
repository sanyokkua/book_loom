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

/** The improve call reaches the wire at its own temperature and context, one per dialect. */
class ImproveWireMockTest {

    private static final ReflectImprove REFLECT_IMPROVE =
            new ReflectImprove(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()), new ObjectMapper());
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String SOURCE = "She walked into the room.";
    private static final String CANDIDATE = "Вона зайшла в кімнату.";
    private static final String IMPROVED = "Вона тихо зайшла в кімнату.";

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void improve_anyDialect_postsTemperatureAndContextForItsKind(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(provider.target(IMPROVED, Duration.ZERO)));
            final ChatModel model = provider.model(Duration.ofSeconds(5), duration -> {});

            REFLECT_IMPROVE.improve(
                    segment(), FRAME, SOURCE, CANDIDATE, List.of(), (call, id, request) -> model.chat(request));

            assertBody(kind, provider.chatBodies().getFirst());
        }
    }

    private static void assertBody(final ProviderKind kind, final String body) {
        switch (kind) {
            case OLLAMA ->
                assertThat(body)
                        .contains(
                                "\"options\":{\"temperature\":0.35,\"num_ctx\":8192,\"num_predict\":128}",
                                "\"think\":false");
            case OPENAI_COMPATIBLE ->
                assertThat(body)
                        .contains("\"temperature\":0.35", "\"max_tokens\":128")
                        .doesNotContain("\"num_ctx\"", "\"think\"", "\"reasoning\"");
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
