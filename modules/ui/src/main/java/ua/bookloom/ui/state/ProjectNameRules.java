package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/**
 * What a typed project name is judged against: the names the other projects of the same book already have.
 *
 * @param usedKeys the folded keys ({@link Project#keyOf}) of the other projects of the book; another book's names are
 *     not here, so two books may share a name
 * @param automaticName the automatic name of the project the card is about, or {@code null} where the card computes
 *     its own from a language the person chooses
 * @param pairFree whether no other project of the book has this project's source and target language
 */
public record ProjectNameRules(
        Set<String> usedKeys, @Nullable String automaticName, boolean pairFree) {

    /** The verdict on one typed name. */
    public enum Check {
        /** Nothing but spaces was typed. */
        EMPTY,
        /** Another project of the book has this name, ignoring case and outer spaces. */
        USED,
        /** The typed name is longer than {@link Project#MAX_NAME_LENGTH}. */
        TOO_LONG,
        /** The name holds a path separator or a control character ({@link Project#hasForbiddenCharacter}). */
        FORBIDDEN_CHARACTER,
        /** The name may be used. */
        OK
    }

    /** Copies the keys. */
    public ProjectNameRules {
        usedKeys = Set.copyOf(Objects.requireNonNull(usedKeys, "usedKeys"));
    }

    /**
     * Judges a typed name.
     *
     * @param typed the text in the field
     * @return {@link Check#EMPTY} for blank text, {@link Check#TOO_LONG} or {@link Check#FORBIDDEN_CHARACTER} for a
     *     typed name that is not the automatic one and breaks those rules, {@link Check#USED} when another project of
     *     the book has the name, else {@link Check#OK}
     */
    public Check check(final String typed) {
        Objects.requireNonNull(typed, "typed");
        if (typed.isBlank()) {
            return Check.EMPTY;
        }
        final String name = typed.strip();
        if (!name.equals(automaticName) && name.length() > Project.MAX_NAME_LENGTH) {
            return Check.TOO_LONG;
        }
        if (Project.hasForbiddenCharacter(name)) {
            return Check.FORBIDDEN_CHARACTER;
        }
        return usedKeys.contains(Project.keyOf(typed)) ? Check.USED : Check.OK;
    }

    /**
     * The rules for a project among the other projects of its book.
     *
     * @param siblings the other projects of the same book, never this one
     * @param own the project the card is about
     * @return rules whose automatic name is the project's own
     */
    static ProjectNameRules forProject(final List<Project> siblings, final Project own) {
        final boolean taken = siblings.stream()
                .anyMatch(other -> Objects.equals(
                                other.brief().sourceLanguage(), own.brief().sourceLanguage())
                        && Objects.equals(
                                other.brief().targetLanguage(), own.brief().targetLanguage()));
        // An automatic name is the name the project has; a typed one leaves the automatic name to be worked out.
        final String automatic = own.nameOrigin() == NameOrigin.AUTO
                ? own.name()
                : automaticOf(own, own.brief().targetLanguage());
        return new ProjectNameRules(keysOf(siblings), automatic, !taken);
    }

    /**
     * The rules for a new project of a book, whose pair is not chosen yet.
     *
     * @param projects every project of the book
     * @return rules with no automatic name
     */
    static ProjectNameRules forBook(final List<Project> projects) {
        return new ProjectNameRules(keysOf(projects), null, true);
    }

    /**
     * The rules for a second translation of a language pair a project of the book already has.
     *
     * @param projects every project of the book
     * @param existing the project that holds the pair
     * @param target the target language being asked for
     * @return rules whose automatic name is the name the pair would have had
     */
    static ProjectNameRules forSecond(final List<Project> projects, final Project existing, final String target) {
        return new ProjectNameRules(keysOf(projects), automaticOf(existing, target), false);
    }

    private static Set<String> keysOf(final List<Project> projects) {
        return projects.stream().map(Project::nameKey).collect(Collectors.toUnmodifiableSet());
    }

    private static String automaticOf(final Project project, final @Nullable String target) {
        return Project.automaticName(project.title(), project.brief().sourceLanguage(), target);
    }
}
