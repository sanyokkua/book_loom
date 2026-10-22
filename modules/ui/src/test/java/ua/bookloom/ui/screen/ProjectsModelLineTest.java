package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.RecordingModelCatalog;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.RecordingModelPickPrompt;
import ua.bookloom.ui.state.LocalModelProbe;

/**
 * The AI-model line of the first-launch card as the first-launch look and "Try again" drive it: looking, found (with and
 * without the first-launch words), not found with its help line, and the two buttons. The probe is the real one over a
 * recording catalogue.
 */
class ProjectsModelLineTest extends ProjectsScreenTestBase {

    private static final String OLLAMA = "http://127.0.0.1:11434";
    private static final String CARD = "projects-empty-card";

    private final RecordingModelCatalog catalog = RecordingModelCatalog.nothingAnswering();
    private final RecordingModelPickPrompt pick = new RecordingModelPickPrompt();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .projectActions(actions)
                .revealer(revealer)
                .engine(engine)
                .catalog(catalog)
                .modelPickPrompt(pick)
                .gated()
                .build();
    }

    private LocalModelProbe probe() {
        return injector.getInstance(LocalModelProbe.class);
    }

    private void awaitCardContains(final String text) throws TimeoutException {
        ImportScreenTestBase.awaitFx(
                () -> String.join(" ", textsUnder(node(CARD))).contains(text));
    }

    private void press(final String id) {
        onFx(() -> ((Button) node(id)).fire());
    }

    // IF the card said nothing while the programs were asked, THEN a slow first launch would look like a failure.
    @Test
    void emptyCard_lookInFlight_saysLookingAndHoldsTryAgainDisabled() throws Exception {
        showEmptyListing();
        catalog.holdDrafts();

        onFx(() -> probe().runFirstLaunch());

        awaitCardContains("Looking for an AI program on this computer…");
        assertThat(((Button) node("projects-empty-tryagain")).isDisabled()).isTrue();
        catalog.releaseDrafts();
        awaitCardContains("No AI model found");
    }

    // IF the first-launch words stayed for ever, THEN they would be false after the person changed the model.
    @Test
    void emptyCard_foundByTheFirstLaunchLook_saysFoundOnThisComputer() throws Exception {
        catalog.answering(OLLAMA, "gemma4:e4b");
        showEmptyListing();

        onFx(() -> probe().runFirstLaunch());

        awaitCardContains(
                "AI model: Ollama · gemma4:e4b — ready ✓ (found on this computer when BookLoom first opened)");
        assertThat(textOf(CARD)).contains("Change…").doesNotContain("I use another program…");
    }

    @Test
    void emptyCard_foundByTryAgain_saysReadyWithoutTheFirstLaunchWords() throws Exception {
        showEmptyListing();
        onFx(() -> probe().runFirstLaunch());
        awaitCardContains("No AI model found");
        catalog.answering(OLLAMA, "gemma4:e4b");

        press("projects-empty-tryagain");

        awaitCardContains("AI model: Ollama · gemma4:e4b — ready ✓");
        assertThat(textOf(CARD)).doesNotContain("found on this computer");
    }

    @Test
    void emptyCard_nothingFound_showsNoModelTheHelpLineAndBothButtonsWithoutAnError() throws Exception {
        showEmptyListing();

        onFx(() -> probe().runFirstLaunch());

        awaitCardContains("No AI model found");
        assertThat(textOf(CARD))
                .contains("Ollama is not open. Open the Ollama app, then press Try again.")
                .doesNotContain("ready ✓");
        assertThat(((Button) node("projects-empty-tryagain")).getText()).isEqualTo("Try again");
        assertThat(((Button) node("projects-empty-tryagain")).isDisabled()).isFalse();
        assertThat(((Button) node("projects-empty-other")).getText()).isEqualTo("I use another program…");
    }

    // IF a card with no look this session offered no Try again, THEN a person whose program was closed at launch would
    // have to restart the app.
    @Test
    void emptyCard_noModelAndNoLookThisSession_stillOffersTryAgain() throws Exception {
        showEmptyListing();

        assertThat(textOf(CARD)).contains("No AI model found");
        assertThat(isPresent("projects-empty-tryagain")).isTrue();
        assertThat(catalog.drafts()).isEmpty();
    }

    // IF Try again asked a different set of addresses than the first look, THEN it would reach where the first look may
    // not.
    @Test
    void tryAgain_pressed_asksTheTwoAddressesOnceMore() throws Exception {
        showEmptyListing();
        onFx(() -> probe().runFirstLaunch());
        awaitCardContains("No AI model found");

        press("projects-empty-tryagain");
        ImportScreenTestBase.awaitFx(() -> catalog.drafts().size() == 4);

        assertThat(catalog.draftAddresses())
                .containsExactly(OLLAMA, "http://127.0.0.1:1234/v1", OLLAMA, "http://127.0.0.1:1234/v1");
    }

    @Test
    void otherProgram_pressed_opensSettings() throws Exception {
        showEmptyListing();
        onFx(() -> probe().runFirstLaunch());
        awaitCardContains("No AI model found");

        press("projects-empty-other");

        assertThat(ua.bookloom.ui.ThemeTestSupport.onFx(
                        () -> navigator.currentView().get()))
                .isEqualTo(ViewNames.SETTINGS);
    }

    // IF dismissing the question left the card on "Looking…", THEN the person could neither retry nor go on.
    @Test
    void emptyCard_severalModelsAndNotNow_showsTheNotFoundLineAndTryAgainAsksAgain() throws Exception {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        showEmptyListing();
        onFx(() -> probe().runFirstLaunch());
        ImportScreenTestBase.awaitFx(() -> !pick.asked().isEmpty());

        onFx(pick::notNow);

        awaitCardContains("No AI model found");
        press("projects-empty-tryagain");
        ImportScreenTestBase.awaitFx(() -> pick.asked().size() == 2);
        assertThat(pick.asked().get(1).modelIds()).containsExactly("gemma4:e4b", "qwen3:8b");
    }

    @Test
    void emptyCard_severalModelsAndOnePicked_saysReadyForThePickedModel() throws Exception {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        showEmptyListing();
        onFx(() -> probe().runFirstLaunch());
        ImportScreenTestBase.awaitFx(() -> !pick.asked().isEmpty());

        onFx(() -> pick.use("qwen3:8b"));

        awaitCardContains("AI model: Ollama · qwen3:8b — ready ✓");
        assertThat(textOf(CARD)).doesNotContain("gemma4:e4b");
    }

    @Test
    void emptyCard_underUkrainian_showsTheUkrainianTryAgainAndHelp() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        showEmptyListing();

        assertThat(((Button) node("projects-empty-tryagain")).getText()).isEqualTo("Спробувати ще раз");
    }
}
