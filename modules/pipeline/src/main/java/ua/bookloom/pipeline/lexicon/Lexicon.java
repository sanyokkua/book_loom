package ua.bookloom.pipeline.lexicon;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.RenderingConsistency;

/**
 * What a run does with the lexicon: reads its entries for a prompt and logs how consistently the run kept them. A store
 * failure never stops a draft — the lexicon is a hint — so a read that fails answers an empty list.
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
}
