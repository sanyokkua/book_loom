package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.heal.DirectedFix;

/** Pins the directed-fix messages, so a prompt change is always a deliberate edit of a golden file. */
class SelfHealPromptGoldenTest {

    private static final String SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";
    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    @ParameterizedTest
    @ValueSource(strings = {"directed-fix-en-uk"})
    void messages_pinnedScenario_areByteEqualToGolden(final String name) throws IOException {
        final List<ChatMessage> messages = render(name);

        assertThat(messages.get(0).content()).isEqualTo(golden(name + ".system.txt"));
        assertThat(messages.get(1).content()).isEqualTo(golden(name + ".user.txt"));
    }

    /** The messages a pinned scenario sends: an echo's directed fix. */
    static List<ChatMessage> render(final String name) {
        final AtomicReference<ChatRequest> sent = new AtomicReference<>();
        final ModelCalls calls = (kind, segmentId, request) -> {
            sent.set(request);
            return Result.ok(new ChatResponse("{\"target\":\"x\"}", FinishReason.STOP));
        };
        switch (name) {
            case "directed-fix-en-uk" ->
                new DirectedFix(TEMPLATES, new DraftReplyParser(new ObjectMapper()))
                        .fix(
                                segment(),
                                FRAME,
                                SOURCE,
                                "Він відчинив ⟦g0⟧старі двері.",
                                List.of(new QaFinding("markup", Severity.HIGH, "missing ⟦g1⟧", "placeholder")),
                                calls);
            default -> throw new IllegalArgumentException(name);
        }
        return Objects.requireNonNull(sent.get(), "request").messages();
    }

    private static String golden(final String file) throws IOException {
        try (var stream = SelfHealPromptGoldenTest.class.getResourceAsStream("golden/" + file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
