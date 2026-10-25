package ua.bookloom.api.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;

/**
 * A book opened for translation, keyed by a stable project id, together with its Book Brief.
 *
 * @param id the project's stable id
 * @param source the source file's path
 * @param format the source book's format
 * @param contentHash a hash identifying the source file's content
 * @param brief the project's current Book Brief
 * @param name the project's display name; unique within its book ignoring letter case
 * @param nameOrigin whether the application or the person chose {@code name}
 * @param title the book's title as its metadata names it, or its file name; what the automatic name starts with
 * @param createdAt when the project was first stored
 * @param lastOpenedAt when the project was last opened
 */
public record Project(
        String id,
        Path source,
        BookFormat format,
        String contentHash,
        BookBrief brief,
        String name,
        NameOrigin nameOrigin,
        String title,
        Instant createdAt,
        Instant lastOpenedAt) {

    /** Longest name a person may type, in characters; an automatic name, built from the title, may be longer. */
    public static final int MAX_NAME_LENGTH = 120;

    private static final String NAME_SEPARATOR = " · ";
    private static final String ARROW = "→ ";

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public Project {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(nameOrigin, "nameOrigin");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastOpenedAt, "lastOpenedAt");
    }

    /**
     * Returns the fold key of the name, so {@code Земля} and {@code земля} are one name within a book.
     *
     * @return the stripped, lower-cased name; derived, so it can never disagree with {@code name}
     */
    public String nameKey() {
        return keyOf(name);
    }

    /**
     * Folds a name to its comparison key.
     *
     * @param name the non-null name
     * @return the stripped name in lower case, locale-independent
     */
    public static String keyOf(final String name) {
        return Objects.requireNonNull(name, "name").strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Tells whether a typed name holds a character a name may not: a control character, which no one can see, or a
     * path separator, since a name may become part of a file name.
     *
     * @param name the non-null name
     * @return {@code true} if the name holds {@code /}, {@code \} or a control character, {@code false} otherwise
     */
    public static boolean hasForbiddenCharacter(final String name) {
        return Objects.requireNonNull(name, "name")
                .chars()
                .anyMatch(character -> character == '/' || character == '\\' || Character.isISOControl(character));
    }

    /**
     * Builds the automatic name: the book's title, then the language pair, each shown as its primary subtag. Before a
     * target is chosen there is no pair to show, so the name carries no arrow; the source code alone still tells two
     * untargeted projects of one book apart, which the per-book name rule needs. A source left on Auto has no code to
     * show, so the title leads straight into the arrow, with no separator before an empty slot.
     *
     * @param title the non-null book title
     * @param source the source language tag, or {@code null} when it is not known yet
     * @param target the target language tag, or {@code null} when none is chosen yet
     * @return for example {@code Title · EN → UK}, or {@code Title → UK} with no source; {@code Title · EN} or
     *     {@code Title} while no target is chosen
     */
    public static String automaticName(
            final String title, final @Nullable String source, final @Nullable String target) {
        Objects.requireNonNull(title, "title");
        if (target == null) {
            return source == null ? title : title + NAME_SEPARATOR + primaryCode(source);
        }
        final String from = source == null ? " " : NAME_SEPARATOR + primaryCode(source) + " ";
        return title + from + ARROW + primaryCode(target);
    }

    private static String primaryCode(final String tag) {
        final int dash = tag.indexOf('-');
        return (dash < 0 ? tag : tag.substring(0, dash)).toUpperCase(Locale.ROOT);
    }

    /**
     * Returns a project with a replacement brief, preserving its identity.
     *
     * @param brief the non-null replacement brief
     * @return a new project with the supplied brief
     */
    public Project withBrief(final BookBrief brief) {
        Objects.requireNonNull(brief, "brief");
        return new Project(id, source, format, contentHash, brief, name, nameOrigin, title, createdAt, lastOpenedAt);
    }

    /**
     * Returns a project with another name and who chose it, preserving everything else.
     *
     * @param name the non-null replacement name
     * @param origin the non-null origin of that name
     * @return a new project with the supplied name
     */
    public Project withName(final String name, final NameOrigin origin) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(origin, "origin");
        return new Project(id, source, format, contentHash, brief, name, origin, title, createdAt, lastOpenedAt);
    }
}
