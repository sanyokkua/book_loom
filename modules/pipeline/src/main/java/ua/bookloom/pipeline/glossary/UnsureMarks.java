package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * Sets on each scanned entry how many of its mentions the clause around them left between it and another name the book
 * writes ({@code specs/glossary/spec.md} "Resolve a form that fits two names by the clause around each mention"), so
 * Names &amp; style can mark the entry Unsure. The count depends only on the entry's names and the book's text.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UnsureMarks {

    /**
     * The entries with their unsure mentions counted.
     *
     * @param entries the non-null entries
     * @param texts the non-null visible texts of the book
     * @param language the source language tag, or null when it is not known (every count is zero)
     * @return a list of the same size, in order; never null
     */
    public static List<GlossaryEntry> marked(
            final List<GlossaryEntry> entries, final List<String> texts, @Nullable final String language) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(texts, "texts");
        final List<GlossaryEntry> marked = entries.stream()
                .map(entry -> entry.withUnsureMentions(LemmaMentions.unsure(entry, texts, language)))
                .toList();
        log.debug(
                "Unsure marks language={} entries={} unsure={}",
                language,
                entries.size(),
                marked.stream().filter(entry -> entry.unsureMentions() > 0).count());
        return marked;
    }
}
