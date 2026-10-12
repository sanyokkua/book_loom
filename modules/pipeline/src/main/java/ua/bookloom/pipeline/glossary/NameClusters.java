package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.prompt.GenderedTitles;

/**
 * The glossary entries that name one person (15i.N2), recomputed from the entries every time, so a name added later
 * changes the clusters and nothing is stored. The anchor of a cluster is a full name: a multi-word character entry
 * that does not open with a title. A one-word character or untyped entry that is the first or the last word of exactly
 * one anchor joins it ({@code Arlo} and {@code Venn} join {@code Arlo Venn}); a surname two anchors share joins
 * neither. A form of address ({@code Mr Venn}) joins the one anchor with its surname, and a gendered one
 * ({@code Mrs}) only when that anchor's gender, from the entry or else the first-name list, is the same, since
 * {@code Mrs Venn} is often the wife of {@code Arlo Venn}. A place, a term, a title and an alias never join and are
 * never an anchor: an alias is a spelling of its entry, not a person.
 */
@Slf4j
public final class NameClusters {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern TRAILING_STOP = Pattern.compile("\\.$");
    private static final int FULL_NAME_WORDS = 2;

    private final List<GlossaryEntry> entries;
    private final Map<String, String> anchorOfJoined;
    private final Set<String> anchors;
    private final @Nullable BookIndex index;
    private final @Nullable String languageTag;

    private NameClusters(
            final List<GlossaryEntry> entries,
            final Map<String, String> anchorOfJoined,
            final Set<String> anchors,
            @Nullable final BookIndex index,
            @Nullable final String languageTag) {
        this.entries = entries;
        this.anchorOfJoined = anchorOfJoined;
        this.anchors = anchors;
        this.index = index;
        this.languageTag = languageTag;
    }

    /**
     * The clusters of some glossary entries, in a book whose language is not known: the titles of every bundled
     * language are read and the first-name list is silent.
     *
     * @param entries every glossary entry of the project
     * @param index the project's book index, which pools the pronouns of a cluster's spellings; null when no book is
     *     open (membership never needs it)
     * @return the clusters
     */
    public static NameClusters of(final List<GlossaryEntry> entries, @Nullable final BookIndex index) {
        return of(entries, index, null);
    }

    /**
     * The clusters of some glossary entries.
     *
     * @param entries every glossary entry of the project
     * @param index the project's book index; null when no book is open (membership never needs it)
     * @param languageTag the tag of the language the book is read in, which picks the titles and the first-name list;
     *     null reads every bundled title list and no first-name list
     * @return the clusters
     */
    public static NameClusters of(
            final List<GlossaryEntry> entries, @Nullable final BookIndex index, @Nullable final String languageTag) {
        Objects.requireNonNull(entries, "entries");
        final Set<String> titles = StopWords.titles(languageTag);
        final List<GlossaryEntry> anchorEntries =
                entries.stream().filter(entry -> isAnchor(entry, titles)).toList();
        final Map<String, String> joined = new HashMap<>();
        for (final GlossaryEntry entry : entries) {
            anchorFor(entry, anchorEntries, titles, languageTag).ifPresent(anchor -> {
                log.debug("Name cluster: entry {} joins the full name {}", entry.id(), anchor.id());
                joined.put(entry.id(), anchor.id());
            });
        }
        final Set<String> anchorIds = new HashSet<>();
        anchorEntries.forEach(anchor -> anchorIds.add(anchor.id()));
        log.debug(
                "Name clusters over {} entries: {} full names, {} joined",
                entries.size(),
                anchorIds.size(),
                joined.size());
        return new NameClusters(List.copyOf(entries), joined, anchorIds, index, languageTag);
    }

    /**
     * The entries of the cluster an entry belongs to.
     *
     * @param entry an entry, found by its id
     * @return its cluster's entries in the order the entries were given, itself included; just itself when alone
     */
    public List<GlossaryEntry> membersOf(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        final String key = keyOf(entry);
        final List<GlossaryEntry> members =
                entries.stream().filter(other -> keyOf(other).equals(key)).toList();
        return members.isEmpty() ? List.of(entry) : members;
    }

    /**
     * The full name of an entry's cluster.
     *
     * @param entry an entry, found by its id
     * @return the cluster's anchor (the entry itself when it is one), or empty when the entry is alone and no full
     *     name
     */
    public Optional<GlossaryEntry> fullNameOf(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        final String key = keyOf(entry);
        return anchors.contains(key)
                ? entries.stream().filter(other -> other.id().equals(key)).findFirst()
                : Optional.empty();
    }

    /**
     * Every spelling that names the person: the terms and aliases of the cluster's entries.
     *
     * @param entry an entry, found by its id
     * @return the spellings without repeats, the entry's cluster in glossary order; never empty
     */
    public List<String> namesOf(final GlossaryEntry entry) {
        final Set<String> names = new LinkedHashSet<>();
        membersOf(entry).forEach(member -> names.addAll(member.names()));
        return List.copyOf(names);
    }

    /**
     * The pronouns that follow any spelling of the person, which together decide a gender that each spelling alone
     * would leave open.
     *
     * @param entry an entry, found by its id
     * @param ordinals the ordinals of the segments to read, in reading order
     * @return the pooled evidence; empty when these clusters were built with no book index
     */
    public PronounGender.Evidence pronounsOf(final GlossaryEntry entry, final List<Integer> ordinals) {
        PronounGender.Evidence pooled = new PronounGender.Evidence(List.of(), List.of());
        if (index == null) {
            log.debug("Name clusters have no book index: no pronouns for entry {}", entry.id());
            return pooled;
        }
        for (final GlossaryEntry member : membersOf(entry)) {
            pooled = pooled.and(PronounGender.of(member.term(), index, ordinals, languageTag));
        }
        return pooled;
    }

    private String keyOf(final GlossaryEntry entry) {
        return anchorOfJoined.getOrDefault(entry.id(), entry.id());
    }

    private static boolean isAnchor(final GlossaryEntry entry, final Set<String> titles) {
        final List<String> words = wordsOf(entry.term());
        return entry.type() == TermType.CHARACTER && words.size() >= FULL_NAME_WORDS && !isHonorific(words, titles);
    }

    private static boolean isHonorific(final List<String> words, final Set<String> titles) {
        return words.size() >= FULL_NAME_WORDS && titles.contains(titleKey(words.getFirst()));
    }

    private static Optional<GlossaryEntry> anchorFor(
            final GlossaryEntry entry,
            final List<GlossaryEntry> anchorEntries,
            final Set<String> titles,
            @Nullable final String languageTag) {
        if (entry.type() != TermType.CHARACTER && entry.type() != TermType.OTHER) {
            return Optional.empty();
        }
        final List<String> words = wordsOf(entry.term());
        if (words.size() == 1) {
            return unique(anchorEntries.stream()
                    .filter(anchor -> isEndWord(words.getFirst(), anchor))
                    .toList());
        }
        if (!isHonorific(words, titles)) {
            return Optional.empty();
        }
        return unique(anchorEntries.stream()
                        .filter(anchor ->
                                lower(wordsOf(anchor.term()).getLast()).equals(lower(words.getLast())))
                        .toList())
                .filter(full -> agrees(words.getFirst(), full, languageTag));
    }

    // A gendered title joins only a full name of its gender; an ungendered one (Doctor) joins either.
    private static boolean agrees(final String title, final GlossaryEntry full, @Nullable final String languageTag) {
        final Optional<Gender> wanted = GenderedTitles.genderOf(title, languageTag);
        if (wanted.isEmpty()) {
            return true;
        }
        final Gender actual = full.gender() != Gender.UNKNOWN
                ? full.gender()
                : GivenNames.genderOf(full.term(), languageTag).orElse(Gender.UNKNOWN);
        final boolean agrees = actual == wanted.get();
        log.debug(
                "Name cluster: a title of gender {} against full name {} of gender {}: {}",
                wanted.get(),
                full.id(),
                actual,
                agrees ? "joined" : "kept apart");
        return agrees;
    }

    private static boolean isEndWord(final String word, final GlossaryEntry anchor) {
        final List<String> words = wordsOf(anchor.term());
        return lower(words.getFirst()).equals(lower(word))
                || lower(words.getLast()).equals(lower(word));
    }

    private static Optional<GlossaryEntry> unique(final List<GlossaryEntry> candidates) {
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static List<String> wordsOf(final String term) {
        return List.of(SPACES.split(term.strip()));
    }

    private static String titleKey(final String word) {
        return lower(TRAILING_STOP.matcher(word).replaceAll(""));
    }

    private static String lower(final String word) {
        return word.toLowerCase(Locale.ROOT);
    }
}
