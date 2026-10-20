package ua.bookloom.api.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

class AppSettingTest {

    @Test
    void defaults_perKey_matchTheSpecification() {
        assertThat(AppSetting.THEME.defaultValue()).isEqualTo(ThemeSetting.SYSTEM);
        assertThat(AppSetting.LANGUAGE.defaultValue()).isNull();
        assertThat(AppSetting.TECHNICAL_DETAILS.defaultValue()).isFalse();
        assertThat(AppSetting.REVIEW_MODE.defaultValue()).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(AppSetting.DEFAULT_DIAL.defaultValue()).isEqualTo(QualityDial.BALANCED);
        assertThat(AppSetting.REASONING.defaultValue()).isEqualTo(ReasoningSetting.OFF);
        assertThat(AppSetting.SAMPLING.defaultValue()).isEqualTo(SamplingSetting.PROGRAM_DEFAULTS);
        assertThat(AppSetting.OPEN_REVIEW_DESK.defaultValue()).isTrue();
        assertThat(AppSetting.LAST_CHECK.defaultValue()).isFalse();
        assertThat(AppSetting.KEEP_AWAKE.defaultValue()).isTrue();
        assertThat(AppSetting.REOPEN_LAST_PROJECT.defaultValue()).isTrue();
        assertThat(AppSetting.CURRENT_MODEL.defaultValue()).isNull();
        assertThat(AppSetting.LAST_PROJECT.defaultValue()).isNull();
        assertThat(AppSetting.FIRST_LAUNCH_PROBED.defaultValue()).isNull();
    }

    @Test
    void keys_perSetting_areTheStoredNames() {
        assertThat(AppSetting.THEME.key()).isEqualTo("ui.theme");
        assertThat(AppSetting.LANGUAGE.key()).isEqualTo("ui.language");
        assertThat(AppSetting.TECHNICAL_DETAILS.key()).isEqualTo("ui.technicalDetails");
        assertThat(AppSetting.REVIEW_MODE.key()).isEqualTo("run.reviewMode");
        assertThat(AppSetting.DEFAULT_DIAL.key()).isEqualTo("run.defaultDial");
        assertThat(AppSetting.REASONING.key()).isEqualTo("run.reasoning");
        assertThat(AppSetting.SAMPLING.key()).isEqualTo("run.sampling");
        assertThat(AppSetting.OPEN_REVIEW_DESK.key()).isEqualTo("automation.openReviewDesk");
        assertThat(AppSetting.LAST_CHECK.key()).isEqualTo("automation.lastCheck");
        assertThat(AppSetting.KEEP_AWAKE.key()).isEqualTo("automation.keepAwake");
        assertThat(AppSetting.REOPEN_LAST_PROJECT.key()).isEqualTo("automation.reopenLastProject");
        assertThat(AppSetting.CURRENT_MODEL.key()).isEqualTo("model.current");
        assertThat(AppSetting.LAST_PROJECT.key()).isEqualTo("app.lastProject");
        assertThat(AppSetting.FIRST_LAUNCH_PROBED.key()).isEqualTo("firstLaunch.probed");
    }

    @Test
    void sleepLineClosed_forAProject_hasAPerProjectKeyAndFalseDefault() {
        final AppSetting<Boolean> setting = AppSetting.sleepLineClosed("p-1");

        assertThat(setting.key()).isEqualTo("help.sleepLineClosed.p-1");
        assertThat(setting.defaultValue()).isFalse();
        assertThat(setting.parse("true")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"light", "LIGHT", " Light "})
    void parse_themeInAnyCase_givesLight(final String raw) {
        assertThat(AppSetting.THEME.parse(raw)).isEqualTo(ThemeSetting.LIGHT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nonsense", "", " "})
    @NullAndEmptySource
    void parse_badEnumValue_fallsBackToTheDefault(final String raw) {
        assertThat(AppSetting.THEME.parse(raw)).isEqualTo(ThemeSetting.SYSTEM);
        assertThat(AppSetting.REVIEW_MODE.parse(raw)).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(AppSetting.DEFAULT_DIAL.parse(raw)).isEqualTo(QualityDial.BALANCED);
        assertThat(AppSetting.REASONING.parse(raw)).isEqualTo(ReasoningSetting.OFF);
        assertThat(AppSetting.SAMPLING.parse(raw)).isEqualTo(SamplingSetting.PROGRAM_DEFAULTS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", "", "TRUE!"})
    void parse_badBoolean_fallsBackToTheDefault(final String raw) {
        assertThat(AppSetting.KEEP_AWAKE.parse(raw)).isTrue();
        assertThat(AppSetting.LAST_CHECK.parse(raw)).isFalse();
        assertThat(AppSetting.FIRST_LAUNCH_PROBED.parse(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "false"})
    void parse_goodBoolean_isTaken(final String raw) {
        assertThat(AppSetting.LAST_CHECK.parse(raw)).isEqualTo(Boolean.parseBoolean(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void parse_knownLanguage_isTaken(final String raw) {
        assertThat(AppSetting.LANGUAGE.parse(raw)).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "", "EN"})
    void parse_unknownLanguage_fallsBackToAbsent(final String raw) {
        assertThat(AppSetting.LANGUAGE.parse(raw)).isNull();
    }

    @Test
    void modelSelection_withAwkwardCharacters_roundTrips() {
        final ModelSelection selection = new ModelSelection("ollama", "library/gemma 4:e4b+x %20 é");

        final String stored = AppSetting.CURRENT_MODEL.format(selection);

        assertThat(AppSetting.CURRENT_MODEL.parse(stored)).isEqualTo(selection);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "onlyone", "a b c", "%zz m"})
    void parse_badModelSelection_fallsBackToAbsent(final String raw) {
        assertThat(AppSetting.CURRENT_MODEL.parse(raw)).isNull();
    }

    @Test
    void format_enumValue_isTheLowerCaseName() {
        assertThat(AppSetting.REVIEW_MODE.format(ReviewMode.ASSISTED)).isEqualTo("assisted");
        assertThat(AppSetting.SAMPLING.format(SamplingSetting.TUNED)).isEqualTo("tuned");
        assertThat(AppSetting.KEEP_AWAKE.format(false)).isEqualTo("false");
        assertThat(AppSetting.LAST_PROJECT.format("p-9")).isEqualTo("p-9");
    }

    @Test
    void parse_lastProjectBlank_fallsBackToAbsent() {
        assertThat(AppSetting.LAST_PROJECT.parse(" ")).isNull();
        assertThat(AppSetting.LAST_PROJECT.parse("p-9")).isEqualTo("p-9");
    }
}
