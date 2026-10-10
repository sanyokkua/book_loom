package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PromptHygieneTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "(none)", "None", "[none]", "n/a", "N/A.", "-", "—", "(empty)"})
    void isFiller_placeholderOrBlank_isTrue(final String line) {
        assertThat(PromptHygiene.isFiller(line)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hale → Гейл (character, male)", "(none — write no ⟦gN⟧ token)", "None of them came."})
    void isFiller_contentLine_isFalse(final String line) {
        assertThat(PromptHygiene.isFiller(line)).isFalse();
    }

    @Test
    void clean_fillerAndRepeats_keepsTheFirstOfEachContentLineInOrder() {
        assertThat(PromptHygiene.clean(List.of("b", "(none)", "a", "", "b", "a")))
                .containsExactly("b", "a");
    }

    @Test
    void cleanText_fillerText_isNull() {
        assertThat(PromptHygiene.cleanText("(none)")).isNull();
        assertThat(PromptHygiene.cleanText("The book so far.")).isEqualTo("The book so far.");
    }

    @Test
    void draftContext_fillerEverywhere_leavesNoSection() {
        final DraftContext context =
                new DraftContext(List.of("(none)", "Один.", "Один."), "(none)", List.of("-"), List.of(""), List.of());

        assertThat(context.precedingTargets()).containsExactly("Один.");
        assertThat(context.summary()).isNull();
        assertThat(context.glossaryLines()).isEmpty();
        assertThat(context.memoryLines()).isEmpty();
    }

    private static final Path PROMPTS = Path.of("src/main/resources/ua/bookloom/pipeline/prompt");
    // A template-tag, block, JSON or finished-sentence line may be followed by anything.
    private static final Pattern SKIPPED_LINE =
            Pattern.compile("^(\\{\\{[#/^]?\\w+\\}\\}$|[\\[<\\]]|\\{(?!\\{)|\")|[.:?!\"}>\\]]$");
    // A list item, a numbered rule, a tag or a template tag starts a line of its own.
    private static final Pattern NEW_BLOCK =
            Pattern.compile("^(- |\\d+\\. |\\{\\{[#/^]?\\w+\\}\\}$|[\\[<\\]]|\\{(?!\\{))");
    private static final Pattern WRONG_ARTICLE =
            Pattern.compile("\\ba (\\{\\{(source|target)Language\\}\\}|English\\b)");

    // A line cut mid-sentence at a fixed column reads as two sentences to a small model, and costs a wasted newline.
    @ParameterizedTest
    @MethodSource("promptFiles")
    void templates_noSentenceIsHardWrapped(final Path file) throws IOException {
        final List<String> lines = Files.readAllLines(file);

        assertThat(IntStream.range(0, lines.size() - 1)
                        .filter(i -> isHardWrapped(lines.get(i), lines.get(i + 1)))
                        .mapToObj(i -> file.getFileName() + ":" + (i + 1)))
                .isEmpty();
    }

    // "a English" is wrong for every source language that starts with a vowel; the templates name no article before a
    // slot.
    @ParameterizedTest
    @MethodSource("promptFiles")
    void templates_noArticleBeforeALanguageSlot(final Path file) throws IOException {
        assertThat(WRONG_ARTICLE.matcher(Files.readString(file)).find()).isFalse();
    }

    private static boolean isHardWrapped(final String line, final String next) {
        if (line.isBlank()
                || next.isBlank()
                || SKIPPED_LINE.matcher(line.strip()).find()) {
            return false;
        }
        return !NEW_BLOCK.matcher(next).find();
    }

    static List<Path> promptFiles() throws IOException {
        try (Stream<Path> files = Files.list(PROMPTS)) {
            return files.filter(file -> file.toString().endsWith(".prompt"))
                    .sorted()
                    .toList();
        }
    }
}
