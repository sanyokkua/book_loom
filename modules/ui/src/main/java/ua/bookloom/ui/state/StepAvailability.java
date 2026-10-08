package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * Which workflow steps cannot be opened yet and why, so the navigation, the toolbar and the navigator all give the same
 * answer. Import and Settings are always open; every other screen needs a book, and the steps that read the languages
 * (structure, names and style, translating) also need two different ones. Export needs only a book, because a stopped
 * or running book can be exported at any time. The properties are touched on the FX Application Thread only.
 */
@Slf4j
@Singleton
public final class StepAvailability implements StepLocks {

    private final CurrentProject project;
    private final Map<ViewNames, ReadOnlyObjectWrapper<@Nullable MessageKey>> locks = new EnumMap<>(ViewNames.class);

    /**
     * Starts following the open book and its brief.
     *
     * @param project the book whose presence and languages decide
     */
    @Inject
    StepAvailability(final CurrentProject project) {
        this.project = Objects.requireNonNull(project, "project");
        for (final ViewNames step : ViewNames.values()) {
            locks.put(step, new ReadOnlyObjectWrapper<>());
        }
        project.book().addListener((observed, old, current) -> refresh());
        project.brief().addListener((observed, old, current) -> refresh());
        refresh();
    }

    @Override
    public ReadOnlyObjectProperty<@Nullable MessageKey> lock(final ViewNames step) {
        Objects.requireNonNull(step, "step");
        return wrapperOf(step).getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<@Nullable MessageKey> wrapperOf(final ViewNames step) {
        return Objects.requireNonNull(locks.get(step), "every step has a lock property");
    }

    private void refresh() {
        final boolean hasBook = project.book().get() != null;
        final BookBrief brief = project.brief().get();
        final boolean languagesUsable =
                brief != null && BriefLanguages.isUsable(brief.sourceLanguage(), brief.targetLanguage());
        for (final ViewNames step : ViewNames.values()) {
            final MessageKey reason = reasonFor(step, hasBook, languagesUsable);
            final ReadOnlyObjectWrapper<@Nullable MessageKey> wrapper = wrapperOf(step);
            if (wrapper.get() != reason) {
                log.debug("step {} lock is now {}", step, reason);
                wrapper.set(reason);
            }
        }
    }

    private static @Nullable MessageKey reasonFor(
            final ViewNames step, final boolean hasBook, final boolean languagesUsable) {
        return switch (step) {
            case IMPORT, SETTINGS, PROJECTS -> null;
            case BOOK_BRIEF, EXPORT -> hasBook ? null : MessageKey.NAV_LOCKED_NO_BOOK;
            case STRUCTURE, NAMES_STYLE, TRANSLATING -> {
                if (!hasBook) {
                    yield MessageKey.NAV_LOCKED_NO_BOOK;
                }
                yield languagesUsable ? null : MessageKey.NAV_LOCKED_NO_LANGUAGES;
            }
        };
    }
}
