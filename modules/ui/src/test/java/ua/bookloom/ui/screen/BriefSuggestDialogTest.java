package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;

/**
 * The fifth row of "What it decides", the story shape, with its agreement over the two windows it compares and their
 * quotes; and a value nothing supports, shown as likely with no agreement count (book-brief "Show what the suggestion
 * decides with its agreement and verified quote").
 */
class BriefSuggestDialogTest extends BriefSuggestTestBase {

    private static final int STORY_SHAPE_ROW = 4;

    private void fillInOneStory() throws Exception {
        openAndShowBrief();
        assistant.answersBrief(Result.ok(BriefFillFixtures.ONE_STORY_ANSWER));
        assistant.announces(BriefFillFixtures.threeOfFourCasual(false));
        fillIn();
        awaitApplicable();
    }

    // IF the story shape had no row, THEN the switch it suggests would change for a reason the person never saw.
    @Test
    void decides_fiveRowsWithStoryShapeAgreement() throws Exception {
        fillInOneStory();

        assertThat(decisions().getItems()).hasSize(5);
        assertThat(textOf("progress-decisions")).contains("Story shape\nOne story — 2 of 2 samples agree");

        selectDecision(STORY_SHAPE_ROW);

        assertThat(textOf("progress-sample-grid"))
                .contains("1 · ch1 · p02–p14", "3 · ch2 · p03–p12")
                .contains(BriefFillFixtures.QUOTE, BriefFillFixtures.LATER_QUOTE)
                .doesNotContain("ch1 · p15–p27");
    }

    // IF a value nothing supports showed a count, THEN "1 of 4" would read like a reading of the book.
    @Test
    void decides_unsupportedGenre_showsLikelyWithoutCount() throws Exception {
        fillInOneStory();

        assertThat(textOf("progress-decisions"))
                .contains("Genre\nfantasy (likely)")
                .doesNotContain("fantasy (likely) —")
                .doesNotContain("1 of 4");
    }
}
