package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.project.NameOrigin;

/** The Chapter summaries switch on the Translating ready card is the Book Brief's own switch, before Start. */
class ReadyCardTest extends BriefRunScreenTestBase {

    private ToggleSwitch toggle(final String id) {
        return (ToggleSwitch) required(id);
    }

    // IF the ready card kept a switch of its own, THEN Start would run with a setting the Brief does not show.
    @Test
    void switch_sameAsBrief() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME)));
        readyToStart();
        showTranslating();
        assertThat(read(() -> toggle("translating-ready-summaries").isSelected()))
                .isTrue();

        onFx(() -> toggle("translating-ready-summaries").setSelected(false));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(brief().summariesOn()).isFalse();
        assertThat(brief().chapterSummaries()).isFalse();
        showBrief();
        assertThat(read(() -> toggle("brief-summaries").isSelected())).isFalse();
    }
}
