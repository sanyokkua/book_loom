package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.ReplyCase.Expect;

/**
 * The defect classes of the October 8 real run held to production code with no model: each synthetic reply is read by
 * the draft reply parser and judged by the run's own classes, and the corpus carries both source languages.
 */
class DefectClassCorpusTest {

    private static final int OVER_MEDIUM = 250;
    private static final int OVER_LONG = 400;

    private static Stream<Arguments> replies(final boolean known) {
        return RunCorpus.replies().stream()
                .filter(c -> c.knownFailure() == known)
                .map(c -> Arguments.of(c.id(), c));
    }

    private static Stream<Arguments> rightReplies() {
        return replies(false);
    }

    private static Stream<Arguments> knownReplies() {
        return replies(true);
    }

    /** What the run makes of the case's reply: accepted as drafted, or refused. */
    static Expect outcome(final ReplyCase replyCase) {
        final EvalProject project = EvalProject.of(
                EvalProject.Setup.single(
                        replyCase.language(),
                        RunCorpus.TARGET_LANGUAGE,
                        replyCase.glossary(),
                        EvalContext.none(),
                        replyCase.source()),
                (kind, segmentId, request) ->
                        Result.err(new AppError(ErrorCode.internal, "t", "no call", null, false, null)));
        return ReplyJudge.judgeReply(project, 0, replyCase.reply()).accepted() ? Expect.ACCEPTED : Expect.REFUSED;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rightReplies")
    void reply_productionReaderAndJudge_decideAsTheCaseExpects(final String id, final ReplyCase replyCase) {
        assertThat(outcome(replyCase)).as(id).isEqualTo(replyCase.resolved());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownReplies")
    void knownFailure_reply_isStillDecidedWrongByProduction(final String id, final ReplyCase replyCase) {
        assertThat(outcome(replyCase))
                .as(id + " now decided right: drop knownFailure (task " + replyCase.fixedBy() + ")")
                .isNotEqualTo(replyCase.resolved());
        assertThat(replyCase.fixedBy()).matches("[A-E]\\d+");
    }

    @Test
    void controlCharReplies_expectation_isTheNamedConstant() {
        assertThat(RunCorpus.replies().stream().filter(c -> c.kind().equals("control-chars")))
                .filteredOn(c -> c.expect() == Expect.CONTROL_CHARS)
                .hasSizeGreaterThanOrEqualTo(5)
                .allSatisfy(c -> assertThat(c.resolved()).isEqualTo(ReplyExpectations.CONTROL_CHARS));
    }

    @Test
    void replies_idsAreUniqueAndBothLanguagesAreCovered() {
        final List<ReplyCase> replies = RunCorpus.replies();

        assertThat(replies.stream().map(ReplyCase::id)).doesNotHaveDuplicates();
        assertThat(replies.stream().map(ReplyCase::language).distinct()).containsExactlyInAnyOrder("en", "ru");
        assertThat(replies.stream().map(ReplyCase::kind).distinct())
                .contains("control-chars", "closer-residue", "mixed-script-tail", "gender-name", "numbers");
    }

    @Test
    void text_newDefectClasses_haveEnglishAndRussianCases() {
        final Map<String, List<String>> languagesByKind = RunCorpus.text().stream()
                .collect(Collectors.groupingBy(
                        RunCase::kind, Collectors.mapping(RunCase::language, Collectors.toList())));

        assertThat(languagesByKind)
                .containsKeys(
                        "female-noun-name",
                        "slang",
                        "numbers",
                        "ascii-dialogue",
                        "venue-name",
                        "vocative-position",
                        "closer-residue",
                        "mixed-script-tail");
        assertThat(languagesByKind.get("female-noun-name")).contains("en", "ru");
        assertThat(languagesByKind.get("slang")).contains("en", "ru");
        assertThat(languagesByKind.get("vocative-position")).contains("en", "ru");
    }

    @Test
    void text_vocativePositions_coverStartMiddleAndEnd() {
        assertThat(RunCorpus.text().stream()
                        .filter(c -> c.kind().equals("vocative-position"))
                        .map(RunCase::id))
                .contains("vocative-start-en", "vocative-middle-en", "vocative-end-en");
    }

    @Test
    void text_femaleNameCases_nameAFemaleCharacterAndMarkTheFeminineVerb() {
        assertThat(RunCorpus.text().stream().filter(c -> c.kind().equals("female-noun-name")))
                .isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.glossary()).singleElement().satisfies(term -> {
                        assertThat(term.gender()).isEqualTo(ua.bookloom.api.project.Gender.FEMALE);
                        assertThat(term.type()).isEqualTo(ua.bookloom.api.project.TermType.CHARACTER);
                    });
                    assertThat(c.marker()).isNotBlank();
                });
    }

    @Test
    void draftCases_longParagraphs_sitOverTheTwoThresholdsAndKeepTheirNumbers() {
        final Map<String, String> byName = PromptEvalCases.ALL.stream()
                .filter(Draft.class::isInstance)
                .map(Draft.class::cast)
                .collect(Collectors.toMap(Draft::name, Draft::masked));

        assertThat(byName.get("long-paragraph-medium"))
                .hasSizeGreaterThan(OVER_MEDIUM)
                .contains("17");
        assertThat(byName.get("long-paragraph-long"))
                .hasSizeGreaterThan(OVER_LONG)
                .contains("63", "61");
        assertThat(BatchEvalCases.drafts().stream().map(Draft::name))
                .contains("long-paragraph-medium", "long-paragraph-long");
    }
}
