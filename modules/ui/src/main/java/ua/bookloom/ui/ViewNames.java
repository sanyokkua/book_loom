package ua.bookloom.ui;

import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * Every navigation entry, in reading order, so no code addresses a screen by a spelt-out path or label.
 *
 * <p>Declaration order is load-bearing: the navigation column is generated from it and "the next step" follows it.
 * An entry carries a resource only once it has a screen; an entry without one is inert, and {@link #isAvailable()} is
 * the single place that says so, so a screen task turns its entry on by adding a path here and nowhere else.
 */
public enum ViewNames {
    /** Project list; has no screen and no step number. */
    PROJECTS(MessageKey.NAV_PROJECTS, NavGroup.WORKFLOW, null, null, Feather.FOLDER),
    /** Choosing the book to translate. */
    IMPORT(MessageKey.NAV_IMPORT, NavGroup.WORKFLOW, 1, "/ua/bookloom/ui/screen/import.fxml", null),
    /** Describing the book to the translator. */
    BOOK_BRIEF(MessageKey.NAV_BRIEF, NavGroup.WORKFLOW, 2, "/ua/bookloom/ui/screen/book-brief.fxml", null),
    /** Choosing which parts of the book to translate. */
    STRUCTURE(MessageKey.NAV_STRUCTURE, NavGroup.WORKFLOW, 3, "/ua/bookloom/ui/screen/structure.fxml", null),
    /** Glossary and style; has no screen yet. */
    NAMES_STYLE(MessageKey.NAV_NAMES_AND_STYLE, NavGroup.WORKFLOW, 4, null, null),
    /** The running translation dashboard. */
    TRANSLATING(MessageKey.NAV_TRANSLATING, NavGroup.WORKFLOW, 5, "/ua/bookloom/ui/screen/translating.fxml", null),
    /** Review queue; has no screen yet. */
    REVIEW(MessageKey.NAV_REVIEW, NavGroup.WORKFLOW, 6, null, null),
    /** Writing the translated book out. */
    EXPORT(MessageKey.NAV_EXPORT, NavGroup.WORKFLOW, 7, "/ua/bookloom/ui/screen/export.fxml", null),
    /** Provider and appearance settings. */
    SETTINGS(
            MessageKey.NAV_SETTINGS,
            NavGroup.APPLICATION,
            null,
            "/ua/bookloom/ui/screen/settings.fxml",
            Feather.SETTINGS);

    private final MessageKey messageKey;
    private final NavGroup group;
    private final @Nullable Integer step;
    private final @Nullable String resource;
    private final @Nullable Feather icon;

    ViewNames(
            final MessageKey messageKey,
            final NavGroup group,
            final @Nullable Integer step,
            final @Nullable String resource,
            final @Nullable Feather icon) {
        this.messageKey = messageKey;
        this.group = group;
        this.step = step;
        this.resource = resource;
        this.icon = icon;
    }

    /**
     * Returns the catalogue entry naming this screen.
     *
     * @return the key used for both the navigation label and the breadcrumb; never null
     */
    public MessageKey messageKey() {
        return messageKey;
    }

    /**
     * Returns the navigation group this entry is listed under.
     *
     * @return the group; never null
     */
    public NavGroup group() {
        return group;
    }

    /**
     * Returns the position in the workflow.
     *
     * @return the step from 1 to 7, or empty for an entry outside the numbered workflow
     */
    public OptionalInt step() {
        return step == null ? OptionalInt.empty() : OptionalInt.of(step);
    }

    /**
     * Returns the classpath location of this screen's view.
     *
     * @return the absolute resource path, or empty while the screen does not exist
     */
    public Optional<String> resource() {
        return Optional.ofNullable(resource);
    }

    /**
     * Returns the glyph the navigation column draws in place of a step number.
     *
     * @return the Feather icon of an entry outside the numbered workflow, or empty for a numbered entry, whose badge is
     *     its step. Typed as the concrete pack enum because Error Prone's {@code ImmutableEnumChecker} rejects an enum
     *     field of the mutable-looking {@code Ikon} interface type.
     */
    public Optional<Feather> icon() {
        return Optional.ofNullable(icon);
    }

    /**
     * Tells whether activating this entry can show anything.
     *
     * @return {@code true} if a view resource exists, {@code false} if the entry is inert
     */
    public boolean isAvailable() {
        return resource != null;
    }
}
