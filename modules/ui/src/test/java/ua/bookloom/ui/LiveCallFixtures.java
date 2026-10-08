package ua.bookloom.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.SegmentLive;

/** Model calls as the live panel is shown them, for the tests that publish them to the mirror. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiveCallFixtures {

    public static final Instant STARTED = Instant.parse("2026-01-01T00:00:10Z");

    /** A draft call of {@code count} segments whose sources are {@code text}, waiting for the model. */
    public static CallSnapshot waiting(
            final long id, final int count, final String text, final List<PromptSection> sections) {
        final List<CallSegment> segments = IntStream.rangeClosed(1, count)
                .mapToObj(n -> new CallSegment("s-" + id + "-" + n, "ch7 · p" + (id * 100 + n), text + " #" + n))
                .toList();
        return CallSnapshot.waiting(
                id, CallKind.DRAFT, "draft-batch-json", null, segments, sections, STARTED, 1, 2, Duration.ofMinutes(3));
    }

    public static CallSnapshot answered(final CallSnapshot waiting, final String reply) {
        return waiting.answered(reply, null, Duration.ofSeconds(7));
    }

    /** One system part and four user parts, in the order the draft prompt sends them. */
    public static List<PromptSection> smallPrompt() {
        return List.of(
                new PromptSection("styleSheet", "Style:", PromptSection.Origin.SYSTEM, List.of("Literary, warm.")),
                new PromptSection(
                        "summary", "[Book so far]", PromptSection.Origin.USER, List.of("A djinni is summoned.")),
                new PromptSection(
                        "glossaryTerms", "[Glossary]", PromptSection.Origin.USER, List.of("Lovelace → Лавлейс")),
                new PromptSection("characters", "[Characters]", PromptSection.Origin.USER, List.of("Nathaniel (he)")),
                new PromptSection(
                        "precedingPairs", "[Previous pairs]", PromptSection.Origin.USER, List.of("Rain.", "Дощ.")));
    }

    /** Parts long enough that the opened prompt pane has to scroll. */
    public static List<PromptSection> largePrompt(final String longText) {
        return List.of(
                new PromptSection("styleSheet", "Style:", PromptSection.Origin.SYSTEM, List.of(longText + longText)),
                new PromptSection("summary", "[Book so far]", PromptSection.Origin.USER, List.of(longText + longText)),
                new PromptSection(
                        "precedingPairs",
                        "[Previous pairs]",
                        PromptSection.Origin.USER,
                        List.of(longText, longText, longText, longText)),
                new PromptSection(
                        "characters", "[Characters]", PromptSection.Origin.USER, List.of(longText, longText)));
    }

    public static LiveCalls calls(final @Nullable CallSnapshot current, final @Nullable CallSnapshot previous) {
        return new LiveCalls(current, previous, Map.of(), STARTED.plusSeconds(12));
    }

    public static LiveCalls calls(
            final @Nullable CallSnapshot current,
            final @Nullable CallSnapshot previous,
            final Map<String, SegmentLive> segments) {
        return new LiveCalls(current, previous, segments, STARTED.plusSeconds(12));
    }
}
