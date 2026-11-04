package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.context.FrameFixtures.DIALOGUE;
import static ua.bookloom.pipeline.context.FrameFixtures.MARKED;
import static ua.bookloom.pipeline.context.FrameFixtures.NARRATION;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;

class ReminderTest {

    private static final RunFrame FRAME = FrameFixtures.frame(FrameFixtures.book(NARRATION, DIALOGUE, MARKED));

    @Test
    void reminder_noMarkNoDialogue_empty() {
        assertThat(Reminder.of(List.of(new FocusItem("ch1:0", NARRATION)), FRAME))
                .isEmpty();
    }

    @Test
    void reminder_markAndDialogue_twoLinesMarkFirst() {
        final List<FocusItem> items = List.of(new FocusItem("ch1:1", DIALOGUE), new FocusItem("ch1:2", MARKED));

        assertThat(Reminder.of(items, FRAME))
                .containsExactly("Keep every ⟦…⟧ mark exactly once.", "Dialogue lines start with «— ».");
    }

    @Test
    void reminder_firstPersonNarrationStatedGender_namesTheNarrator() {
        final String paragraph = "Я долго стоял у окна и смотрел на дорогу.";
        final BookBrief brief =
                FrameFixtures.brief("ru", "uk").withNarrator(new Narrator(NarratorPerson.FIRST, Gender.MALE));
        final RunFrame frame = FrameFixtures.frame(FrameFixtures.book(paragraph), brief, ModelProfile.defaults());

        assertThat(Reminder.of(List.of(new FocusItem("ch1:0", paragraph)), frame))
                .singleElement()
                .asString()
                .contains("male");
    }
}
