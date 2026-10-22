package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.state.RunState;

/**
 * Once translating has started the brief's languages are fixed (book-brief "Lock the languages once translating has
 * started"): both boxes are disabled with a hint that names the way to another language, and every other choice stays
 * editable while the run is paused.
 */
class BookBriefLanguageLockScreenTest extends BriefRunScreenTestBase {

    private static final String HINT =
            "To translate into another language, use Translate into another language on Projects";

    // IF the languages stayed editable after the first decision, THEN the stored translations would belong to a pair
    // the brief no longer names.
    @Test
    void languages_pausedWith74Decided_areDisabledWithTheHint() throws Exception {
        openAndShowBrief();

        publishRun(RunState.PAUSED, 74);

        assertThat(required("brief-source").isDisabled()).isTrue();
        assertThat(required("brief-target").isDisabled()).isTrue();
        assertThat(isShown("brief-languages-locked")).isTrue();
        assertThat(textOf("brief-languages-locked")).isEqualTo(HINT);
    }

    // IF the lock took the whole brief with it, THEN a paused run could not change its register or its names policy.
    @Test
    void otherChoices_pausedWith74Decided_stayEditable() throws Exception {
        openAndShowBrief();

        publishRun(RunState.PAUSED, 74);

        assertThat(List.of("brief-tone-register", "brief-policy-names", "brief-aux-nav", "brief-quality"))
                .allSatisfy(id -> assertThat(required(id).isDisabled()).as(id).isFalse());
    }

    // IF a new project showed the hint, THEN a person who has not translated anything would be told they cannot choose.
    @Test
    void languages_newProject_areEditableWithNoHint() throws Exception {
        openAndShowBrief();

        assertThat(required("brief-source").isDisabled()).isFalse();
        assertThat(required("brief-target").isDisabled()).isFalse();
        assertThat(isShown("brief-languages-locked")).isFalse();
    }

    @Test
    void languages_reopenedProjectWithDecidedSegments_areDisabledBeforeAnyRun() throws Exception {
        projects.willList(List.of(
                new ProjectSummary(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME).project(), 74, 100, null, null)));
        readyToStart();

        showBrief();

        assertThat(required("brief-target").isDisabled()).isTrue();
        assertThat(isShown("brief-languages-locked")).isTrue();
    }

    @Test
    void hint_underUkrainian_isWordedInUkrainian() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        openAndShowBrief();

        publishRun(RunState.PAUSED, 74);

        assertThat(textOf("brief-languages-locked"))
                .isEqualTo("Щоб перекласти іншою мовою, скористайтеся «Перекласти іншою мовою» на екрані «Проєкти»");
    }
}
