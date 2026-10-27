package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.project.BookBrief;

/**
 * Keeps the run notice that names the input a start is missing — the book, the target language or the model — in step
 * with what the person has supplied: set when a start is refused or the brief has no target language, withdrawn the
 * moment that input exists, so a ready start never reads as refused. FX thread only.
 */
@Slf4j
final class MissingInputNotice {

    private final ReadOnlyObjectWrapper<@Nullable RunNotice> notice;
    private final CurrentProject current;
    private final SettingsViewModel settings;

    MissingInputNotice(
            final ReadOnlyObjectWrapper<@Nullable RunNotice> notice,
            final CurrentProject current,
            final SettingsViewModel settings) {
        this.notice = Objects.requireNonNull(notice, "notice");
        this.current = Objects.requireNonNull(current, "current");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Refuses a start that lacks an input.
     *
     * @return the chosen model when nothing is missing, or empty after publishing the notice that names the first
     *     missing input
     */
    Optional<ModelSelection> refuseIfMissing() {
        final Optional<ModelSelection> selection = settings.selection();
        final Optional<RunNotice.Input> missing =
                RunNotice.Input.firstMissing(current.brief().get(), selection.isPresent());
        if (missing.isPresent()) {
            log.debug("run refused: {} is missing", missing.get());
            notice.set(new RunNotice.MissingInput(missing.get()));
            return Optional.empty();
        }
        notice.set(null);
        return selection;
    }

    void modelTextChanged() {
        clearIfSupplied(RunNotice.Input.MODEL, () -> settings.selection().isPresent());
    }

    void bookChanged(final @Nullable OpenedBook book) {
        clearIfSupplied(RunNotice.Input.BOOK, () -> book != null);
    }

    void briefChanged(final @Nullable BookBrief brief) {
        if (brief != null && brief.targetLanguage() == null && notice.get() == null) {
            log.debug("the brief has no target language: Start is refused at once, until one is chosen");
            notice.set(new RunNotice.MissingInput(RunNotice.Input.TARGET_LANGUAGE));
            return;
        }
        clearIfSupplied(RunNotice.Input.TARGET_LANGUAGE, () -> brief != null && brief.targetLanguage() != null);
    }

    // Withdraws a refusal that named the input once that input exists.
    private void clearIfSupplied(final RunNotice.Input input, final BooleanSupplier supplied) {
        if (notice.get() instanceof RunNotice.MissingInput missing
                && missing.which() == input
                && supplied.getAsBoolean()) {
            log.debug("{} is now supplied: withdrawing the missing-input notice", input);
            notice.set(null);
        }
    }
}
