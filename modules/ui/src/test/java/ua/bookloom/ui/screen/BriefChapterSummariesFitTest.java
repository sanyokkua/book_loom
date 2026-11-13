package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.ChapterTally;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.StoryShape;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The story shape, the Chapter summaries switch with both its lines, the fill-in dialog's five rows and the ready
 * card's switch keep every Ukrainian word whole from the narrowest window up: the lines wrap, the switch and the
 * shape's options never shrink below their words.
 */
class BriefChapterSummariesFitTest extends BriefSuggestTestBase {

    private static final BriefSuggestion SEPARATE = new BriefSuggestion(
            null,
            Register.NEUTRAL,
            null,
            null,
            NarratorPerson.UNSPECIFIED,
            Gender.UNKNOWN,
            Map.of(BriefField.STORY_SHAPE, new FieldEvidence(FieldEvidence.Basis.LIKELY, List.of(), 0, 2)),
            null,
            StoryShape.SEPARATE_STORIES,
            List.of());

    private void assertWhole(final String id, final double width) {
        final Node card = required(id);
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(card))).isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.squeezed(card))).isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.overflowing(card, width)))
                .isEmpty();
    }

    // IF the suggestion and the count were cut, THEN the person would not see why the switch is off or what it costs.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1280, 800", "1920, 700"})
    void toneCard_ukrainianShapeAndBothLines_fitWhole(final double width, final double height) throws Exception {
        projects.onChapterTally(new ChapterTally(30, 21, 21, 29));
        useUkrainian();
        openAndShowBrief();
        assistant.answersBrief(Result.ok(SEPARATE));
        fillIn();
        awaitApplicable();
        onFx(() -> button("progress-apply").fire());
        awaitFx(() -> isShown("brief-summaries-suggested"));
        onFx(() -> ((ToggleSwitch) required("brief-summaries")).setSelected(true));
        awaitFx(() -> isShown("brief-summaries-pending"));

        showAtSceneSize(width, height);
        WaitForAsyncUtils.waitForFxEvents();

        assertWhole("brief-tone-card", width);
    }

    // IF a Ukrainian row of "What it decides" ran out of room, THEN the story shape's agreement would end in "…".
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void fillDialog_ukrainianFiveRows_fitWhole(final double width, final double height) throws Exception {
        useUkrainian();
        openAndShowBrief();
        assistant.answersBrief(Result.ok(BriefFillFixtures.ONE_STORY_ANSWER));
        assistant.announces(BriefFillFixtures.threeOfFourCasual(false));
        showAtSceneSize(width, height);
        fillIn();
        awaitApplicable();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(decisions().getItems()).hasSize(5);
        assertWhole("busy-card", width);
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void readyCard_ukrainianSwitchRow_fitsWhole(final double width, final double height) throws Exception {
        useUkrainian();
        openAndShowBrief();
        showTranslating();
        showAtSceneSize(width, height);
        WaitForAsyncUtils.waitForFxEvents();

        assertWhole("translating-ready-card", width);
    }
}
