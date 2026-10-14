package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;

/**
 * The renderings of one person's spellings agree word by word (15i.N6): {@code Arlo Venn → Арло Венн} makes
 * {@code Venn → Венн}, whichever of the two the glossary already holds or the call is asking about. A held rendering
 * binds only when the person chose it, never an earlier suggestion nobody confirmed; between two asked spellings the
 * full name's is the cluster's chosen rendering. Words are the runs between spaces and hyphens, and a spelling is
 * aligned only when its rendering has as many words as its term. Beyond one cluster, one word of a person's name has
 * one rendering in every person's name ({@code Wren} of {@code Tam Wren} and of {@code Tamsin Wren}), and one word of a
 * place's name in every place's name ({@code Arden} of {@code Arden Hall}), up to the first word in lower case
 * ({@code Tower} of {@code Tower of Arlen}).
 */
@Slf4j
final class ClusterAgreement {

    private static final Pattern WORD = Pattern.compile("[^\\s-]+");

    // A word keeps one rendering across the names of one group: a person's names, or the names of places and other
    // named things (15i.N8: a place's first word written alone, "Arden" beside "Arden Hall"). A place named after a
    // person keeps its own rendering, and a term or a title may be a common word whose form varies.
    private static final Set<TermType> PLACE_GROUP = Set.of(TermType.PLACE, TermType.OTHER);

    private final NameClusters clusters;
    private final Map<String, String> held;
    private final Map<String, String> heldWords;

    private ClusterAgreement(
            final NameClusters clusters, final Map<String, String> held, final Map<String, String> heldWords) {
        this.clusters = clusters;
        this.held = held;
        this.heldWords = heldWords;
    }

    /**
     * The agreement over the glossary and the entries being asked about.
     *
     * @param clusters the clusters of the held entries with the asked ones in place of their held versions
     * @param heldEntries every entry the glossary holds; never null
     * @param asked the entries this call asks about; never null
     * @return the agreement, whose held renderings are those of the entries not asked about
     */
    static ClusterAgreement over(
            final NameClusters clusters, final List<GlossaryEntry> heldEntries, final List<GlossaryEntry> asked) {
        Objects.requireNonNull(clusters, "clusters");
        final Set<String> askedIds = new HashSet<>();
        asked.forEach(entry -> askedIds.add(entry.id()));
        final Map<String, String> targets = new HashMap<>();
        final Map<String, String> words = new HashMap<>();
        for (final GlossaryEntry entry : heldEntries) {
            final String target = entry.target();
            // Only the person's rendering binds: an earlier suggestion nobody confirmed is no cluster's (15i review).
            if (!askedIds.contains(entry.id())
                    && entry.origin() == TargetOrigin.PERSON
                    && target != null
                    && !target.isBlank()) {
                targets.put(entry.id(), target.strip());
                groupOf(entry.type()).ifPresent(group -> learnWords(group, entry.term(), target.strip(), words));
            }
        }
        return new ClusterAgreement(clusters, Map.copyOf(targets), Map.copyOf(words));
    }

    /**
     * What the glossary already renders of an entry's cluster, as the call states it.
     *
     * @param entry an entry being asked about; never null
     * @return {@code Arlo Venn → Арло Венн, so Venn → Венн} when the entry is another spelling of a held full name
     *     with a rendering; empty when it stands alone, is the full name or the full name has no rendering yet
     */
    Optional<String> statementFor(final GlossaryEntry entry) {
        final Optional<GlossaryEntry> full =
                clusters.fullNameOf(entry).filter(anchor -> !anchor.id().equals(entry.id()));
        final String rendering = full.map(anchor -> held.get(anchor.id())).orElse(null);
        if (rendering == null) {
            return Optional.empty();
        }
        final GlossaryEntry anchor = full.get();
        final String whole = anchor.term() + " → " + rendering;
        final Optional<String> part = partOf(anchor.term(), rendering, entry.term());
        log.debug(
                "Cluster statement for entry {}: full name {} has a rendering, part found {}",
                entry.id(),
                anchor.id(),
                part.isPresent());
        return Optional.of(
                part.map(word -> whole + ", so " + entry.term() + " → " + word).orElse(whole));
    }

    /**
     * Brings the suggestions of one person's spellings into agreement.
     *
     * @param suggestions the suggestions of a call; never null
     * @return the suggestions in their order, a spelling's target replaced by the cluster's word where they differ
     */
    List<Suggestion> align(final List<Suggestion> suggestions) {
        final Map<String, String> asked = new HashMap<>();
        suggestions.forEach(suggestion -> asked.put(suggestion.entry().id(), suggestion.target()));
        final List<Suggestion> aligned = new ArrayList<>();
        for (final Suggestion suggestion : suggestions) {
            final String target = agreed(suggestion.entry(), suggestion.target(), asked);
            if (!target.equals(suggestion.target())) {
                log.debug(
                        "Cluster agreement changed the target of entry {}",
                        suggestion.entry().id());
                log.trace("Cluster agreement: {} → {}", suggestion.target(), target);
            }
            aligned.add(target.equals(suggestion.target()) ? suggestion : new Suggestion(suggestion.entry(), target));
        }
        return withSharedWords(aligned);
    }

    // One word of a person's name has one rendering across every person's name (15i.N gate): two anchors of one
    // person ("Tam Wren", "Tamsin Wren") or a family share a surname, which no cluster joins. A held rendering of the
    // word wins, else the first suggestion that renders it; a rendering with another number of words is left alone.
    private List<Suggestion> withSharedWords(final List<Suggestion> suggestions) {
        final Map<String, String> words = new HashMap<>(heldWords);
        final List<Suggestion> agreed = new ArrayList<>();
        for (final Suggestion suggestion : suggestions) {
            final GlossaryEntry entry = suggestion.entry();
            final Optional<String> group = groupOf(entry.type());
            if (group.isEmpty()) {
                agreed.add(suggestion);
                continue;
            }
            final String target = sharedWordsOf(group.get(), entry.term(), suggestion.target(), words);
            if (!target.equals(suggestion.target())) {
                log.debug("Shared-word agreement changed the target of entry {}", entry.id());
                log.trace("Shared-word agreement: {} → {}", suggestion.target(), target);
            }
            learnWords(group.get(), entry.term(), target, words);
            agreed.add(target.equals(suggestion.target()) ? suggestion : new Suggestion(entry, target));
        }
        return agreed;
    }

    private static Optional<String> groupOf(final TermType type) {
        if (type == TermType.CHARACTER) {
            return Optional.of("person:");
        }
        return PLACE_GROUP.contains(type) ? Optional.of("place:") : Optional.empty();
    }

    private static String sharedWordsOf(
            final String group, final String term, final String target, final Map<String, String> words) {
        final List<String> source = wordsOf(term);
        final List<int[]> spans = spansOf(target);
        if (source.size() != spans.size()) {
            return target;
        }
        final StringBuilder written = new StringBuilder(target);
        for (int word = headOf(term) - 1; word >= 0; word--) {
            final String known = words.get(group + source.get(word));
            if (known != null) {
                written.replace(spans.get(word)[0], spans.get(word)[1], known);
            }
        }
        return written.toString();
    }

    // Each word of a name whose rendering has as many words, kept as the first rendering seen of it.
    private static void learnWords(
            final String group, final String term, final String target, final Map<String, String> words) {
        final List<String> source = wordsOf(term);
        final List<int[]> spans = spansOf(target);
        if (source.size() == spans.size()) {
            for (int word = 0; word < headOf(term); word++) {
                words.putIfAbsent(group + source.get(word), target.substring(spans.get(word)[0], spans.get(word)[1]));
            }
        }
    }

    private String agreed(final GlossaryEntry entry, final String target, final Map<String, String> asked) {
        final Optional<GlossaryEntry> full = clusters.fullNameOf(entry);
        if (full.isEmpty()) {
            return target;
        }
        if (full.get().id().equals(entry.id())) {
            return withHeldMembers(entry, target);
        }
        final String rendering =
                held.getOrDefault(full.get().id(), asked.get(full.get().id()));
        return rendering == null
                ? target
                : partOf(full.get().term(), rendering, entry.term()).orElse(target);
    }

    private String withHeldMembers(final GlossaryEntry full, final String target) {
        String agreed = target;
        for (final GlossaryEntry member : clusters.membersOf(full)) {
            final String own = held.get(member.id());
            if (own != null && !member.id().equals(full.id())) {
                agreed = withWords(full.term(), agreed, member.term(), own).orElse(agreed);
            }
        }
        return agreed;
    }

    // The part of a full name's rendering that stands where the member's words stand in the full name.
    private static Optional<String> partOf(final String full, final String rendering, final String member) {
        final List<int[]> spans = spansOf(rendering);
        final int first = indexOf(wordsOf(full), wordsOf(member));
        if (first < 0 || spans.size() != wordsOf(full).size()) {
            return Optional.empty();
        }
        final int last = first + wordsOf(member).size() - 1;
        return Optional.of(rendering.substring(spans.get(first)[0], spans.get(last)[1]));
    }

    // The full name's rendering with the member's rendering written over the member's words.
    private static Optional<String> withWords(
            final String full, final String rendering, final String member, final String memberRendering) {
        final List<int[]> spans = spansOf(rendering);
        final List<int[]> own = spansOf(memberRendering);
        final int first = indexOf(wordsOf(full), wordsOf(member));
        if (first < 0
                || spans.size() != wordsOf(full).size()
                || own.size() != wordsOf(member).size()) {
            return Optional.empty();
        }
        final StringBuilder written = new StringBuilder(rendering);
        for (int word = own.size() - 1; word >= 0; word--) {
            final int[] at = spans.get(first + word);
            written.replace(at[0], at[1], memberRendering.substring(own.get(word)[0], own.get(word)[1]));
        }
        return Optional.of(written.toString());
    }

    // The words before the first one in lower case: in "Tower of Arlen" only "Tower" keeps its form, since a target
    // language may decline the rest ("Вежа в Лондоні").
    private static int headOf(final String term) {
        final List<int[]> spans = spansOf(term);
        for (int word = 0; word < spans.size(); word++) {
            if (Character.isLowerCase(term.codePointAt(spans.get(word)[0]))) {
                return word;
            }
        }
        return spans.size();
    }

    private static List<String> wordsOf(final String name) {
        return spansOf(name).stream()
                .map(span -> name.substring(span[0], span[1]).toLowerCase(Locale.ROOT))
                .toList();
    }

    private static List<int[]> spansOf(final String text) {
        final List<int[]> spans = new ArrayList<>();
        final Matcher words = WORD.matcher(text);
        while (words.find()) {
            spans.add(new int[] {words.start(), words.end()});
        }
        return spans;
    }

    private static int indexOf(final List<String> whole, final List<String> part) {
        return part.isEmpty() ? -1 : Collections.indexOfSubList(whole, part);
    }
}
