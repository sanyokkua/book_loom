package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The destination beside the Languages card as the real scene shows it: the proposal, a path typed by hand, the file
 * chooser's button, the overwrite choice, the report that a file is already there, and Continue's dependence on a
 * usable path. "Wired" is asserted by driving the view model and reading the node, and by changing the node and
 * reading the view model. The native file chooser has no headless implementation, so only its button is checked.
 */
class BookBriefScreenDestinationTest extends BookBriefScreenTestBase {

    @TempDir
    private Path dir;

    private void openFrankenstein() throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private TextField destinationField() {
        return (TextField) required("brief-destination");
    }

    // IF the field did not show the proposal, THEN a person would not know where the translation would be written.
    @Test
    void destination_bookOpened_showsTheProposalBesideTheSource() throws TimeoutException {
        openFrankenstein();

        assertThat(destinationField().getText())
                .isEqualTo(dir.resolve("Frankenstein.uk.epub").toString());
    }

    // IF the field did not follow a target change, THEN the proposal would name the wrong language.
    @Test
    void destination_targetChangedTwiceInTheViewModel_followsBothChanges() throws TimeoutException {
        openFrankenstein();

        onFx(() -> briefModel().selectTarget("de"));
        assertThat(destinationField().getText())
                .isEqualTo(dir.resolve("Frankenstein.de.epub").toString());
        onFx(() -> briefModel().selectTarget("pl"));

        assertThat(destinationField().getText())
                .isEqualTo(dir.resolve("Frankenstein.pl.epub").toString());
    }

    // IF a proposal written into the field counted as typing, THEN the second target change would be ignored.
    @Test
    void destination_targetChangedInThePickerTwice_followsBothChanges() throws TimeoutException {
        openFrankenstein();

        onFx(() -> comboSelect("de"));
        onFx(() -> comboSelect("en"));

        assertThat(destinationField().getText())
                .isEqualTo(dir.resolve("Frankenstein.en.epub").toString());
    }

    // IF typing did not reach the view model, THEN the run would write to the proposal and not to the typed path.
    @Test
    void destination_typedIntoTheField_reachesTheViewModelAsAHandEdit() throws TimeoutException {
        openFrankenstein();

        onFx(() -> destinationField().setText("/archive/out.epub"));
        onFx(() -> comboSelect("de"));

        assertThat(shownDestination()).isEqualTo("/archive/out.epub");
        assertThat(destinationField().getText()).isEqualTo("/archive/out.epub");
    }

    // IF a path edited through the view model were not shown, THEN the field would describe a different destination.
    @Test
    void destination_editedInTheViewModel_isShownInTheFieldAndSurvivesATargetChange() throws TimeoutException {
        openFrankenstein();

        onFx(() -> briefModel().editDestination("/archive/out.epub"));
        onFx(() -> briefModel().selectTarget("de"));

        assertThat(destinationField().getText()).isEqualTo("/archive/out.epub");
    }

    // IF the file chooser's button were missing or disabled, THEN a person could only type the path.
    @Test
    void browse_bookOpened_isAnEnabledButton() throws TimeoutException {
        openFrankenstein();

        assertThat(button("brief-destination-browse").isDisabled()).isFalse();
    }

    // IF replacing were on by default, THEN a run could destroy a file nobody agreed to lose.
    @Test
    void overwrite_bookOpened_isASwitchAndNotSelected() throws TimeoutException {
        openFrankenstein();

        assertThat(toggle("brief-overwrite").isSelected()).isFalse();
        assertThat(toggle("brief-overwrite").isDisabled()).isFalse();
    }

    // IF the switch did not reach the view model, THEN the request would refuse or replace against the person's wish.
    @Test
    void overwrite_switchedOn_reachesTheViewModel() throws TimeoutException {
        openFrankenstein();

        onFx(() -> toggle("brief-overwrite").setSelected(true));

        assertThat(overwriteChosen()).isTrue();
    }

    // IF the report showed with a free path, THEN it would warn about a problem that does not exist.
    @Test
    void existsReport_freeDestination_isNotShown() throws TimeoutException {
        openFrankenstein();

        assertThat(isShown("brief-destination-exists")).isFalse();
    }

    // IF an occupied path were not reported until the run was refused, THEN the refusal would arrive two screens late.
    @Test
    void existsReport_existingFileAtTheDestinationAndOverwriteOff_isShownWithNoRunStarted() throws Exception {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        openFrankenstein();

        onFx(() -> briefModel().editDestination(occupied.toString()));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("brief-destination-exists")).isTrue();
        assertThat(port.openedPaths()).containsExactly(dir.resolve("Frankenstein.epub"));
    }

    // IF the report were shown for a proposal that already exists, THEN only a typed path would ever be warned about.
    @Test
    void existsReport_theProposedDestinationExists_isShown() throws IOException, TimeoutException {
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");

        openFrankenstein();

        assertThat(isShown("brief-destination-exists")).isTrue();
    }

    // IF allowing the replacement left the report up, THEN a person would be warned about something they permitted.
    @Test
    void existsReport_overwriteSwitchedOn_isHidden() throws Exception {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        openFrankenstein();
        onFx(() -> briefModel().editDestination(occupied.toString()));

        onFx(() -> toggle("brief-overwrite").setSelected(true));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("brief-destination-exists")).isFalse();
    }

    // IF switching overwrite off again did not bring the report back, THEN a stale all-clear would be shown.
    @Test
    void existsReport_overwriteSwitchedOnThenOff_isShownAgain() throws Exception {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        openFrankenstein();
        onFx(() -> briefModel().editDestination(occupied.toString()));
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> toggle("brief-overwrite").setSelected(true));
        WaitForAsyncUtils.waitForFxEvents();

        onFx(() -> toggle("brief-overwrite").setSelected(false));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("brief-destination-exists")).isTrue();
    }

    // IF the report were recomputed only on a change, THEN a file created after the brief was first shown would go
    // unreported when the person comes back to it.
    @Test
    void existsReport_fileAppearedBetweenVisits_isShownWhenTheBriefIsShownAgain() throws IOException, TimeoutException {
        openFrankenstein();
        assertThat(isShown("brief-destination-exists")).isFalse();
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "appeared later");

        showBrief();
        awaitFx(() -> isShown("brief-destination-exists"));

        assertThat(isShown("brief-destination-exists")).isTrue();
    }

    // IF permission to replace survived a different book, THEN the switch would show on for a file nobody agreed to
    // lose.
    @Test
    void overwrite_differentBookOpenedAfterReplaceAllowed_isShownOffAndTheWarningReturns()
            throws IOException, TimeoutException {
        Files.writeString(dir.resolve("Dracula.uk.epub"), "already here");
        openFrankenstein();
        onFx(() -> toggle("brief-overwrite").setSelected(true));
        final Path second = dir.resolve("Dracula.epub");
        port.on(second, Result.ok(BookFixtures.book("dracula", BookFormat.EPUB, "en", null, null, 1)));
        openImport();
        openBook(second);

        showBrief();
        awaitFx(() -> isShown("brief-destination-exists"));

        assertThat(toggle("brief-overwrite").isSelected()).isFalse();
        assertThat(isShown("brief-destination-exists")).isTrue();
    }

    // IF Continue stayed enabled with no destination, THEN a run could start with nowhere to write.
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void continueControl_blankDestination_isDisabled(final String blank) throws TimeoutException {
        openFrankenstein();

        onFx(() -> briefModel().editDestination(blank));

        assertThat(button("brief-continue").isDisabled()).isTrue();
    }

    // IF Continue were disabled with a usable path, THEN a person could never leave the brief.
    @Test
    void continueControl_destinationProposed_isEnabled() throws TimeoutException {
        openFrankenstein();

        assertThat(button("brief-continue").isDisabled()).isFalse();
    }

    // IF Continue stayed disabled after a blank was replaced, THEN one slip would strand the person on the screen.
    @Test
    void continueControl_blankDestinationThenAPath_isEnabledAgain() throws TimeoutException {
        openFrankenstein();
        onFx(() -> briefModel().editDestination(""));

        onFx(() -> briefModel().editDestination("/archive/out.epub"));

        assertThat(button("brief-continue").isDisabled()).isFalse();
    }

    private void comboSelect(final String code) {
        targetPicker().getSelectionModel().select(code);
    }

    private boolean overwriteChosen() {
        return ThemeTestSupport.onFx(() -> briefModel().overwrite().get());
    }
}
