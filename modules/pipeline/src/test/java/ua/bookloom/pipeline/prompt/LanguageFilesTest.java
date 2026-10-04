package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;

/**
 * Every shipped language and pair file: the keys it must state, only keys the assembler reads, the size limits the
 * prompt-hygiene lint holds each part to, and examples a run would accept — a reply the strict parser reads, the
 * source's tokens in the same order, and each declared pair still around words on both sides.
 */
class LanguageFilesTest {

    private static final int TARGET_LIMIT = 200;
    private static final int CHECKS_LIMIT = 60;
    private static final int SOURCE_LIMIT = 80;
    private static final int PAIR_LIMIT = 100;
    private static final DraftReplyParser PARSER = new DraftReplyParser(new ObjectMapper());
    private static final Pattern KEY = Pattern.compile(
            "(quotes|dialogue|apostrophe|hyphen|ellipsis|agreement|address|numbers|dates|names|status|nameExample"
                    + "|names\\.(convention|terms|policy\\.[A-Z_]+)|(pitfalls|sourceNotes|reviewerChecks)\\.\\d+"
                    + "|example\\.\\d+(\\.pairs)?)");
    private static final Pattern PAIR = Pattern.compile("(⟦g\\d+⟧) (⟦g\\d+⟧)");

    static Properties file(final String resource) throws IOException {
        final Properties properties = new Properties();
        try (InputStream stream = LanguageRules.class.getResourceAsStream(resource)) {
            properties.load(new InputStreamReader(Objects.requireNonNull(stream, resource), StandardCharsets.UTF_8));
        }
        return properties;
    }

    static Stream<String> everyFile() {
        return Stream.of(
                        Stream.of("languages/generic.properties"),
                        V1Languages.LANGUAGES.stream().map(tag -> "languages/" + tag + ".properties"),
                        V1Languages.PAIRS.stream().map(pair -> "languages/pairs/" + pair + ".properties"))
                .flatMap(stream -> stream);
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void languageFile_everyV1Language_statesTheRequiredKeys(final String tag) throws IOException {
        final Properties properties = file("languages/" + tag + ".properties");

        assertThat(properties.stringPropertyNames())
                .contains("status", "quotes", "agreement", "pitfalls.1", "reviewerChecks.1", "sourceNotes.1");
        assertThat(properties.getProperty("status")).isIn("tested", "untested");
    }

    @ParameterizedTest
    @MethodSource("everyFile")
    void file_everyKey_isOneTheAssemblerReads(final String resource) throws IOException {
        assertThat(file(resource).stringPropertyNames())
                .allMatch(key -> KEY.matcher(key).matches(), resource);
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void languageFile_target_staysWithinTheTargetLimits(final String tag) {
        final LanguageRules rules = LanguageRules.bundled();

        assertThat(tokens(rules.targetRules(tag, false))).as(tag).isLessThanOrEqualTo(TARGET_LIMIT);
        assertThat(tokens(rules.targetRules(tag, true)) - tokens(rules.targetRules(tag, false)))
                .as(tag + " reviewer checks")
                .isLessThanOrEqualTo(CHECKS_LIMIT);
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void languageFile_source_staysWithinTheSourceLimit(final String tag) {
        assertThat(tokens(LanguageRules.bundled().sourceRules(tag))).as(tag).isLessThanOrEqualTo(SOURCE_LIMIT);
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#pairs")
    void pairFile_rules_stayWithinThePairLimit(final String pair) {
        final int dash = pair.indexOf('-');
        final String source = pair.substring(0, dash);
        final String target = pair.substring(dash + 1);

        assertThat(tokens(LanguageRules.bundled().pairRules(source, target, true)))
                .as(pair)
                .isLessThanOrEqualTo(PAIR_LIMIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ru", "uk"})
    void section_languagesTheOwnerReads_areNotEmptyInBothDirections(final String tag) {
        assertThat(LanguageRules.bundled().sourceRules(tag)).isNotBlank();
        assertThat(LanguageRules.bundled().targetRules(tag, false)).startsWith("Target — ");
    }

    @ParameterizedTest
    @MethodSource("exampleReplies")
    void example_reply_parsesAndKeepsTheSourceTokensInOrder(final Example example) {
        final ParsedReply parsed = PARSER.parse(example.reply());

        assertThat(parsed.kind()).as(example.source()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(Tokens.inOrder(parsed.translation())).isEqualTo(Tokens.inOrder(example.source()));
    }

    @ParameterizedTest
    @MethodSource("exampleReplies")
    void example_declaredPairs_wrapWordsOnBothSides(final Example example) {
        final String target = PARSER.parse(example.reply()).translation();
        final var pairs = PAIR.matcher(example.pairs()).results().toList();

        assertThat(pairs)
                .allSatisfy(pair -> assertThat(List.of(example.source(), target))
                        .allSatisfy(text -> assertThat(between(text, pair.group(1), pair.group(2)))
                                .as("%s…%s in %s", pair.group(1), pair.group(2), text)
                                .isNotBlank()));
    }

    @ParameterizedTest
    @MethodSource("everyFile")
    void file_examples_stayWithinFourHundredTokens(final String resource) throws IOException {
        final Properties properties = file(resource);
        final String all = String.join(
                "\n\n",
                properties.stringPropertyNames().stream()
                        .filter(key -> key.matches("example\\.\\d+"))
                        .sorted()
                        .map(properties::getProperty)
                        .toList());

        assertThat(tokens(all)).as(resource).isLessThanOrEqualTo(400);
    }

    @Test
    void nameExample_everyFileThatHasOne_endsWithAReplyTheSuggestionParserReads() throws IOException {
        for (final String resource : List.of("languages/generic.properties", "languages/pairs/en-uk.properties")) {
            final String text = file(resource).getProperty("nameExample");

            assertThat(text).as(resource).contains("A valid reply:\n{\"suggestions\":[");
        }
    }

    /** One example: its source line, its reply line and the pairs its {@code .pairs} key declares. */
    record Example(String source, String reply, String pairs) {}

    static Stream<Example> exampleReplies() throws IOException {
        return everyFile().flatMap(LanguageFilesTest::examplesOf);
    }

    private static Stream<Example> examplesOf(final String resource) {
        try {
            final Properties properties = file(resource);
            return properties.stringPropertyNames().stream()
                    .filter(key -> key.matches("example\\.\\d+"))
                    .sorted()
                    .map(key -> new Example(
                            line(properties.getProperty(key), "Source: "),
                            line(properties.getProperty(key), "Reply: "),
                            properties.getProperty(key + ".pairs", "")));
        } catch (IOException cause) {
            throw new IllegalStateException(resource, cause);
        }
    }

    private static String line(final String block, final String prefix) {
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

    private static int tokens(final String text) {
        return TokenEstimator.estimate(text, "en");
    }
}
