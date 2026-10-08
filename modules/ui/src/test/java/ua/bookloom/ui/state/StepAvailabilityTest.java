package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;

/** Which workflow steps are closed, and why, from the open book and the languages of its brief. */
class StepAvailabilityTest {

    private final CurrentProject project = new CurrentProject();
    private final StepAvailability availability = new StepAvailability(project);

    private void openBook(final String source, final @Nullable String target) {
        OpenBookForTest.open(project, source, target);
    }

    @ParameterizedTest
    @CsvSource({
        "BOOK_BRIEF",
        "STRUCTURE",
        "NAMES_STYLE",
        "TRANSLATING",
        "EXPORT",
    })
    void lockReason_noBook_lockedWithOpenABookFirst(final ViewNames step) {
        // IF a step opened with no book, THEN its screen would show a project that does not exist.
        assertThat(availability.lockReason(step)).contains(MessageKey.NAV_LOCKED_NO_BOOK);
    }

    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"IMPORT", "SETTINGS", "PROJECTS"})
    void lockReason_noBook_importSettingsAndInertStayOpen(final ViewNames step) {
        // IF Import were locked with no book, THEN there would be no way to open one; Settings needs no book.
        assertThat(availability.lockReason(step)).isEmpty();
    }

    @Test
    void lockReason_bookWithoutLanguages_locksTheLanguageDependentSteps() {
        openBook("en", null);

        assertThat(availability.lockReason(ViewNames.BOOK_BRIEF)).isEmpty();
        assertThat(availability.lockReason(ViewNames.STRUCTURE)).contains(MessageKey.NAV_LOCKED_NO_LANGUAGES);
        assertThat(availability.lockReason(ViewNames.NAMES_STYLE)).contains(MessageKey.NAV_LOCKED_NO_LANGUAGES);
        assertThat(availability.lockReason(ViewNames.TRANSLATING)).contains(MessageKey.NAV_LOCKED_NO_LANGUAGES);
        assertThat(availability.lockReason(ViewNames.EXPORT)).isEmpty();
    }

    @Test
    void lockReason_sameLanguages_locksTheLanguageDependentSteps() {
        // IF identical languages passed, THEN a run would "translate" a book into itself.
        openBook("en", "en");

        assertThat(availability.lockReason(ViewNames.TRANSLATING)).contains(MessageKey.NAV_LOCKED_NO_LANGUAGES);
    }

    @Test
    void lockReason_validLanguages_opensEverything() {
        openBook("en", "uk");

        final List<Optional<MessageKey>> reasons = new ArrayList<>();
        for (final ViewNames step : ViewNames.values()) {
            reasons.add(availability.lockReason(step));
        }
        assertThat(reasons).allMatch(Optional::isEmpty);
    }

    @Test
    void lock_languagesChosenAfterwards_updatesTheObservableReason() {
        // IF the reason were computed once, THEN choosing the languages would leave the entries locked.
        openBook("en", null);
        assertThat(availability.lock(ViewNames.TRANSLATING).get()).isEqualTo(MessageKey.NAV_LOCKED_NO_LANGUAGES);

        project.replaceBrief(project.brief().get().withLanguages("en", "uk"));

        assertThat(availability.lock(ViewNames.TRANSLATING).get()).isNull();
    }

    @Test
    void lock_bookClosed_locksAgain() {
        openBook("en", "uk");
        project.clear();

        assertThat(availability.lock(ViewNames.EXPORT).get()).isEqualTo(MessageKey.NAV_LOCKED_NO_BOOK);
    }
}
