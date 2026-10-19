package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.PronounReader.Pronoun;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.WindowPlan;

/**
 * The evidence the glossary review shows the model for each name (15h.A2): a few numbered windows from across the book
 * — the first use, then one from each stretch of the rest — each cut by the book index inside the use's own paragraph
 * to {@link #WINDOW_TOKENS} and growing forward first, since a pronoun that refers to the name usually follows it; a
 * use whose window gives the code a pronoun of the name preferred within its stretch; and the code's pronoun tally
 * over the whole book. Each window records the pronoun the code gives the name there, so a
 * gender the model claims can be checked against the windows it cites. A name judged from its first sentence or two
 * was the old way, and it gave a woman the gender of the speaker's "he" (an owner run, 2026-10-09). The uses are the
 * index's mentions in any letter case (15i.N4), so the review counts what the reading counts, by one matching rule.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameWindows {

    /** How many windows a name shows. */
    static final int WINDOWS = 4;

    /** How many windows a name used more than {@link #MANY_USES} times shows. */
    static final int MANY_WINDOWS = 6;

    /** Above this many uses a name shows {@link #MANY_WINDOWS} windows. */
    static final int MANY_USES = 30;

    /** How many windows a place shows; its gender is never read from the book. */
    static final int PLACE_WINDOWS = 2;

    /**
     * The token budget of one window, cut by the book index inside the use's own paragraph (15i.N4): about the three
     * sentences of up to 200 characters the review showed before, so a batch of forty terms keeps its size.
     */
    static final int WINDOW_TOKENS = 150;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * One window of book text around a use of the name.
     *
     * @param text the sentences around the use inside its paragraph, joined by spaces
     * @param pronoun the gender of the pronoun the code gives the name in this window — outside speech, after the name
     *     with no other name between — or null when there is none
     */
    record Window(String text, @Nullable Gender pronoun) {}

    /**
     * How the book uses one name.
     *
     * @param count how many times it occurs, in any case
     * @param windows the windows shown, in book order; window {@code n} is number {@code n + 1} in the prompt
     * @param pronouns the pronouns the code read after the name across the whole book
     */
    record Evidence(int count, List<Window> windows, PronounGender.Evidence pronouns) {

        static final Evidence NONE = new Evidence(0, List.of(), new PronounGender.Evidence(List.of(), List.of()));

        Evidence {
            windows = List.copyOf(windows);
        }
    }

    /**
     * Gathers the windows for the entries from the book's index, each entry's pronouns its own (no cluster pools them).
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null
     * @param entries the entries asked about; never null
     * @param languageTag the source language's tag, which picks the pronouns; null reads none
     * @return the evidence by term as held; a term the book never uses maps to {@link Evidence#NONE}
     */
    static Map<String, Evidence> of(
            final BookIndex index,
            final List<Integer> ordinals,
            final List<GlossaryEntry> entries,
            @Nullable final String languageTag) {
        return of(index, ordinals, entries, NameClusters.of(List.of(), index, languageTag), languageTag);
    }

    /**
     * Gathers the windows for the entries from the book's index.
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null
     * @param entries the entries asked about; never null
     * @param clusters the clusters of the whole glossary, built on the same index: the pronouns after any spelling of
     *     a person count for each of them (15i.N2); never null
     * @param languageTag the source language's tag, which picks the pronouns; null reads none
     * @return the evidence by term as held; a term the book never uses maps to {@link Evidence#NONE}
     */
    static Map<String, Evidence> of(
            final BookIndex index,
            final List<Integer> ordinals,
            final List<GlossaryEntry> entries,
            final NameClusters clusters,
            @Nullable final String languageTag) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(clusters, "clusters");
        final Map<String, List<Mention>> uses = usesOf(index, ordinals, entries);
        final Map<String, Evidence> evidence = new LinkedHashMap<>();
        for (final GlossaryEntry entry : entries) {
            final List<Mention> found = uses.getOrDefault(entry.term(), List.of());
            evidence.put(
                    entry.term(), evidenceOf(index, entry, found, clusters.pronounsOf(entry, ordinals), languageTag));
        }
        log.debug(
                "Name windows over {} segments for {} entries, {} used in the book",
                ordinals.size(),
                entries.size(),
                uses.size());
        return evidence;
    }

    private static Map<String, List<Mention>> usesOf(
            final BookIndex index, final List<Integer> ordinals, final List<GlossaryEntry> entries) {
        final Set<Integer> slice = new HashSet<>(ordinals);
        final Set<String> terms = new HashSet<>();
        entries.forEach(entry -> terms.add(entry.term()));
        return index.mentions(terms, Case.INSENSITIVE).stream()
                .filter(mention -> slice.contains(mention.ordinal()))
                .collect(Collectors.groupingBy(Mention::name, LinkedHashMap::new, Collectors.toList()));
    }

    private static Evidence evidenceOf(
            final BookIndex index,
            final GlossaryEntry entry,
            final List<Mention> uses,
            final PronounGender.Evidence pronouns,
            @Nullable final String languageTag) {
        if (uses.isEmpty()) {
            return new Evidence(0, List.of(), pronouns);
        }
        final PronounLookup lookup = new PronounLookup(
                PronounReader.of(entry.term(), languageTag), index, new HashMap<>(), pronouns.usesObjectForms());
        // Two uses in one short paragraph give the same window; the model reads it once, so it is cited once.
        final Set<String> shown = new HashSet<>();
        final List<Window> windows = index
                .windows(
                        uses,
                        new WindowPlan(wanted(entry, uses.size()), 0, WINDOW_TOKENS, WindowPlan.Growth.FORWARD_FIRST),
                        use -> lookup.pronounOf(use) != null)
                .stream()
                .filter(window -> shown.add(window.firstOrdinal() + ":" + window.text()))
                .map(window -> new Window(window.text(), lookup.pronounIn(window)))
                .toList();
        log.trace(
                "Name windows for {}: {} uses, windows at {}, pronouns {}",
                entry.term(),
                uses.size(),
                windows,
                pronouns.compact());
        return new Evidence(uses.size(), windows, pronouns);
    }

    private static int wanted(final GlossaryEntry entry, final int count) {
        if (entry.type() == TermType.PLACE) {
            return PLACE_WINDOWS;
        }
        return count > MANY_USES ? MANY_WINDOWS : WINDOWS;
    }

    /**
     * Reads the pronoun the code gives the name at each use, keeping each segment's readings once they are made. An
     * object or possessive form gives a use its gender only when {@code objectForms}: by
     * {@link PronounGender.Evidence#usesObjectForms()}, the name's subject pronouns are scarce and none of them names
     * the other gender.
     */
    private record PronounLookup(
            @Nullable PronounReader reader,
            BookIndex index,
            Map<Integer, List<PronounReader.Mention>> readings,
            boolean objectForms) {

        @Nullable
        Pronoun pronounOf(final Mention use) {
            final List<PronounReader.Mention> found = readings.computeIfAbsent(
                    use.ordinal(), ordinal -> reader == null ? List.of() : reader.read(textOf(ordinal)));
            return found.stream()
                    .filter(mention -> mention.start() == use.start())
                    .map(mention -> objectForms ? mention.attributed() : mention.subject())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        // The pronoun counts only when the window the index cut still shows it, from the name up to the pronoun.
        @Nullable
        Gender pronounIn(final ua.bookloom.pipeline.index.Window window) {
            final Mention use = window.mention();
            final Pronoun pronoun = pronounOf(use);
            if (pronoun == null) {
                return null;
            }
            final String shown = WHITESPACE
                    .matcher(textOf(use.ordinal()).substring(use.start(), pronoun.end()))
                    .replaceAll(" ");
            final boolean inWindow = window.text().startsWith(shown, window.markStart());
            log.trace("Pronoun {} of '{}' in its window: {}", pronoun.word(), use.name(), inWindow);
            return inWindow ? pronoun.gender() : null;
        }

        private String textOf(final int ordinal) {
            return index.visibleTexts(List.of(ordinal)).getFirst();
        }
    }
}
