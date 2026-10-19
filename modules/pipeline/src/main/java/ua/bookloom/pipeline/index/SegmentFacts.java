package ua.bookloom.pipeline.index;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;

/**
 * What a scan of running text needs of a segment besides its words: the language the book declares for it and the
 * language of each inline run, so a foreign passage is left out without reading the document again.
 *
 * @param declaredLanguage the segment's declared language tag, or {@code null} when it declares none
 * @param pairs the segment's placeholder pairs, each with the language of the run it wraps
 */
record SegmentFacts(@Nullable String declaredLanguage, List<PlaceholderPair> pairs) {}
