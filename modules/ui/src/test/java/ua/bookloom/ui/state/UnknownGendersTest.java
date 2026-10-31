package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Origin;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedGlossaryService;

/** What Start asks about, in the order the service gives it, and how the answers are stored. */
class UnknownGendersTest extends FxTestBase {

    private final ScriptedGlossaryService glossary = new ScriptedGlossaryService();
    private final UnknownGenders unknown = new UnknownGenders(glossary, new DirectExecutor());

    @Override
    public void start(final Stage stage) {
        // The toolkit is all this test needs.
    }

    @Test
    void find_characters_arriveInTheOrderGivenWithTheirOfferedValues() {
        final UnknownGender ivar = new UnknownGender("Ivar", 12, true, "e1", Gender.MALE, Origin.LIKELY, 0.55, null);
        final UnknownGender mira =
                new UnknownGender("Mira Holt", 30, true, "e2", Gender.FEMALE, Origin.LIKELY, 0.9, null);
        glossary.willListUnknownGenders(List.of(ivar, mira));
        final List<UnknownGender> seen = new java.util.concurrent.CopyOnWriteArrayList<>();

        onFx(() -> {
            unknown.find("p1", seen::addAll);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(seen).containsExactly(ivar, mira);
        assertThat(glossary.unknownGenderCalls()).containsExactly("p1:5");
    }

    @Test
    void answer_tickedRows_goToAnswerGendersAndThenRunTheContinuation() {
        final AtomicInteger continued = new AtomicInteger();

        onFx(() -> {
            unknown.answer("p1", Map.of("e1", Gender.FEMALE), continued::incrementAndGet);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(glossary.genderAnswers()).containsExactly(Map.of("e1", Gender.FEMALE));
        assertThat(continued).hasValue(1);
    }
}
