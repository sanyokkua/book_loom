package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.QaFinding;

/**
 * Finds the words of the target a finding is about, so the review panel can mark them where they stand.
 *
 * <p>Two shapes carry a quote: an applied edit, whose replacement is what stands in the target now, and a note that
 * opens with the quoted words in double quotes followed by a dash or {@code could read} — the shape the checks and the
 * reviewer write. A quote cut to its first 80 characters ends in an ellipsis, which is dropped, so the rest still
 * matches.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvidenceQuote {

    private static final char QUOTE = '"';
    private static final String ELLIPSIS = "…";
    private static final List<String> ENDINGS = List.of("\" — ", "\" could read ");

    /**
     * The words a finding quotes.
     *
     * @param finding any finding of a segment; never null
     * @return the quoted words, or empty when the finding quotes none
     */
    public static Optional<String> of(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        final Optional<AppliedEdit> edit = AppliedEdit.from(finding);
        if (edit.isPresent()) {
            return Optional.of(edit.get().replacement()).filter(words -> !words.isEmpty());
        }
        return quotedPrefix(finding.note());
    }

    /**
     * Every quote of a segment's findings, in order.
     *
     * @param findings the segment's findings; never null
     * @return the quotes; never null, empty when none quotes anything
     */
    public static List<String> allOf(final List<QaFinding> findings) {
        Objects.requireNonNull(findings, "findings");
        return findings.stream()
                .map(EvidenceQuote::of)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<String> quotedPrefix(final String note) {
        if (note.isEmpty() || note.charAt(0) != QUOTE) {
            return Optional.empty();
        }
        return ENDINGS.stream().mapToInt(note::indexOf).filter(end -> end > 1).min().stream()
                .mapToObj(end -> note.substring(1, end))
                .map(words -> words.endsWith(ELLIPSIS) ? words.substring(0, words.length() - 1) : words)
                .findFirst()
                .filter(words -> !words.isEmpty());
    }
}
