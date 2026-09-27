package ua.bookloom.api.document;

import java.util.List;
import ua.bookloom.api.Result;

/**
 * Splits an oversized segment's masked text at sentence boundaries ({@code specs/document-round-trip/spec.md}
 * "Split an oversized segment at sentence boundaries"), so the chunker can split it without depending on
 * {@code :document}. Implemented by {@code :document}.
 *
 * <p>Every method is a boundary method: no exception may cross this interface, per
 * {@code 02_Architecture/09_ERROR_HANDLING.md#boundary-discipline}.
 */
public interface SentenceSplitter {

    /**
     * Splits {@code masked} into sentence-bounded pieces.
     *
     * @param masked the segment's masked text to split; may carry extra atomic tokens for the pipeline's protected
     *     spans, locked terms and kept foreign runs beyond {@code segment}'s own placeholders
     * @param segment the segment {@code masked} was derived from
     * @param languageTag the language tag to split by (for example an ISO 639-1 code)
     * @return the ordered sentence-bounded pieces, or a failed result
     */
    Result<List<String>> split(String masked, Segment segment, String languageTag);
}
