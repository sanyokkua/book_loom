package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;

class WindowInjectionGraderTest {

    private static final List<MiniGold.Entry> GOLD = List.of(
            new MiniGold.Entry("Ilsabet Corwen", List.of("Ilsabet Corwen", "Ilsabet"), "Ілсабет Корвен", Gender.FEMALE),
            new MiniGold.Entry("Dorrin Vask", List.of("Dorrin Vask", "Dorrin"), "Дорін Васк", Gender.MALE));

    private static CaseRecord.Exchange nextItemCall(final String nextBody) {
        return new CaseRecord.Exchange(
                "[system] Translate.\n[user] [Glossary — apply these renderings exactly]\n"
                        + "Dorrin Vask → Дорін Васк (character, male)\n\n"
                        + "[Next item after the batch — context only; read it, do NOT translate it]\n"
                        + nextBody + "\n\n<Items>\n<s id=\"1\">Ilsabet locked the door.</s>\n</Items>\n\nReminder.",
                "{}");
    }

    @Test
    void windowInjection_nextItemBodyStartsWithNumberedTag_nameInItIsInTheWindow() {
        final MiniScorer.Verdict verdict = MiniGraders.windowInjection(
                List.of(nextItemCall("[8] Dorrin disputes the ledger.")),
                List.of("Ilsabet locked the door.", "[8] Dorrin disputes the ledger."),
                GOLD);

        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.detail()).contains("mapChecked=1 mapOutside=0");
    }

    @Test
    void windowInjection_nextItemNamesOtherPerson_nameStillOutsideTheWindow() {
        final MiniScorer.Verdict verdict = MiniGraders.windowInjection(
                List.of(nextItemCall("[8] Ilsabet disputes the ledger.")),
                List.of("Ilsabet locked the door.", "[8] Ilsabet disputes the ledger."),
                GOLD);

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("mapOutside=1").contains("Dorrin Vask");
    }
}
