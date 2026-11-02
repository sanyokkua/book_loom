package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;
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
            "(quotes|dialogue|apostrophe|hyphen|ellipsis|agreement|address|numbers|dates|names|status|nameExample|genderCheck|stemAlternations|forbiddenLetters|obliqueEndings|nonNominativeEndings|articles|openNameFinals|officeTitles|voicingPairs|honorifics|negationWords|moodWords|translit|namesInflect|firstPersonPronouns|femalePronouns|malePronouns|femaleObjectPronouns|maleObjectPronouns|pluralSuffixes|determiners|indefiniteArticles|pluralPronouns|nameJoiners|firmJoiners|firmTails|femaleTitles|maleTitles|titleRenderings|foreignWords|addressTerms|informalAddress|formalAddress|dialogueAttribution|nameAlternations|nounEndings|clippedVocative|femSurnameMap|pastFemale|pastMale|pastNeuter|pastSkipWords|pastNotVerbs|paradigmGender|pastMaleIrregular|shortAdjFemale|shortAdjMale|addressPositions|stripStressMarks|conventions|negationCounts|outputRatio|briefApostrophes|briefQuotes"
                    + "|names\\.(convention|terms|policy\\.[A-Z_]+)|(pitfalls|sourceNotes|reviewerChecks)\\.\\d+"
                    + "|example\\.\\d+(\\.pairs)?|batchExample\\.\\d+"
                    + "|nameParadigms\\.\\d+|convention\\.[A-Za-z]+\\.(choices|default)"
                    + "|prepExample\\.(termReading|lexiconReading|prescan|reviewTerms)\\.(\\d+|reply)"
                    + "|nameExample\\.(TRANSLATE|TRANSLITERATE|KEEP_ORIGINAL))");
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

    @Test
    void pairFile_enUkTitleRenderings_mapLowerCaseSourceWordsToTargetScriptWords() throws IOException {
        final String rows = file("languages/pairs/en-uk.properties").getProperty("titleRenderings");

        assertThat(rows.split("\\s+")).isNotEmpty().allMatch(row -> row.matches("\\p{Ll}+>\\p{IsCyrillic}+"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"languages/ru.properties", "languages/uk.properties"})
    void file_negationAndMoodWords_shareNoWord(final String resource) throws IOException {
        final Properties properties = file(resource);
        final Set<String> negations =
                Set.of(properties.getProperty("negationWords").split(" "));
        final Set<String> moods = Set.of(properties.getProperty("moodWords").split(" "));
        assertThat(negations).doesNotContainAnyElementsOf(moods);
    }

    @Test
    void file_russianTarget_statesForbiddenLettersAndAddressForms() throws IOException {
        final Properties properties = file("languages/ru.properties");
        assertThat(properties.getProperty("forbiddenLetters")).isEqualTo("іїєґ");
        assertThat(properties.getProperty("informalAddress")).contains("ты", "тебе");
        assertThat(properties.getProperty("formalAddress")).contains("Вы", "Вам");
        assertThat(properties.getProperty("honorifics")).contains("г-н", "д-р");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nameParadigz.1", "nameParadigms", "functionWords", "convention.hardG.other"})
    void key_madeUpOrMalformed_isRejected(final String key) {
        assertThat(KEY.matcher(key).matches()).isFalse();
    }

    @Test
    void conventions_ukrainian_declaresHardGWithDefaultGe() throws IOException {
        final Properties uk = file("languages/uk.properties");
        assertThat(uk.getProperty("conventions")).isEqualTo("hardG");
        assertThat(uk.getProperty("convention.hardG.choices")).isEqualTo("г ґ");
        assertThat(uk.getProperty("convention.hardG.default")).isEqualTo("г");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ru", "fr", "hr", "pl", "cs", "sl", "sk", "es", "pt", "de"})
    void conventions_languageWithoutTheKey_declaresNone(final String tag) throws IOException {
        assertThat(file("languages/" + tag + ".properties").getProperty("conventions"))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ru", "uk", "fr", "hr", "pl", "cs", "sl", "sk", "es", "pt", "de"})
    void stripStressMarks_onlyRussianAndUkrainian_setIt(final String tag) throws IOException {
        final String value = file("languages/" + tag + ".properties").getProperty("stripStressMarks");
        assertThat(value).isEqualTo(Set.of("ru", "uk").contains(tag) ? "true" : null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk"})
    void pastEndings_russianAndUkrainian_stateFemaleAndMale(final String tag) throws IOException {
        final Properties properties = file("languages/" + tag + ".properties");
        assertThat(properties.getProperty("pastFemale")).contains("ла");
        assertThat(properties.getProperty("pastMale")).isNotBlank();
        assertThat(properties.getProperty("pastMaleIrregular")).isNotBlank();
        assertThat(properties.getProperty("shortAdjFemale")).contains("рада");
        assertThat(properties.getProperty("shortAdjMale")).contains("рад");
        assertThat(properties.getProperty("addressPositions")).startsWith("у ");
    }

    static Stream<String> preparationExamples() {
        return Stream.of("generic", "ru", "uk")
                .flatMap(file -> Stream.of("termReading", "lexiconReading", "prescan", "reviewTerms")
                        .map(kind -> file + ":" + kind));
    }

    // IF an example's quote, evidence or term were not in its own window, THEN the prompt would teach the model to
    // quote what the code then refuses; and a reply the parser cannot read would teach a broken shape.
    @ParameterizedTest
    @MethodSource("preparationExamples")
    void prepExample_everyFile_quotesStandInTheirWindowAndRepliesParse(final String fileAndKind) throws IOException {
        final int colon = fileAndKind.indexOf(':');
        final Properties properties = file("languages/" + fileAndKind.substring(0, colon) + ".properties");
        final String prefix = "prepExample." + fileAndKind.substring(colon + 1) + ".";
        final List<String> lines = Stream.iterate(1, n -> properties.getProperty(prefix + n) != null, n -> n + 1)
                .map(n -> properties.getProperty(prefix + n))
                .toList();
        final ObjectMapper mapper = new ObjectMapper();

        assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
        assertThat(mapper.readTree(properties.getProperty(prefix + "reply")).isObject())
                .isTrue();
        assertThat(lines).filteredOn(line -> line.contains("→ {")).allSatisfy(line -> {
            final String window =
                    line.substring(0, line.indexOf(" → ")).replace("[[", "").replace("]]", "");
            final var answer = mapper.readTree(line.substring(line.indexOf("→ {") + 2));
            assertThat(window).contains(answer.path("quote").asText("none").replace("none", ""));
            assertThat(window).contains(answer.path("evidence").asText(""));
            assertThat(window).contains(answer.path("term").asText(""));
        });
    }

    @ParameterizedTest
    @CsvSource({
        "ru-uk,TRANSLITERATE",
        "ru-uk,TRANSLATE",
        "ru-uk,KEEP_ORIGINAL",
        "en-uk,TRANSLITERATE",
        "en-uk,TRANSLATE",
        "en-uk,KEEP_ORIGINAL"
    })
    void nameExamples_ruUkAndEnUk_holdOnePerPolicy(final String pair, final String policy) throws IOException {
        final Properties properties = file("languages/pairs/" + pair + ".properties");
        assertThat(properties.getProperty("nameExample." + policy)).contains("A valid reply:\n{\"suggestions\":[");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru-uk", "en-uk", "uk-ru", "pl-uk"})
    void negationCounts_ruUkOnly_isSet(final String pair) throws IOException {
        final String value = file("languages/pairs/" + pair + ".properties").getProperty("negationCounts");
        assertThat(value).isEqualTo(pair.equals("ru-uk") ? "true" : null);
    }

    @Test
    void key_oldFunctionWordsKey_isRejected() {
        assertThat(KEY.matcher("functionWords").matches()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("everyFile")
    void file_everyKey_isOneTheAssemblerReads(final String resource) throws IOException {
        assertThat(file(resource).stringPropertyNames())
                .allMatch(key -> KEY.matcher(key).matches(), resource);
    }

    @ParameterizedTest
    @MethodSource("everyFile")
    void file_dialogueAttribution_compiles(final String resource) throws IOException {
        final String pattern = file(resource).getProperty("dialogueAttribution", "");

        assertThatCode(() -> Pattern.compile(pattern)).as(resource).doesNotThrowAnyException();
    }

    // A file that lists subject pronouns states its object forms too, even as an empty list, so a language whose
    // object forms are ambiguous says so instead of being forgotten (15h.A2).
    @ParameterizedTest
    @MethodSource("everyFile")
    void file_subjectPronouns_comeWithTheObjectForms(final String resource) throws IOException {
        final Properties properties = file(resource);

        assertThat(properties.containsKey("femalePronouns"))
                .as(resource)
                .isEqualTo(properties.containsKey("femaleObjectPronouns"));
        assertThat(properties.containsKey("malePronouns"))
                .as(resource)
                .isEqualTo(properties.containsKey("maleObjectPronouns"));
    }

    // Every language file lists its words of address, each pair naming male or female (15h.G2).
    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void languageFile_addressTerms_listOnlyMaleOrFemalePairs(final String tag) throws IOException {
        final String terms = file("languages/" + tag + ".properties").getProperty("addressTerms");

        assertThat(terms).as(tag).isNotBlank();
        assertThat(List.of(terms.split("\\s+"))).as(tag).allMatch(pair -> pair.matches("[^>]+>(male|female)"));
    }

    @Test
    void addressTerms_englishAndUkrainian_mapEachWordToItsGender() {
        assertThat(AddressTerms.of("en")).containsEntry("sir", Gender.MALE).containsEntry("ma'am", Gender.FEMALE);
        assertThat(AddressTerms.of("uk")).containsEntry("пане", Gender.MALE).containsEntry("дівчино", Gender.FEMALE);
        assertThat(AddressTerms.of("de")).containsEntry("fräulein", Gender.FEMALE);
    }

    @Test
    void objectPronouns_englishAndUkrainian_listTheFormsThatShowGender() {
        assertThat(Pronouns.femaleObject("en")).containsExactlyInAnyOrder("her", "hers");
        assertThat(Pronouns.maleObject("uk")).contains("його", "йому");
        assertThat(Pronouns.femaleObject("fr")).isEmpty();
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
        final ParsedReply parsed = PARSER.parse(example.reply(), example.source());

        assertThat(parsed.kind()).as(example.source()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(Tokens.inOrder(parsed.translation())).isEqualTo(Tokens.inOrder(example.source()));
    }

    @ParameterizedTest
    @MethodSource("exampleReplies")
    void example_declaredPairs_wrapWordsOnBothSides(final Example example) {
        final String target = PARSER.parse(example.reply(), example.source()).translation();
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

    static Stream<Example> exampleReplies() {
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

    // A model fed straight quotes read the speaker's "he" as a name's gender and answered with control codes; one
    // worked line shows what a straight-quoted dialogue becomes in the target (15h.C3).
    @Test
    void section_englishToUkrainian_quoteLineCarriesAStraightQuotedExample() {
        final String section = LanguageRules.bundled().section("en", "uk", false);

        assertThat(section)
                .contains(
                        "- Quotes: Straight quotes are dialogue too: \"Stay,\" he said. → «Залишайся», — сказав він.");
    }
}
