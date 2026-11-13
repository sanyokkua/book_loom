package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion.ShapeQuote;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.StoryShape;

/**
 * The story shape of the brief suggestion (design D6), decided from the synthesis's answer: whether the chapters tell
 * one story or separate ones, with one quote from each of two windows. A quote counts only when the passage it names
 * was read, is named once, and holds the quote; two such quotes make the shape stand on {@link Basis#PASSAGES}, fewer
 * leave it the model's most probable value, {@link Basis#LIKELY}. Either way the brief stores the shape as likely
 * until the person confirms it. Split from {@link BriefDecision} for its length.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StoryShapeDecision {

    // The shape compares two windows, so two quotes are all it needs and all its count is over.
    private static final int COMPARED = 2;

    /**
     * The decided shape.
     *
     * @param value the shape, or null when the synthesis gave none the brief can hold
     * @param quotes the kept quotes when the shape stands on two windows; never null, empty otherwise
     * @param evidence what the shape stands on, or null exactly when {@code value} is
     */
    record Shape(
            @Nullable StoryShape value,
            List<ShapeQuote> quotes,
            @Nullable FieldEvidence evidence) {

        static final Shape NONE = new Shape(null, List.of(), null);

        /** Copies the quotes. */
        Shape {
            quotes = List.copyOf(quotes);
        }
    }

    /**
     * Decides the story shape.
     *
     * @param synthesis the synthesis reply, or null when the call failed, was skipped or its reply could not be read
     * @param windows the windows read, in passage order
     * @return the shape with its quotes and evidence; {@link Shape#NONE} when the synthesis gave no valid shape
     */
    static Shape of(final @Nullable JsonNode synthesis, final List<BookSample> windows) {
        final JsonNode node = synthesis == null ? null : synthesis.get(BriefReplies.wireName(BriefField.STORY_SHAPE));
        final StoryShape value =
                node == null ? null : valueOf(node.path("value").asText(""));
        if (node == null || value == null) {
            log.debug("Brief field STORY_SHAPE not decided: the synthesis gave no shape the brief can hold");
            return Shape.NONE;
        }
        final List<ShapeQuote> kept = kept(node.path("quotes"), windows);
        final int samples = Math.clamp(windows.size(), 1, COMPARED);
        final boolean compared = kept.size() >= COMPARED;
        final FieldEvidence evidence = compared
                ? new FieldEvidence(
                        Basis.PASSAGES, kept.stream().map(ShapeQuote::quote).toList(), COMPARED, COMPARED)
                : new FieldEvidence(Basis.LIKELY, List.of(), Math.min(kept.size(), samples), samples);
        log.info("Brief field STORY_SHAPE decided: {} {}", value, evidence.describe());
        return new Shape(value, compared ? kept : List.of(), evidence);
    }

    private static @Nullable StoryShape valueOf(final String raw) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "one story" -> StoryShape.ONE_STORY;
            case "separate stories" -> StoryShape.SEPARATE_STORIES;
            default -> null;
        };
    }

    // The first two quotes that each name a different window read and are found in it.
    private static List<ShapeQuote> kept(final JsonNode quotes, final List<BookSample> windows) {
        final List<ShapeQuote> kept = new ArrayList<>();
        final Set<Integer> named = new HashSet<>();
        for (final JsonNode item : quotes) {
            final int passage = item.path("passage").asInt(0);
            final String quote = item.path("quote").asText("").strip();
            final String refused = refusal(passage, quote, windows, named);
            log.trace("Story shape quote passage={} '{}'", passage, quote);
            if (refused != null) {
                log.debug("Story shape quote of passage {} refused: {}", passage, refused);
            } else if (kept.size() < COMPARED) {
                kept.add(new ShapeQuote(passage, quote));
            }
        }
        return kept;
    }

    // Why a quote is no support, or null when it is; a passage named again is refused even when the first was not.
    private static @Nullable String refusal(
            final int passage, final String quote, final List<BookSample> windows, final Set<Integer> named) {
        if (passage < 1 || passage > windows.size()) {
            return "passage out of range";
        }
        if (!named.add(passage)) {
            return "repeated";
        }
        return windows.get(passage - 1).holds(quote) ? null : "not in window";
    }
}
