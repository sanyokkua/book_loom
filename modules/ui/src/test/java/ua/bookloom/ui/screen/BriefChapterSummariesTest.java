package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
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
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.RunState;

/**
 * The Book Brief's Chapter summaries switch and the story shape that suggests it (book-brief "Offer a per-book switch
 * for chapter summaries", "Show the story shape the analysis found, with the switch it suggests" and "Say how many
 * finished chapters will be summarised when the switch is turned on").
 */
class BriefChapterSummariesTest extends BriefSuggestTestBase {

    private static BriefSuggestion shaped(final StoryShape shape) {
        return new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(BriefField.STORY_SHAPE, new FieldEvidence(FieldEvidence.Basis.LIKELY, List.of(), 0, 2)),
                null,
                shape,
                List.of());
    }

    private void fillInWith(final StoryShape shape) throws TimeoutException {
        assistant.answersBrief(Result.ok(shaped(shape)));
        fillIn();
        awaitApplicable();
        onFx(() -> button("progress-apply").fire());
        awaitFx(() -> brief().storyShape() == shape);
    }

    private ToggleSwitch summaries() {
        return (ToggleSwitch) required("brief-summaries");
    }

    private void switchTo(final boolean on) {
        onFx(() -> summaries().setSelected(on));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean isSelected() {
        return read(() -> summaries().isSelected());
    }

    private List<Boolean> shapeSegments() {
        return read(() -> ((SegmentedButton) required("brief-tone-story-shape"))
                .getButtons().stream().map(ToggleButton::isSelected).toList());
    }

    // IF no analysis showed a shape, THEN the person would be shown a reading of the book nobody made.
    @Test
    void switch_noAnalysis_onWithNoShapeAndNoSuggestionLine() throws Exception {
        openAndShowBrief();

        assertThat(isSelected()).isTrue();
        assertThat(isShown("brief-tone-story-shape-field")).isFalse();
        assertThat(isShown("brief-summaries-suggested")).isFalse();
        assertThat(TooltipProbe.tipText(required("brief-summaries"))).contains("next Continue");
    }

    // IF separate stories still suggested summaries, THEN one tale's summary would mislead the next.
    @Test
    void switch_separateStories_suggestedOff() throws Exception {
        openAndShowBrief();

        fillInWith(StoryShape.SEPARATE_STORIES);
        awaitFx(() -> isShown("brief-summaries-suggested"));

        assertThat(isSelected()).isFalse();
        assertThat(shapeSegments()).containsExactly(false, true);
        assertThat(isShown("brief-likely-story-shape")).isTrue();
        assertThat(textOfLabel("brief-summaries-suggested")).isEqualTo("Suggested: off — separate stories (likely)");
    }

    @Test
    void switch_oneStory_suggestedOn() throws Exception {
        openAndShowBrief();

        fillInWith(StoryShape.ONE_STORY);
        awaitFx(() -> isShown("brief-summaries-suggested"));

        assertThat(isSelected()).isTrue();
        assertThat(textOfLabel("brief-summaries-suggested"))
                .isEqualTo("Suggested: on — the chapters tell one story (likely)");
    }

    // IF changing the shape kept the mark, THEN the person's own answer would still read as a guess.
    @Test
    void shape_personChangesIt_likelyMarkGoesAndTheSuggestionFollows() throws Exception {
        openAndShowBrief();
        fillInWith(StoryShape.ONE_STORY);

        onFx(() -> ((SegmentedButton) required("brief-tone-story-shape"))
                .getButtons()
                .get(1)
                .fire());
        awaitFx(() -> brief().storyShape() == StoryShape.SEPARATE_STORIES);

        assertThat(isShown("brief-likely-story-shape")).isFalse();
        assertThat(isSelected()).isFalse();
        assertThat(textOfLabel("brief-summaries-suggested")).isEqualTo("Suggested: off — separate stories");
    }

    // IF a later suggestion changed a forced switch, THEN the person's decision would be undone behind their back.
    @Test
    void switch_forcedOn_laterSuggestionKeepsIt() throws Exception {
        openAndShowBrief();
        switchTo(false);
        switchTo(true);

        fillInWith(StoryShape.SEPARATE_STORIES);

        assertThat(brief().chapterSummaries()).isTrue();
        assertThat(brief().storyShape()).isEqualTo(StoryShape.SEPARATE_STORIES);
        assertThat(isSelected()).isTrue();
    }

    @Test
    void switch_forcedOff_laterOneStoryKeepsItOff() throws Exception {
        openAndShowBrief();
        switchTo(false);

        fillInWith(StoryShape.ONE_STORY);

        assertThat(brief().chapterSummaries()).isFalse();
        assertThat(isSelected()).isFalse();
    }

    // IF the cost were hidden, THEN turning summaries on at chapter 13 would spend twelve calls unannounced.
    @Test
    void switch_turnedOnWithTwelveFinished_showsCount() throws Exception {
        projects.onChapterTally(new ChapterTally(20, 12, 12, 19));
        openAndShowBrief();
        switchTo(false);

        switchTo(true);
        awaitFx(() -> isShown("brief-summaries-pending"));

        assertThat(textOfLabel("brief-summaries-pending"))
                .isEqualTo("12 finished chapters will be summarised before the next passage.");

        switchTo(false);

        assertThat(isShown("brief-summaries-pending")).isFalse();
    }

    @Test
    void switch_turnedOnWithNothingFinished_saysNothing() throws Exception {
        openAndShowBrief();
        switchTo(false);

        switchTo(true);

        assertThat(isShown("brief-summaries-pending")).isFalse();
    }

    // IF the switch changed while a run was at work, THEN some chapters would be summarised and others not.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"RUNNING, true", "PAUSED, false"})
    void switch_translating_readOnly(final RunState state, final boolean disabled) throws Exception {
        openAndShowBrief();

        publishRun(state, 10);

        assertThat(read(() -> summaries().isDisabled())).isEqualTo(disabled);
    }

    @Test
    void switch_ukrainian_pendingLineReadsItsPlural() throws Exception {
        projects.onChapterTally(new ChapterTally(9, 3, 3, 8));
        useLocale(Locale.forLanguageTag("uk"));
        openAndShowBrief();
        switchTo(false);

        switchTo(true);
        awaitFx(() -> isShown("brief-summaries-pending"));

        assertThat(((Label) required("brief-summaries-pending")).getText())
                .isEqualTo("3 завершені розділи буде підсумовано перед наступним уривком.");
    }
}
