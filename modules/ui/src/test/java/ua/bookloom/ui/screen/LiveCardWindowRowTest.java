package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ProofreadItem;
import ua.bookloom.api.pipeline.ProofreadWindowRead;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LiveRow;

/**
 * A read proofread window is a row of the live card, {@code window ch7 · p40–p42}, whose detail shows each segment it
 * read with its text before and after and the verdict with its reason in words.
 */
class LiveCardWindowRowTest extends LiveCardTestBase {

    private static final ProofreadItem KEPT = new ProofreadItem(
            "ch7.xhtml:40",
            "ch7 · p40",
            "Ліна сказав, що прийде.",
            "Ліна сказала, що прийде.",
            ProofreadOutcome.KEPT,
            "",
            List.of("gender ending against: Ліна — female"));
    private static final ProofreadItem REJECTED = new ProofreadItem(
            "ch7.xhtml:42",
            "ch7 · p42",
            "Він знав, що вона прийде.",
            "Він знав, что вона прийде.",
            ProofreadOutcome.REJECTED,
            "function-word-residue: что",
            List.of());

    private static LiveRow window(final ProofreadItem... items) {
        return LiveRow.proofreadWindow(new ProofreadWindowRead(1, List.of(items)));
    }

    private List<String> detailTexts() {
        return ThemeTestSupport.onFx(() -> required("translating-live-detail-window").lookupAll(".label").stream()
                .map(node -> ((Label) node).getText())
                .toList());
    }

    // IF a window's long text squeezed its locator away, THEN the row would not say which paragraphs the window read.
    // The text gives way first and the locator stays whole.
    @Test
    void windowRow_longTextAfter_locatorStaysWhole() {
        resizeScene(960, 640);
        final String longText =
                "Редакційна команда · Опубліковано 2026-10-28 · Час читання: близько 12 хвилин. ".repeat(4);
        showRows(List.of(window(new ProofreadItem(
                "ch1.md:3", "ch1 · p03", "Редакторська команда", longText, ProofreadOutcome.KEPT, "", List.of()))));
        onFx(() -> {});

        assertThat(locators()).containsExactly("window ch1 · p03");
        final double[] widthAndNeed = ThemeTestSupport.onFx(() -> {
            final Label locator = (Label) cells().getFirst().lookup(".live-row-locator");
            return new double[] {locator.getWidth(), locator.prefWidth(-1)};
        });
        assertThat(widthAndNeed[0]).isGreaterThanOrEqualTo(widthAndNeed[1] - 0.5);
    }

    // IF a kept rewrite showed no text before and after, THEN the person could not see what the last pass changed.
    @Test
    void keptRow_detailShowsBeforeAfterAndVerdict() {
        showRows(List.of(window(KEPT)));
        pick(0);

        assertThat(locators()).containsExactly("window ch7 · p40");
        assertThat(isShown("translating-live-detail-window")).isTrue();
        assertThat(isShown("translating-live-detail-texts")).isFalse();
        assertThat(detailTexts())
                .contains(
                        "ch7 · p40 · kept: gender ending fixed", "Ліна сказав, що прийде.", "Ліна сказала, що прийде.");
    }

    // IF a refused rewrite gave no reason, THEN the person could not tell a bad edit from a lost one.
    @Test
    void rejectedRow_namesItsReasonInWords() {
        showRows(List.of(window(KEPT, REJECTED)));
        pick(0);

        assertThat(locators()).containsExactly("window ch7 · p40–p42");
        assertThat(detailTexts()).contains("ch7 · p42 · rejected: added an untranslated word «что»");
    }

    // IF the Ukrainian verdicts were cut at the smallest window, THEN the reason would be read half.
    @Test
    void windowRow_ukrainianAtTheSmallestWindow_detailShowsEveryLine() {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(960, 640);
        showRows(List.of(window(KEPT, REJECTED)));
        pick(0);
        onFx(() -> {});

        assertThat(locators()).containsExactly("вікно ch7 · p40–p42");
        assertThat(detailTexts()).contains("ch7 · p40 · залишено: виправлено закінчення роду");
        final Node detail = required("translating-live-detail-window");
        assertThat(detail).isInstanceOf(VBox.class);
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(detail))).isEmpty();
    }
}
