package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;

/** Small builders for the protected-span tests: a segment of given masked text and glossary entries. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProtectedSpansFixtures {

    /** Restores every candidate unchanged, so a test reads the text the protected-span gate handed on. */
    static final GateFunction PASSTHROUGH = (segment, maskedReply) -> new GateResult.Restored(maskedReply, maskedReply);

    static Segment segment(final String masked) {
        return segment(masked, List.of());
    }

    static Segment segment(final String masked, final List<PlaceholderPair> pairs) {
        return declaring(masked, pairs, null);
    }

    /** A segment whose block declares {@code language}, as {@code <p xml:lang="la">} does. */
    static Segment declaring(final String masked, final List<PlaceholderPair> pairs, @Nullable final String language) {
        return new Segment(
                "u1:0",
                "u1",
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
                0.0,
                language,
                pairs,
                List.of());
    }

    static GlossaryEntry locked(final String term, @Nullable final String target) {
        return new GlossaryEntry("p1:" + term, "p1", term, target, TermType.CHARACTER, Gender.UNKNOWN, true);
    }

    static GlossaryEntry unlocked(final String term, final String target) {
        return new GlossaryEntry("p1:" + term, "p1", term, target, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    static PlaceholderPair pair(final int open, final int close, @Nullable final String language) {
        return new PlaceholderPair("⟦g" + open + "⟧", "⟦g" + close + "⟧", language);
    }
}
