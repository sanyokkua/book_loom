package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What the Add term card collected, before the term has an id or a project.
 *
 * @param term the source term as typed; a blank one is refused by the view model
 * @param target the chosen rendering as typed, blank when none was given
 * @param type the chosen type
 * @param gender the chosen gender
 * @param locked whether the term is to be locked
 */
public record NewTerm(String term, String target, TermType type, Gender gender, boolean locked) {

    /** Validates the invariants a caller is entitled to assume. */
    public NewTerm {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(gender, "gender");
    }

    /**
     * Why the term cannot be added to a glossary.
     *
     * @param held the entries the glossary already has
     * @param messages the catalogue the reason is worded from
     * @return the reason in the display language, or empty if the term may be added; checked in the order a blank
     *     source, a duplicate ignoring case, then a lock with no target
     */
    Optional<String> refusal(final List<GlossaryEntry> held, final Messages messages) {
        final String source = term.strip();
        if (source.isEmpty()) {
            return Optional.of(messages.get(MessageKey.DIALOG_ADD_TERM_REQUIRED));
        }
        if (held.stream().anyMatch(row -> row.term().equalsIgnoreCase(source))) {
            return Optional.of(messages.get(MessageKey.NAMES_STYLE_DUPLICATE, source));
        }
        if (locked && target.isBlank()) {
            return Optional.of(messages.get(MessageKey.NAMES_STYLE_LOCK_NEEDS_TARGET));
        }
        return Optional.empty();
    }
}
