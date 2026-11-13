package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectService;

/** After a Brief key change the screen says how many decided segments now need a look. */
class BriefRecheckBannerTest extends BriefRunScreenTestBase {

    private void saveAnsweredWith(final int rechecked) throws Exception {
        projects.onUpdateBrief(Result.ok(new ProjectChange.Saved(
                ScriptedProjectService.storedProjectOf(ProjectFixtures.source("Frankenstein.epub")), rechecked)));
        onFx(() -> briefViewModel().setGenre("Gothic novel"));
        ProjectFixtures.awaitBackgroundIdle(injector);
    }

    @Test
    void banner_storedCountTwelve_showsItInEnglish() throws Exception {
        openAndShowBrief();

        saveAnsweredWith(12);

        awaitFx(() -> isShown("brief-recheck-banner"));
        assertThat(textOf("brief-recheck-banner")).contains("12 segments need a look after this change.");
    }

    @Test
    void banner_storedCountZero_isNotShown() throws Exception {
        openAndShowBrief();

        saveAnsweredWith(0);

        assertThat(isShown("brief-recheck-banner")).isFalse();
    }

    @ParameterizedTest(name = "{0} in Ukrainian")
    @CsvSource({
        "1, Після цієї зміни 1 сегмент потребує перегляду.",
        "3, Після цієї зміни 3 сегменти потребують перегляду.",
        "12, Після цієї зміни 12 сегментів потребують перегляду.",
    })
    void banner_ukrainianCounts_useTheirPluralForm(final int count, final String sentence) throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        openAndShowBrief();

        saveAnsweredWith(count);

        awaitFx(() -> isShown("brief-recheck-banner"));
        assertThat(textOf("brief-recheck-banner")).contains(sentence);
    }
}
