package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * What a name scan proposes for the glossary (15i.N5): the entries, and the terms among them the scan kept only for
 * the names reading to decide — a word the book also writes in lower case, but often with a capital mid-sentence
 * ({@link FrequencyScan#READING_MIN_MID_SENTENCE}). Such a term is added only where the dial's {@link NamesPlan} reads
 * it, since without a reading nothing would tell the bartender Mark from "a mark on the wall".
 *
 * @param entries the proposed entries, in candidate order
 * @param readingOnly the terms of the entries kept only for the reading, as written
 */
@Slf4j
public record ScanProposals(List<GlossaryEntry> entries, Set<String> readingOnly) {

    /** Copies the components. */
    public ScanProposals {
        entries = List.copyOf(entries);
        readingOnly = Set.copyOf(readingOnly);
    }

    /**
     * The entries seeded with the genders their first names and pronouns suggest, then without a term kept only for
     * the reading that the dial's plan would not read — so Fast, which reads only likely characters, keeps a common
     * word with a capital only when the pronouns already made it a person.
     *
     * @param story the slice the scan read; never null
     * @param language the language the code reads the book in; null under Auto
     * @param dial the project's quality dial; null when no reading follows, which drops every such term
     * @return the seeded entries to add; never null
     */
    public List<GlossaryEntry> seededFor(
            final BookSlice story, @Nullable final String language, @Nullable final QualityDial dial) {
        Objects.requireNonNull(story, "story");
        final List<GlossaryEntry> seeded =
                PronounGender.seeded(GivenNames.seeded(entries, language), story.visibleTexts(), language);
        if (readingOnly.isEmpty()) {
            return seeded;
        }
        final Set<String> read = dial == null
                ? Set.of()
                : NamesPlan.of(dial, NamesPlan.candidates(seeded, story)).read().stream()
                        .map(candidate -> candidate.entry().term())
                        .collect(Collectors.toSet());
        final List<GlossaryEntry> kept = seeded.stream()
                .filter(entry -> !readingOnly.contains(entry.term()) || read.contains(entry.term()))
                .toList();
        log.debug(
                "Scan proposals for dial {}: {} kept only for the reading, {} of them read, {} of {} entries added",
                dial,
                readingOnly.size(),
                readingOnly.stream().filter(read::contains).count(),
                kept.size(),
                seeded.size());
        return kept;
    }
}
