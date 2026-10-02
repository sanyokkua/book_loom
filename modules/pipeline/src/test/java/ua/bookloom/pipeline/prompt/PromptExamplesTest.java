package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;

/**
 * The bundled examples: the most specific file wins, and every example in every shipped file is one a run would accept
 * — a reply the strict parser reads, the source's tokens in the same order, and each declared pair still around words
 * on both sides — so a small model is never shown a reply the gate would refuse.
 */
class PromptExamplesTest {

    private static final DraftReplyParser PARSER = new DraftReplyParser(new ObjectMapper());
    private static final Pattern PAIRS_NOTE = Pattern.compile("^# pairs: (.*)$", Pattern.MULTILINE);
    private static final Pattern PAIR = Pattern.compile("(⟦g\\d+⟧) (⟦g\\d+⟧)");
    private static final int EXAMPLES_BUDGET = 400;

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "en, uk, en-uk",
                "de, uk, neutral",
                "en, de, de",
                "fr, de, de",
                "en-GB, pl, pl",
                "null, fr, fr",
                "en, ja, neutral",
                "null, ja, neutral"
            })
    void forPair_bundledFiles_choosesTheMostSpecificThatExists(
            @Nullable final String source, final String target, final String expectedFile) throws IOException {
        final String chosen = new PromptExamples(fileName -> PromptTemplates.class.getResourceAsStream(fileName))
                .forPair(source, target);

        assertThat(chosen).isEqualTo(PromptExamples.withoutNotes(shipped(expectedFile)));
    }

    @Test
    void candidates_regionalTags_areTriedByPrimaryLanguage() {
        assertThat(PromptExamples.candidates("en-US", "pt-BR")).containsExactly("en-pt", "pt", "neutral");
    }

    @Test
    void forPair_pairFileMissing_fallsBackToTheTargetThenNeutral() {
        final Map<String, String> files = Map.of("examples/uk.txt", "Source: A\nReply: {\"target\":\"Б\"}");
        final PromptExamples examples = new PromptExamples(fileName -> files.containsKey(fileName)
                ? new ByteArrayInputStream(files.get(fileName).getBytes(StandardCharsets.UTF_8))
                : null);

        assertThat(examples.forPair("en", "uk")).isEqualTo("Source: A\nReply: {\"target\":\"Б\"}");
    }

    @ParameterizedTest
    @MethodSource("shippedExamples")
    void shippedExample_reply_parsesAndKeepsTheSourceTokensInOrder(final Example example) {
        final ParsedReply parsed = PARSER.parse(example.reply());

        assertThat(parsed.kind()).as(example.source()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(Tokens.inOrder(parsed.translation())).isEqualTo(Tokens.inOrder(example.source()));
    }

    @ParameterizedTest
    @MethodSource("shippedExamples")
    void shippedExample_declaredPairs_wrapWordsOnBothSides(final Example example) {
        final String target = PARSER.parse(example.reply()).translation();

        assertThat(example.pairs())
                .allSatisfy(pair -> assertThat(List.of(example.source(), target))
                        .allSatisfy(text -> assertThat(between(text, pair[0], pair[1]))
                                .as("%s…%s in %s", pair[0], pair[1], text)
                                .isNotBlank()));
    }

    @ParameterizedTest
    @MethodSource("shippedFileNames")
    void shippedFile_rendered_staysWithinFourHundredTokens(final String fileName) throws IOException {
        final String rendered = PromptExamples.withoutNotes(shipped(fileName));

        assertThat(TokenEstimator.estimate(rendered, null)).isLessThanOrEqualTo(EXAMPLES_BUDGET);
        assertThat(rendered).doesNotContain("#");
    }

    /** One example: its source line, its reply line and the pairs its {@code # pairs:} note declares. */
    record Example(String source, String reply, List<String[]> pairs) {

        /** Copies the pairs. */
        Example {
            pairs = List.copyOf(pairs);
        }
    }

    static Stream<String> shippedFileNames() throws IOException, URISyntaxException {
        final Path directory = Path.of(Objects.requireNonNull(
                        PromptTemplates.class.getResource(PromptExamples.DIRECTORY), "examples directory")
                .toURI());
        try (Stream<Path> files = Files.list(directory)) {
            final List<String> names = files.map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - ".txt".length()))
                    .sorted()
                    .toList();
            return names.stream();
        }
    }

    static Stream<Example> shippedExamples() throws IOException, URISyntaxException {
        final List<String> texts =
                shippedFileNames().map(PromptExamplesTest::shippedUnchecked).toList();
        return texts.stream()
                .flatMap(text -> Arrays.stream(text.split("\n\\s*\n")))
                .filter(block -> block.contains("Source: "))
                .map(PromptExamplesTest::example);
    }

    private static Example example(final String block) {
        final String source = valueOf(block, "Source: ");
        final String reply = valueOf(block, "Reply: ");
        final Matcher note = PAIRS_NOTE.matcher(block);
        final List<String[]> pairs = note.find()
                ? PAIR.matcher(note.group(1))
                        .results()
                        .map(result -> new String[] {result.group(1), result.group(2)})
                        .toList()
                : List.of();
        return new Example(source, reply, pairs);
    }

    private static String valueOf(final String block, final String prefix) {
        return block.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + prefix + "line in: " + block));
    }

    private static String between(final String text, final String open, final String close) {
        final int start = text.indexOf(open);
        final int end = text.indexOf(close, Math.max(start, 0));
        return start < 0 || end < 0 ? "" : text.substring(start + open.length(), end);
    }

    private static String shipped(final String name) throws IOException {
        try (var stream = PromptTemplates.class.getResourceAsStream(PromptExamples.DIRECTORY + name + ".txt")) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String shippedUnchecked(final String name) {
        try {
            return shipped(name);
        } catch (IOException cause) {
            throw new IllegalStateException(name, cause);
        }
    }
}
