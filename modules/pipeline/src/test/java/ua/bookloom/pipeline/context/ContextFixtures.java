package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** Builders for the character sheet tests: plain segments and glossary entries. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ContextFixtures {

    static Segment segment(final int order, final String masked) {
        return new Segment(
                "ch01.xhtml:" + order,
                "ch01.xhtml",
                order,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash" + order,
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                List.of(),
                List.of());
    }

    static GlossaryEntry entry(
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        return GlossaryEntry.of("p1:" + term, "p1", term, target, type, gender, locked);
    }
}
