package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.glossary.NameCandidate;

/**
 * Keeps the forms of the stored recurring terms in step with the book: a scan that meets a held term in a form the
 * entry does not list yet adds it, so the entry is matched, read and learned through every form the book writes.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LexiconForms {

    /**
     * Adds to each held entry the forms of the scanned group it belongs to.
     *
     * @param lexicon the store; never null
     * @param projectId the project; never null
     * @param groups the scan's groups, each a representative with its other forms as aliases; never null
     * @return how many entries gained a form, or a store's error
     */
    static Result<Integer> merge(
            final LexiconRepository lexicon, final String projectId, final List<NameCandidate> groups) {
        Objects.requireNonNull(lexicon, "lexicon");
        final Map<String, NameCandidate> byForm = new HashMap<>();
        groups.forEach(
                group -> KeyTermScan.formsOf(group).forEach(form -> byForm.put(LexiconEntry.keyOf(form), group)));
        return lexicon.all(projectId).flatMap(held -> {
            int changed = 0;
            for (final LexiconEntry entry : held) {
                final Optional<List<String>> more = added(entry, byForm);
                if (more.isEmpty()) {
                    continue;
                }
                final List<String> forms = more.get();
                final Result<Optional<LexiconEntry>> stored =
                        lexicon.update(projectId, entry.term(), found -> with(found, forms));
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
                changed++;
            }
            log.debug("Lexicon forms merged project={} groups={} entriesChanged={}", projectId, groups.size(), changed);
            return Result.ok(changed);
        });
    }

    /**
     * The other forms of each group by its term's key, as the lexicon reading takes them.
     *
     * @param groups the non-null groups, each a representative with its other forms as aliases
     * @return never null
     */
    static Map<String, List<String>> byKey(final List<NameCandidate> groups) {
        final Map<String, List<String>> forms = new HashMap<>();
        groups.forEach(group -> forms.put(LexiconEntry.keyOf(group.term()), group.aliases()));
        return forms;
    }

    /**
     * The stored forms of each entry by its term's key, as the lexicon reading takes them.
     *
     * @param entries the non-null entries
     * @return never null
     */
    static Map<String, List<String>> ofEntries(final List<LexiconEntry> entries) {
        final Map<String, List<String>> forms = new HashMap<>();
        entries.forEach(entry -> forms.put(LexiconEntry.keyOf(entry.term()), entry.forms()));
        return forms;
    }

    /**
     * An entry with more forms.
     *
     * @param entry the non-null entry
     * @param forms the forms to keep beside its term; its own term and repeats are left out
     * @return a copy holding its forms and the new ones, in that order
     */
    static LexiconEntry with(final LexiconEntry entry, final List<String> forms) {
        final String own = LexiconEntry.keyOf(entry.term());
        final Set<String> keys = new LinkedHashSet<>();
        final List<String> all = new ArrayList<>();
        for (final String form : concat(entry.forms(), forms)) {
            final String key = LexiconEntry.keyOf(form);
            if (!key.equals(own) && !key.isEmpty() && keys.add(key)) {
                all.add(form);
            }
        }
        return new LexiconEntry(
                entry.projectId(),
                entry.term(),
                entry.renderings(),
                entry.chosen(),
                entry.suggested(),
                entry.learned(),
                entry.chosenOrigin(),
                all);
    }

    // The forms of the group the entry's term or a form belongs to that the entry does not hold yet.
    private static Optional<List<String>> added(final LexiconEntry entry, final Map<String, NameCandidate> byForm) {
        final Set<String> own = new LinkedHashSet<>();
        own.add(LexiconEntry.keyOf(entry.term()));
        entry.forms().forEach(form -> own.add(LexiconEntry.keyOf(form)));
        return own.stream()
                .map(byForm::get)
                .filter(Objects::nonNull)
                .findFirst()
                .map(group -> KeyTermScan.formsOf(group).stream()
                        .filter(form -> !own.contains(LexiconEntry.keyOf(form)))
                        .toList())
                .filter(more -> !more.isEmpty());
    }

    private static List<String> concat(final List<String> first, final List<String> second) {
        final List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
