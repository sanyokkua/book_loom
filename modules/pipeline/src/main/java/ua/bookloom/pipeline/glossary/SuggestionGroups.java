package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.NamePolicy;

/**
 * The entries one suggestion run asks, grouped by the policy each is asked under: the Book Brief's, except a name whose
 * meaning the book plays on and the person chose to translate, which is asked under Translate in calls of its own
 * (design D5). The groups' batches are announced as one series. One run's calls are made in order on one thread.
 */
@Slf4j
final class SuggestionGroups {

    private final Map<NamePolicy, List<GlossaryEntry>> groups;
    private final int total;
    private int started;

    private SuggestionGroups(final Map<NamePolicy, List<GlossaryEntry>> groups, final int total) {
        this.groups = groups;
        this.total = total;
    }

    /**
     * Groups the asked entries.
     *
     * @param asked the entries that need a call, in their order; never null
     * @param policy the Book Brief's name policy; never null
     * @param batchSize how many entries one call carries; positive
     * @return the groups, in the order their policies first occur
     */
    static SuggestionGroups of(final List<GlossaryEntry> asked, final NamePolicy policy, final int batchSize) {
        Objects.requireNonNull(asked, "asked");
        Objects.requireNonNull(policy, "policy");
        final Map<NamePolicy, List<GlossaryEntry>> groups = new LinkedHashMap<>();
        for (final GlossaryEntry entry : asked) {
            final boolean isTranslatedByChoice =
                    policy == NamePolicy.TRANSLITERATE && entry.meaningChoice() == MeaningChoice.TRANSLATE;
            groups.computeIfAbsent(isTranslatedByChoice ? NamePolicy.TRANSLATE : policy, key -> new ArrayList<>())
                    .add(entry);
        }
        final int total = groups.values().stream()
                .mapToInt(group -> Math.ceilDiv(group.size(), batchSize))
                .sum();
        log.debug("Suggestion groups policy={} groups={} batches={}", policy, groups.keySet(), total);
        return new SuggestionGroups(groups, total);
    }

    Map<NamePolicy, List<GlossaryEntry>> byPolicy() {
        return groups;
    }

    int batches() {
        return total;
    }

    int translatedByChoice(final NamePolicy policy) {
        return policy == NamePolicy.TRANSLATE
                ? 0
                : groups.getOrDefault(NamePolicy.TRANSLATE, List.of()).size();
    }

    /** The announcement of the next batch, counted across every group. */
    BatchStarted next() {
        started++;
        return new BatchStarted(CallKind.SUGGEST_TARGETS, started, total);
    }

    /**
     * The suggestions with a meaning note only where one belongs: a name sounded out under Transliterate. Under a policy
     * that translates or keeps names, or for an entry that is no name (a common noun is translated, so its meaning is
     * its target), the note is dropped.
     *
     * @param suggestions the suggestions of one group; never null
     * @param policy the policy the group was asked under; never null
     * @param context the held glossary and the readings, which decide whether an entry is a name; never null
     * @return the suggestions in their order; never null
     */
    static List<SuggestionReplies.Suggestion> noted(
            final List<SuggestionReplies.Suggestion> suggestions,
            final NamePolicy policy,
            final TargetContext context) {
        final boolean isSoundedOut = policy == NamePolicy.TRANSLITERATE;
        return suggestions.stream()
                .map(suggestion -> isSoundedOut && SuggestionLocks.isNameLike(suggestion.entry(), context)
                        ? suggestion
                        : suggestion.withoutMeaning())
                .toList();
    }
}
