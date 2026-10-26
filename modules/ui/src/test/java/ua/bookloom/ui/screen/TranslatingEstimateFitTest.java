package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The estimate the Translating strip shows before Start is the longest line it holds in Ukrainian; at the smallest
 * window it breaks onto a second line instead of ending in dots that hide where the speed was measured.
 */
class TranslatingEstimateFitTest extends BriefRunScreenTestBase {

    @Test
    void bigLine_ukrainianEstimateAtTheSmallestWindow_isShownWhole() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        modelSettings.measured("ollama", MODEL, 3.0);
        projects.willList(List.of(
                new ProjectSummary(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME).project(), 0, 100, null, null)));
        readyToStart();
        showTranslating();

        assertThat(labelText("translating-strip-big")).startsWith("Для цієї книжки на цьому комп’ютері");
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(required("translating-strip"))))
                .isEmpty();
    }
}
