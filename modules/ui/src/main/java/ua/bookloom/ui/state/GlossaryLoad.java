package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;

/** Reads a project's glossary, and proposes names with the deterministic scan only when there is none yet. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryLoad {

    static Result<List<GlossaryEntry>> loadOrScan(final GlossaryService glossary, final String project) {
        final Result<List<GlossaryEntry>> held = glossary.entries(project);
        if (held.isErr() || !Objects.requireNonNull(held.data(), "held").isEmpty()) {
            log.debug("glossary of project {} read, ok {}: no scan", project, held.isOk());
            return held;
        }
        log.debug("glossary of project {} is empty: running the deterministic scan", project);
        final Result<List<GlossaryEntry>> proposed = glossary.scan(project).map(EntryChanges::added);
        log.info(
                "deterministic scan of project {} proposed {} entries",
                project,
                proposed.isOk()
                        ? Objects.requireNonNull(proposed.data(), "proposed").size()
                        : 0);
        return proposed;
    }
}
