package ua.bookloom.pipeline.review;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetOrigin;

/** The glossary a stored context snapshot recorded, read back as entries for a replayed draft. */
// Checkstyle parses source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SnapshotTerms {

    static List<GlossaryEntry> entriesOf(final ContextSnapshot snapshot, final String projectId) {
        return snapshot.glossary().stream()
                .map(term -> new GlossaryEntry(
                        term.term(),
                        projectId,
                        term.term(),
                        term.target(),
                        term.type(),
                        term.gender(),
                        term.locked(),
                        term.suggested() ? TargetOrigin.SUGGESTED : TargetOrigin.PERSON))
                .toList();
    }
}
