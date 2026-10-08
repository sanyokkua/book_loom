package ua.bookloom.pipeline.lexicon;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.RenderingConsistency;

/**
 * What a run does with the lexicon: reads its entries for a prompt, records the pairs a text proved, and picks the key
 * terms a set of source texts names. A store failure never stops a draft — the lexicon is a hint — so a read that fails
 * answers an empty list and a record that fails is logged and skipped.
 */
@Slf4j
@RequiredArgsConstructor
public final class Lexicon {

    private final LexiconRepository repository;

    /**
     * The project's entries as they stand now.
     *
     * @param projectId the non-null project id
     * @return the entries; never null, empty when there are none or the store failed
     */
    public List<LexiconEntry> entries(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        final List<LexiconEntry> held = repository.all(projectId).data();
        if (held == null) {
            log.warn("The lexicon could not be read; the run goes on without it project={}", projectId);
            return List.of();
        }
        return held;
    }

    /**
     * Counts each verified pair once more.
     *
     * @param projectId the non-null project id
     * @param pairs the non-null verified pairs of one translated item
     */
    public void record(final String projectId, final List<TermMappingVerifier.Pair> pairs) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(pairs, "pairs");
        for (final TermMappingVerifier.Pair pair : pairs) {
            if (repository.record(projectId, pair.term(), pair.rendering()).isErr()) {
                log.warn("A term pair could not be recorded project={}", projectId);
            } else {
                log.trace("Recorded term pair {} -> {}", pair.term(), pair.rendering());
            }
        }
        log.debug("Recorded {} term pairs project={}", pairs.size(), projectId);
    }

    /**
     * Writes the one INFO line that says how consistently the run kept its recurring terms, as
     * {@link RenderingConsistency#describe()} words it.
     *
     * @param projectId the non-null project id
     */
    public void logConsistency(final String projectId) {
        log.info(
                "Lexicon consistency project={} {}",
                projectId,
                RenderingConsistency.of(entries(projectId)).describe());
    }

    /**
     * The terms of the entries that a set of source texts names, in the entries' order.
     *
     * @param entries the non-null lexicon entries
     * @param texts the non-null source texts, tokens removed
     * @return the key terms present; never null, empty when none is
     */
    public static List<String> termsIn(final List<LexiconEntry> entries, final List<String> texts) {
        return entries.stream()
                .map(LexiconEntry::term)
                .filter(term -> texts.stream().anyMatch(text -> TermMatch.isNamedIn(term, text)))
                .toList();
    }
}
