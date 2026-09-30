package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;

/** The footer's placement, its ids and its unavailable forward action. */
@SuppressWarnings("NullAway.Init")
class StepFooterTest extends ApplicationTest {

    private static StepFooter.Action action(final String id, final String styleClass, final List<String> fired) {
        return new StepFooter.Action(id, "label " + id, "tip " + id, styleClass, () -> fired.add(id));
    }

    // IF back were not first and forward last, THEN the buttons would not sit bottom left and bottom right.
    @Test
    void of_backAndForward_backComesFirstAndForwardLastWithASpacerBetween() {
        final StepFooter footer = ThemeTestSupport.onFx(() -> StepFooter.of(
                action("a-back", "btn-ghost", new ArrayList<>()), action("a-next", "btn-primary", new ArrayList<>())));

        assertThat(footer.getChildren()).hasSize(3);
        assertThat(footer.getChildren().get(0).getId()).isEqualTo("a-back");
        assertThat(footer.getChildren().get(1)).isInstanceOf(Region.class);
        assertThat(footer.getChildren().get(2).getId()).isEqualTo("a-next");
        assertThat(footer.getChildren().get(0).getStyleClass()).contains("btn-ghost");
        assertThat(footer.getChildren().get(2).getStyleClass()).contains("btn-primary");
    }

    // IF the footer dropped the action's tip, THEN the step's two buttons would be the only silent ones.
    @Test
    void of_backAndForward_eachButtonCarriesTheTipOfItsAction() {
        final StepFooter footer = ThemeTestSupport.onFx(() -> StepFooter.of(
                action("c-back", "btn-ghost", new ArrayList<>()), action("c-next", "btn-primary", new ArrayList<>())));

        assertThat(TooltipProbe.tipText(footer.getChildren().get(0))).isEqualTo("tip c-back");
        assertThat(TooltipProbe.tipText(footer.forwardButton())).isEqualTo("tip c-next");
    }

    // IF an unavailable forward action were absent or pressable, THEN the last step would hide or fake its end.
    @Test
    void withUnavailableForward_forward_isPresentAndDisabled() {
        final StepFooter footer = ThemeTestSupport.onFx(() -> StepFooter.withUnavailableForward(
                action("e-back", "btn-ghost", new ArrayList<>()), action("e-next", "btn-primary", new ArrayList<>())));

        assertThat(footer.forwardButton().getId()).isEqualTo("e-next");
        assertThat(footer.forwardButton().isDisabled()).isTrue();
    }

    // IF the footer wrapped the action wrongly, THEN pressing a button would not reach the caller's effect.
    @Test
    void of_pressingEachButton_runsItsOwnAction() {
        final List<String> fired = new ArrayList<>();
        final StepFooter footer = ThemeTestSupport.onFx(
                () -> StepFooter.of(action("b-back", "btn-ghost", fired), action("b-next", "btn-primary", fired)));

        ThemeTestSupport.onFx(() -> {
            ((Button) footer.getChildren().get(0)).fire();
            footer.forwardButton().fire();
            return null;
        });

        assertThat(fired).containsExactly("b-back", "b-next");
    }

    // IF a step with nothing before it drew a back button, THEN Import would offer a Back to nowhere.
    @Test
    void of_noBack_holdsOnlyTheSpacerAndForward() {
        final StepFooter footer = ThemeTestSupport.onFx(
                () -> StepFooter.of(null, action("import-continue", "btn-primary", new ArrayList<>())));

        assertThat(footer.getChildren()).hasSize(2);
        assertThat(footer.forwardButton().getId()).isEqualTo("import-continue");
    }
}
