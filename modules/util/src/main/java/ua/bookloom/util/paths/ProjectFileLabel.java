package ua.bookloom.util.paths;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** The part of a named project's name that tells its translation's file from another of the same book and pair. */
// Checkstyle reads the source before Lombok adds the private constructor (see DestinationPath).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProjectFileLabel {

    private static final String SEPARATOR = " · ";
    private static final String ARROW = "→ ";
    private static final String NO_TARGET = "…";

    /**
     * Finds the label a project's file name carries after the language, when it needs one.
     *
     * @param self the non-null project the file is for
     * @param all the non-null stored projects, which may include {@code self}
     * @return the label, or empty when the project is not named by the person, is alone with its pair, or its name
     *     reduces to nothing
     */
    public static Optional<String> of(final Project self, final List<Project> all) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(all, "all");
        if (self.nameOrigin() != NameOrigin.USER || !sharesPair(self, all)) {
            return Optional.empty();
        }
        final String label = reduced(remainder(self));
        return label.isEmpty() ? Optional.empty() : Optional.of(label);
    }

    private static boolean sharesPair(final Project self, final List<Project> all) {
        return all.stream()
                .filter(other -> !other.id().equals(self.id()))
                .filter(other -> other.contentHash().equals(self.contentHash()))
                .filter(other -> Objects.equals(
                        other.brief().sourceLanguage(), self.brief().sourceLanguage()))
                .anyMatch(other -> Objects.equals(
                        other.brief().targetLanguage(), self.brief().targetLanguage()));
    }

    // The name after the automatic name, else after the book's title, else the whole name.
    private static String remainder(final Project self) {
        final String name = self.name();
        final String automatic = automaticName(self);
        if (startsWithIgnoringCase(name, automatic)) {
            return name.substring(automatic.length());
        }
        return startsWithIgnoringCase(name, self.title())
                ? name.substring(self.title().length())
                : name;
    }

    // The same text the project service gives an automatic name: title, separator, "EN " when the source is chosen, "→
    // UK".
    private static String automaticName(final Project self) {
        final String source = self.brief().sourceLanguage();
        final String target = self.brief().targetLanguage();
        final String from = source == null ? "" : primaryCode(source) + " ";
        final String to = target == null ? NO_TARGET : primaryCode(target);
        return self.title() + SEPARATOR + from + ARROW + to;
    }

    private static boolean startsWithIgnoringCase(final String text, final String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String primaryCode(final String tag) {
        final int dash = tag.indexOf('-');
        return (dash < 0 ? tag : tag.substring(0, dash)).toUpperCase(Locale.ROOT);
    }

    private static String reduced(final String text) {
        final StringBuilder out = new StringBuilder();
        for (final char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else if (Character.isWhitespace(c) || c == '-') {
                out.append('-');
            }
        }
        return out.toString().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
    }
}
