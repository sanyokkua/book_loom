package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/** Per-word script purity, including a word wholly in a script the target does not use. */
class ScriptPurityCheckTest {

    private static List<CheckFinding> uk(final String source, final String target) {
        return ScriptPurityCheck.find(source, target, Languages.scriptOf("uk"));
    }

    @Test
    void check_cjkWordInUkTarget_blocks() {
        final List<CheckFinding> found = uk("He found the key.", "Він знайшов 鍵 у шухляді.");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.MIXED_SCRIPT);
            assertThat(finding.blocking()).isTrue();
            assertThat(finding.span().text()).isEqualTo("鍵");
        });
    }

    @Test
    void check_latinBrandInUkTarget_allowed() {
        assertThat(uk("He bought a ThinkPad.", "Він купив ThinkPad.")).isEmpty();
    }

    @Test
    void check_mixedScriptWord_flags() {
        assertThat(uk("He learned.", "Він навчg3вся."))
                .singleElement()
                .satisfies(finding -> assertThat(finding.span().text()).isEqualTo("навчg3вся"));
    }

    @Test
    void check_foreignScriptWordHeldBySource_allowed() {
        assertThat(uk("He wrote 鍵 on it.", "Він написав 鍵 на ньому.")).isEmpty();
    }

    @Test
    void check_fullWidthStopInUkTarget_blocks() {
        assertThat(uk("He left.", "Він пішов。")).singleElement().satisfies(finding -> {
            assertThat(finding.blocking()).isTrue();
            assertThat(finding.span().text()).isEqualTo("。");
        });
    }

    @Test
    void check_fullWidthStopHeldBySource_allowed() {
        assertThat(uk("他走了。", "Він пішов。")).isEmpty();
    }

    @Test
    void check_targetWithoutASpacedAlphabet_isSkipped() {
        assertThat(ScriptPurityCheck.find("x", "他走了。", Optional.of(Script.HAN))).isEmpty();
    }
}
