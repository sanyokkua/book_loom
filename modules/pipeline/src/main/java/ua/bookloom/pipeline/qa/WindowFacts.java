package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.glossary.NameForms;

/**
 * What the never-worse vetoes read about the window a candidate belongs to (design D14).
 *
 * @param sourceMasked the source text with the document's tokens still in it
 * @param sourceLanguage the source language tag, or null when the book declares none
 * @param targetLanguage the target language tag
 * @param names the book-map names that have a rendering
 * @param trigger the finding that marked the focus, or null for an unmarked focus
 * @param unmarkedMaxFocus whether the focus is an unmarked paragraph of Max's whole-book re-read, the one case a small
 *     rewrite at an equal score is kept
 */
public record WindowFacts(
        String sourceMasked,
        @Nullable String sourceLanguage,
        String targetLanguage,
        List<NameFact> names,
        @Nullable QaFinding trigger,
        boolean unmarkedMaxFocus) {

    /** Rejects a missing component and copies the list. */
    public WindowFacts {
        Objects.requireNonNull(sourceMasked, "sourceMasked");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        names = List.copyOf(Objects.requireNonNull(names, "names"));
    }

    /**
     * The facts of one window, its names read off the glossary entries.
     *
     * @param sourceMasked the non-null masked source
     * @param sourceLanguage the source language tag, or null
     * @param targetLanguage the non-null target language tag
     * @param entries the non-null glossary entries; an entry with no rendering is left out
     * @param trigger the marking finding, or null
     * @param unmarkedMaxFocus whether the focus is an unmarked Max paragraph
     * @return the facts
     */
    public static WindowFacts of(
            final String sourceMasked,
            @Nullable final String sourceLanguage,
            final String targetLanguage,
            final List<GlossaryEntry> entries,
            @Nullable final QaFinding trigger,
            final boolean unmarkedMaxFocus) {
        Objects.requireNonNull(entries, "entries");
        final List<NameFact> names = entries.stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .map(entry -> fact(entry, targetLanguage))
                .toList();
        return new WindowFacts(sourceMasked, sourceLanguage, targetLanguage, names, trigger, unmarkedMaxFocus);
    }

    /**
     * The marking finding.
     *
     * @return the finding if the focus is marked, or empty if it is not
     */
    public Optional<QaFinding> triggerFinding() {
        return Optional.ofNullable(trigger);
    }

    private static NameFact fact(final GlossaryEntry entry, final String targetLanguage) {
        final String target = Objects.requireNonNull(entry.target(), "target");
        return new NameFact(
                entry.names(),
                target,
                entry.targetState(),
                NameForms.forms(target, entry.gender(), targetLanguage).all());
    }
}
