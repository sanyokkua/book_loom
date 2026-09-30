package ua.bookloom.ui.state;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** The four single-field changes a glossary row allows, each leaving the rest of the entry as it was. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryEdits {

    static GlossaryEntry target(final GlossaryEntry held, final @Nullable String target) {
        return new GlossaryEntry(
                held.id(), held.projectId(), held.term(), target, held.type(), held.gender(), held.locked());
    }

    static GlossaryEntry locked(final GlossaryEntry held, final boolean locked) {
        return new GlossaryEntry(
                held.id(), held.projectId(), held.term(), held.target(), held.type(), held.gender(), locked);
    }

    static GlossaryEntry type(final GlossaryEntry held, final TermType type) {
        return new GlossaryEntry(
                held.id(), held.projectId(), held.term(), held.target(), type, held.gender(), held.locked());
    }

    static GlossaryEntry gender(final GlossaryEntry held, final Gender gender) {
        return new GlossaryEntry(
                held.id(), held.projectId(), held.term(), held.target(), held.type(), gender, held.locked());
    }

    static int indexOf(final List<GlossaryEntry> rows, final String entryId) {
        for (int at = 0; at < rows.size(); at++) {
            if (rows.get(at).id().equals(entryId)) {
                return at;
            }
        }
        return -1;
    }
}
