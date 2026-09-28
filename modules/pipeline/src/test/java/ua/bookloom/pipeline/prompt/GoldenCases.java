package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;

/** The pinned prompt scenarios: a case name maps to the builder's languages and the messages it must produce. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GoldenCases {

    static final String SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";

    /** Scenario names, one golden file pair (system and user) each. */
    static final List<String> NAMES = List.of(
            "draft-en-uk",
            "draft-en-uk-preceding",
            "draft-unknown-source",
            "draft-zh-hant",
            "structural-repair",
            "placeholder-repair");

    static List<ChatMessage> render(final String name, final BiFunction<String, String, DraftPromptBuilder> builders) {
        return switch (name) {
            case "draft-en-uk" -> builders.apply("en", "uk").messagesFor(segment(SOURCE));
            case "draft-en-uk-preceding" ->
                builders.apply("en", "uk")
                        .messagesFor(segment(SOURCE), new DraftContext(List.of("Перший.", "Другий.")));
            case "draft-unknown-source" -> builders.apply(null, "uk").messagesFor(segment("One."));
            case "draft-zh-hant" -> builders.apply("zh-Hant", "uk").messagesFor(segment(SOURCE));
            case "structural-repair" ->
                builders.apply("en", "uk")
                        .messagesForStructuredRepair(
                                segment(SOURCE), DraftContext.empty(), "Він відчинив", "was not a JSON object");
            case "placeholder-repair" ->
                builders.apply("en", "uk")
                        .messagesForPlaceholderRepair(
                                segment(SOURCE), DraftContext.empty(), "Він відчинив ⟦g0⟧старі двері.");
            default -> throw new IllegalArgumentException(name);
        };
    }

    static Segment segment(final String masked) {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
