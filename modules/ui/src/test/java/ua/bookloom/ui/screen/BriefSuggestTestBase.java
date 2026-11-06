package ua.bookloom.ui.screen;

import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.ui.ScriptedSetupAssistant;

/**
 * What the tests of the fill-in dialog's "What it decides" group share: a scripted assistant that answers the brief
 * suggestion, pressing "Fill in from the book", waiting for the answer to be applicable, and the rows of the group.
 */
abstract class BriefSuggestTestBase extends BriefRunScreenTestBase {

    ScriptedSetupAssistant assistant;

    @BeforeEach
    void scriptAnAnswer() {
        assistant = (ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class);
        assistant.answersBrief(Result.ok(BriefFillFixtures.ANSWER));
    }

    /** Rebuilds the shell in Ukrainian, with a scripted assistant of the new graph that answers the same. */
    void useUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));
        scriptAnAnswer();
    }

    void fillIn() throws TimeoutException {
        onFx(() -> button("brief-suggest-style").fire());
        awaitFx(() -> optional("busy-card") != null && optional("busy-card").isVisible());
    }

    void awaitApplicable() throws TimeoutException {
        awaitFx(() ->
                optional("progress-apply") != null && !button("progress-apply").isDisabled());
    }

    @SuppressWarnings("unchecked")
    ListView<Object> decisions() {
        return (ListView<Object>) required("progress-decisions");
    }

    void selectDecision(final int index) {
        onFx(() -> decisions().getSelectionModel().select(index));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
