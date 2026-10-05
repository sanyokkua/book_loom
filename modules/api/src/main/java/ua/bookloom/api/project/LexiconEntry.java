package ua.bookloom.api.project;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A recurring common term or title of one project ({@code master}, {@code imp}, {@code Mr}) with the renderings the
 * run has verified for it, so the book keeps one rendering where a model would drift between several. Unlike a
 * glossary entry it is soft: the rendering is a "keep consistent" hint, never a lock.
 *
 * <p>Which rendering counts as established is decided here, once, by the conflict policy of {@link #established()}, so
 * the prompt, the reviewer and the screen agree.
 *
 * @param projectId the owning project's id
 * @param term the source term as proposed or typed; matched ignoring case
 * @param renderings the verified renderings in the order they were first seen, each with how often it was used
 * @param chosen the rendering the person typed or accepted, or null; it always wins
 * @param suggested the model's suggested rendering from the suggestion call, or null; used only while nothing was seen
 */
public record LexiconEntry(
        String projectId,
        String term,
        List<Rendering> renderings,
        @Nullable String chosen,
        @Nullable String suggested) {

    /**
     * One verified rendering and its use count.
     *
     * @param text the rendering as first written
     * @param count how many times a draft used it; at least 1
     */
    public record Rendering(String text, int count) {

        /** Rejects a blank text or a count below one. */
        public Rendering {
            Objects.requireNonNull(text, "text");
            if (text.isBlank() || count < 1) {
                throw new IllegalArgumentException("a rendering needs a text and a count of at least 1");
            }
        }
    }

    /** Rejects missing parts, copies the list and reads a blank choice or suggestion as none. */
    public LexiconEntry {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        renderings = List.copyOf(Objects.requireNonNull(renderings, "renderings"));
        chosen = blankAsNull(chosen);
        suggested = blankAsNull(suggested);
    }

    /**
     * A term nothing was seen for yet.
     *
     * @param projectId the owning project's id
     * @param term the source term
     * @return an entry with no rendering, choice or suggestion
     */
    public static LexiconEntry of(final String projectId, final String term) {
        return new LexiconEntry(projectId, term, List.of(), null, null);
    }

    /**
     * The comparison key of a term or rendering: composed, stripped and case-folded.
     *
     * @param text the text to key; never null
     * @return the key
     */
    public static String keyOf(final String text) {
        return Normalizer.normalize(Objects.requireNonNull(text, "text"), Normalizer.Form.NFC)
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * The rendering a draft is asked to keep: the person's choice if there is one; else the most used verified
     * rendering, the one first seen when two are used equally often; else the model's suggestion.
     *
     * @return the rendering, or empty when nothing was chosen, seen or suggested
     */
    public Optional<String> established() {
        if (chosen != null) {
            return Optional.of(chosen);
        }
        return majority().or(() -> Optional.ofNullable(suggested));
    }

    /**
     * The most used verified rendering; the first seen wins a tie.
     *
     * @return the rendering, or empty when none was verified yet
     */
    public Optional<String> majority() {
        Rendering best = null;
        for (final Rendering candidate : renderings) {
            if (best == null || candidate.count() > best.count()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best).map(Rendering::text);
    }

    /**
     * This entry after a draft used a rendering once more; a spelling that differs only in case counts with the first.
     *
     * @param rendering the non-blank rendering a verified pair named
     * @return a copy with the count raised, or with the rendering added last when it is new
     */
    public LexiconEntry seen(final String rendering) {
        final String key = keyOf(rendering);
        final List<Rendering> next = new ArrayList<>();
        boolean found = false;
        for (final Rendering held : renderings) {
            if (keyOf(held.text()).equals(key)) {
                next.add(new Rendering(held.text(), held.count() + 1));
                found = true;
            } else {
                next.add(held);
            }
        }
        if (!found) {
            next.add(new Rendering(rendering.strip(), 1));
        }
        return new LexiconEntry(projectId, term, next, chosen, suggested);
    }

    /**
     * This entry with the person's rendering.
     *
     * @param rendering the rendering to keep, or null or blank to clear the choice
     * @return a copy with the choice set
     */
    public LexiconEntry withChosen(@Nullable final String rendering) {
        return new LexiconEntry(projectId, term, renderings, rendering, suggested);
    }

    /**
     * This entry with the model's suggested rendering.
     *
     * @param rendering the suggestion, or null or blank to clear it
     * @return a copy with the suggestion set
     */
    public LexiconEntry withSuggested(@Nullable final String rendering) {
        return new LexiconEntry(projectId, term, renderings, chosen, rendering);
    }

    /**
     * How many different renderings the drafts used for this term.
     *
     * @return the number of verified renderings; 0 when none
     */
    public int distinctRenderings() {
        return renderings.size();
    }

    private static @Nullable String blankAsNull(@Nullable final String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
