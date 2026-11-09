package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** What the context builder's run frame reads from the shipped language files (design D9). */
class FrameWordingTest {

    private static Properties file(final String resource) throws IOException {
        return LanguageFilesTest.file(resource);
    }

    @Test
    void generic_statesBookRuleAndReminderKeys() throws IOException {
        final Properties generic = file("languages/generic.properties");

        assertThat(generic.stringPropertyNames())
                .contains(
                        "rule.marks",
                        "rule.dialogue",
                        "rule.address",
                        "rule.numbers",
                        "rule.inflect",
                        "reminder.dialogue",
                        "reminder.narrator",
                        "illustration.label");
        assertThat(generic.getProperty("rule.inflect")).contains("decline them to fit, keep the stem");
        assertThat(generic.getProperty("reminder.marks")).isEqualTo("Keep every ⟦…⟧ mark exactly once.");
        assertThat(generic.getProperty("illustration.label")).contains("{target}");
    }

    // A book rule is stated in the target's own words when its file has them and in the generic words otherwise, so
    // no V1 target is ever left without one of the six lines.
    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void languageFile_everyV1Target_resolvesEveryBookRuleKey(final String tag) {
        final LanguageRules rules = LanguageRules.bundled();

        assertThat(List.of("marks", "dialogue", "address", "numbers", "agreement", "inflect"))
                .allSatisfy(key -> assertThat(FrameWording.bookRule(rules, tag, key))
                        .as(tag + " " + key)
                        .isNotBlank());
    }

    @Test
    void illustration_holdsNoTokenAndTargetScript() {
        final String illustration = FrameWording.illustration(LanguageRules.bundled(), "uk");

        assertThat(illustration).isNotBlank().doesNotContain("⟦");
        assertThat(illustration.codePoints().filter(Character::isLetter))
                .allMatch(letter -> Character.UnicodeScript.of(letter) == Character.UnicodeScript.CYRILLIC);
    }

    // Block 5 of the context builder leaves out what a book-rule line already says; today's section keeps it.
    @Test
    void section_excludedRules_leftOutOfTargetAndPair() {
        final LanguageRules rules = LanguageRules.bundled();

        assertThat(FrameWording.section(rules, "ru", "uk", Set.of(RuleKey.AGREEMENT)))
                .doesNotContain("- Agreement:");
        assertThat(rules.section("ru", "uk")).contains("- Agreement:");
    }
}
