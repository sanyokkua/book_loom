package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.qa.CheckLevel;

/** A pinned name inflects freely; a word that is none of its forms is a spelling error, and only a pinned one blocks. */
class NameSpellingTest {

    private static List<CheckFinding> run(
            final String source, final String target, final String pair, final boolean pinned) {
        final String rendering = pair.substring(pair.indexOf('→') + 1).strip();
        return TextChecks.run(new TextCheckInput(
                source,
                target,
                "en",
                "uk",
                List.of(pair),
                List.of(),
                UnitRole.BODY,
                pinned ? List.of(rendering) : List.of()));
    }

    @Test
    void check_pinnedNameInflected_noFinding() {
        assertThat(run("I saw Irwin.", "Я бачив Ірвіна.", "Irwin → Ірвін", true))
                .isEmpty();
    }

    @Test
    void check_pinnedNameRespelt_blocks() {
        final List<CheckFinding> found = run("David stayed home.", "Тавид залишився вдома.", "David → Давид", true);

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.NAME_SPELLING);
            assertThat(finding.level()).isEqualTo(CheckLevel.BLOCK);
            assertThat(finding.blocking()).isTrue();
            assertThat(finding.span().text()).isEqualTo("Тавид");
            assertThat(finding.explanation()).contains("Давид");
        });
    }

    @Test
    void check_suggestedNameRespelt_isANote() {
        final List<CheckFinding> found = run("David stayed home.", "Тавид залишився вдома.", "David → Давид", false);

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.NAME_SPELLING);
            assertThat(finding.level()).isEqualTo(CheckLevel.NOTE);
            assertThat(finding.blocking()).isFalse();
        });
    }
}
