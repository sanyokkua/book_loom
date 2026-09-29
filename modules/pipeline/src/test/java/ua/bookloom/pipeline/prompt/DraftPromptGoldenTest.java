package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;

/** Pins the messages the draft and repair prompts produced before they moved into template files. */
class DraftPromptGoldenTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();

    // Moving the prose into templates must not change one byte the model receives.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "draft-en-uk",
                "draft-en-uk-preceding",
                "draft-unknown-source",
                "draft-zh-hant",
                "structural-repair",
                "placeholder-repair"
            })
    void messages_pinnedScenario_areByteEqualToGolden(final String name) throws IOException {
        final var messages = GoldenCases.render(
                name,
                (source, target) -> new DraftPromptBuilder(
                        TEMPLATES,
                        new CallFrame(
                                source,
                                target,
                                StyleSheet.from(BookBrief.defaults(source)),
                                ForeignPassagePolicy.KEEP)));

        assertThat(messages.get(0).content()).isEqualTo(golden(name + ".system.txt"));
        assertThat(messages.get(1).content()).isEqualTo(golden(name + ".user.txt"));
    }

    private static String golden(final String file) throws IOException {
        try (var stream = DraftPromptGoldenTest.class.getResourceAsStream("golden/" + file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
