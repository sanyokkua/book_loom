package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** Builds a segment whose source hash is the readable text {@code h:<masked>}, so a test can name a context key. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslationMemoryFixtures {

    static Segment segment(final String id, final String masked) {
        return new Segment(
                id,
                id.substring(0, id.indexOf(':')),
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "h:" + masked,
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
}
