package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** Builders for the context-package tests: chunks of plain segments, glossary entries and the assembler's inputs. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ContextFixtures {

    static final StyleSheet STYLE = new StyleSheet("Neutral register.", "hash");
    static final TmLookup NO_MEMORY = new TmLookup(null, List.of(), List.of());

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

    static Chunk chunk(final String... texts) {
        final List<Segment> segments = new ArrayList<>();
        for (int order = 0; order < texts.length; order++) {
            segments.add(segment(order, texts[order]));
        }
        return new Chunk("ch01.xhtml", segments, false);
    }

    static ProtectedMask mask(final Segment segment, final List<GlossaryEntry> glossary) {
        return ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, glossary);
    }

    static ContextInputs inputs(
            @Nullable final String summary,
            final int precedingCount,
            final List<GlossaryEntry> glossary,
            final List<String> earlierMaskedTargets) {
        return new ContextInputs(STYLE, summary, precedingCount, glossary, earlierMaskedTargets);
    }

    static GlossaryEntry entry(
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        return new GlossaryEntry("p1:" + term, "p1", term, target, type, gender, locked);
    }

    static TmEntry tmEntry(final String source, final String target) {
        return new TmEntry("e:" + source, "p1", "hash", "ctx", source, target);
    }
}
