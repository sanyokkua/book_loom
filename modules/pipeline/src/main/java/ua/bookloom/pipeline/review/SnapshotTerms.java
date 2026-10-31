package ua.bookloom.pipeline.review;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TargetState;

/** The glossary a stored context snapshot recorded, read back as entries for a replayed draft. */
// Checkstyle parses source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class SnapshotTerms {

    static List<GlossaryEntry> entriesOf(final ContextSnapshot snapshot, final String projectId) {
        log.debug(
                "Snapshot glossary project={} terms={}",
                projectId,
                snapshot.glossary().size());
        return snapshot.glossary().stream()
                .map(term -> new GlossaryEntry(
                        term.term(),
                        projectId,
                        term.term(),
                        List.of(),
                        false,
                        term.target(),
                        stateOf(term),
                        term.suggested() ? Origin.LIKELY : Origin.USER,
                        term.type(),
                        null,
                        Origin.USER,
                        term.gender(),
                        term.genderOrigin(),
                        false,
                        false,
                        null,
                        null))
                .toList();
    }

    private static TargetState stateOf(final SnapshotTerm term) {
        if (term.locked()) {
            return TargetState.LOCKED;
        }
        return term.suggested() ? TargetState.SUGGESTED : TargetState.PINNED;
    }
}
