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

/** Which workflow steps are closed, and why, from the open project and the languages of its brief. */
class StepAvailabilityTest {

    private final CurrentProject project = new CurrentProject();
    private final StepAvailability availability = new StepAvailability(project);

    private void openBook(final @Nullable String source, final @Nullable String target) {
        OpenBookForTest.open(project, source, target);
    }

    @ParameterizedTest
    @CsvSource({
        "IMPORT",
        "BOOK_BRIEF",
        "NAMES_STYLE",
        "TRANSLATING",
        "EXPORT",
    })
    void lockReason_noProject_everyWorkflowStepIsLockedWithStepsUnlockWhenAProjectIsOpen(final ViewNames step) {
        // IF a step opened with no project, THEN its screen would show a project that does not exist; Import is
        // locked too, because a new translation reaches it through the toolbar, not through the step.
        assertThat(availability.lockReason(step)).contains(MessageKey.NAV_LOCKED_NO_PROJECT);
    }

    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"SETTINGS", "PROJECTS"})
    void lockReason_noProject_projectsAndSettingsStayOpen(final ViewNames step) {
        // IF Projects were locked with no project, THEN there would be no way to open one; Settings needs none.
        assertThat(availability.lockReason(step)).isEmpty();
    }

    @Test
    void lockReason_bookWithoutLanguages_locksTheLanguageDependentSteps() {
        openBook("en", null);

        assertThat(availability.lockReason(ViewNames.BOOK_BRIEF)).isEmpty();
        assertThat(availability.lockReason(ViewNames.NAMES_STYLE)).contains(MessageKey.NAV_LOCKED_NO_LANGUAGES);
        assertThat(availability.lockReason(ViewNames.TRANSLATING)).contains(MessageKey.NAV_LOCKED_NO_LANGUAGES);
        assertThat(availability.lockReason(ViewNames.EXPORT)).isEmpty();
    }

    // IF Auto kept the steps locked, THEN a book declaring no language could never reach Translating.
    @Test
    void lockReason_autoSourceWithATarget_opensEverything() {
        openBook(null, "uk");

        final List<Optional<MessageKey>> reasons = new ArrayList<>();
        for (final ViewNames step : ViewNames.values()) {
            reasons.add(availability.lockReason(step));
        }
        assertThat(reasons).allMatch(Optional::isEmpty);
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

        assertThat(availability.lock(ViewNames.EXPORT).get()).isEqualTo(MessageKey.NAV_LOCKED_NO_PROJECT);
    }
}
