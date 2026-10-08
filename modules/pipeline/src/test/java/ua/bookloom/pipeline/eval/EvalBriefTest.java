package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.prompt.StyleSheet;

class EvalBriefTest {

    private static final String REAL_RUN_STYLE = """
            Genre: cyberpunk novel
            Narrative voice / era: intense, technical jargon mixed with visceral description
            Audience: readers interested in cyberpunk themes
            Register: formal and literary; prefer elevated, careful diction.
            """;

    @TempDir
    private Path dir;

    @Test
    void of_noSettings_isTheDefaultBrief() {
        assertThat(EvalBrief.of("en", "uk", Map.of()))
                .isEqualTo(BookBrief.defaults("en").withLanguages("en", "uk"));
    }

    @Test
    void of_burningChromePreset_givesTheStyleSheetOfTheRealRunsBrief() {
        final BookBrief real = new BookBrief(
                "en",
                "uk",
                "cyberpunk novel",
                Register.FORMAL_LITERARY,
                "intense, technical jargon mixed with visceral description",
                "readers interested in cyberpunk themes",
                NamePolicy.TRANSLATE,
                BookBrief.defaults("en").foreignPassages(),
                BookBrief.defaults("en").footnotes(),
                BookBrief.defaults("en").units(),
                BookBrief.defaults("en").balance(),
                BookBrief.defaults("en").alsoTranslate(),
                QualityDial.BALANCED);

        final BookBrief eval = EvalBrief.of("en", "uk", Map.of("BOOKLOOM_EVAL_PRESET", "burning-chrome"));

        assertThat(StyleSheet.from(eval).text()).isEqualTo(StyleSheet.from(real).text());
        assertThat(StyleSheet.from(eval).text()).contains(REAL_RUN_STYLE.strip());
    }

    @Test
    void of_individualSettings_winOverThePreset() {
        final BookBrief brief = EvalBrief.of(
                "en",
                "uk",
                Map.of(
                        "BOOKLOOM_EVAL_PRESET", "burning-chrome",
                        "BOOKLOOM_EVAL_REGISTER", "casual",
                        "BOOKLOOM_EVAL_NAMES", "keep",
                        "BOOKLOOM_EVAL_GENRE", "noir",
                        "BOOKLOOM_EVAL_DIAL", "max"));

        assertThat(brief.register()).isEqualTo(Register.CASUAL);
        assertThat(brief.names()).isEqualTo(NamePolicy.KEEP_ORIGINAL);
        assertThat(brief.genre()).isEqualTo("noir");
        assertThat(brief.dial()).isEqualTo(QualityDial.MAX);
    }

    @Test
    void of_briefFile_setsItsFieldsAndAnEnvSettingWins() throws IOException {
        final Path file = dir.resolve("brief.json");
        Files.writeString(file, "{\"register\":\"FORMAL_LITERARY\",\"names\":\"translate\",\"genre\":\"space opera\"}");

        final BookBrief brief = EvalBrief.of(
                "en", "uk", Map.of("BOOKLOOM_EVAL_BRIEF", file.toString(), "BOOKLOOM_EVAL_GENRE", "western"));

        assertThat(brief.register()).isEqualTo(Register.FORMAL_LITERARY);
        assertThat(brief.names()).isEqualTo(NamePolicy.TRANSLATE);
        assertThat(brief.genre()).isEqualTo("western");
    }

    @Test
    void of_unknownPreset_failsNamingThePresets() {
        assertThatThrownBy(() -> EvalBrief.of("en", "uk", Map.of("BOOKLOOM_EVAL_PRESET", "nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("burning-chrome");
    }

    @Test
    void project_withThePreset_sendsTheRealRunsStyleSheet() {
        final BookBrief brief = EvalBrief.of("en", "uk", Map.of("BOOKLOOM_EVAL_PRESET", "burning-chrome"));
        final EvalProject project = EvalProject.of(
                EvalProject.Setup.single("en", "uk", List.of(), EvalContext.none(), "He ran."),
                (kind, segment, request) -> null,
                16384,
                brief);

        assertThat(project.frame().styleSheet()).isEqualTo(StyleSheet.from(brief));
    }
}
