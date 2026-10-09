package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.eval.StageCases.RetryBook;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The stage suites proven offline: each runner against a fake model that answers like a good one, then a silent one,
 * with the recorded request compared with the production class's own prompt for the stage.
 */
class StageEvalRunnerTest {

    @TempDir
    private Path workDir;

    private static ChatRequest firstOf(final StageProbe probe, final CallKind kind) {
        return probe.requests().stream()
                .filter(request -> request.callKind() == kind)
                .findFirst()
                .orElseThrow();
    }

    private static String system(final ChatRequest request) {
        return request.messages().getFirst().content();
    }

    private static Map<String, String> draftsOf(final List<RetryBook> books, final boolean fixed) {
        final Map<String, String> drafts = new LinkedHashMap<>();
        books.forEach(book -> {
            book.fillers().forEach(filler -> drafts.put(filler.text(), filler.draft()));
            book.cases()
                    .forEach(retryCase -> drafts.put(retryCase.text(), fixed ? retryCase.fixed() : retryCase.draft()));
        });
        return drafts;
    }

    @Test
    void cases_cover_everyStageInBothLanguagePairs() {
        assertThat(StageCases.prescan())
                .extracting(StageCases.PreScanCase::source)
                .containsExactly("en", "ru");
        assertThat(StageCases.terms()).extracting(StageCases.TermsCase::source).containsExactly("en", "ru");
        assertThat(StageCases.setup()).hasSize(2);
        assertThat(StageCases.retry()).extracting(RetryBook::id).containsExactly("en-uk", "ru-uk");
        assertThat(StageCases.retry().getFirst().cases())
                .extracting(StageCases.RetryCase::id)
                .containsExactly("mixed-script", "left-untranslated", "closer-residue", "vocative-drop");
        assertThat(StageCases.prescan().getFirst().expect())
                .extracting(StageCases.Expect::term)
                .contains("Flint", "Monday");
        assertThat(StageCases.terms().getFirst().terms())
                .extracting(StageCases.TermVerdict::term)
                .contains("keep", "black", "screen", "datavault");
    }

    @Test
    void prescan_goodModel_passesAndSendsTheProductionPrompt() {
        final PreScanEvalRunner runner = new PreScanEvalRunner(new StageFakeModel(false));

        final List<StageRow> rows = runner.run(StageCases.prescan());

        assertThat(rows).extracting(StageRow::passed).as(rows.toString()).containsOnly(true);
        final ChatRequest sent = firstOf(runner.probe(), CallKind.PRESCAN);
        assertThat(sent.responseFormat().name()).isEqualTo(PromptName.PRESCAN.responseFormatName());
        assertThat(system(sent))
                .isEqualTo(new PromptTemplates()
                        .renderSystem(PromptName.PRESCAN, StageFrames.frame(StageFrames.brief("en", "uk"), "en", "uk"))
                        .strip());
        assertThat(rows.getFirst().calls()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void prescan_silentModel_failsEveryCaseWithTheNamesNotKept() {
        final List<StageRow> rows = new PreScanEvalRunner(new StageFakeModel(true)).run(StageCases.prescan());

        assertThat(rows).extracting(StageRow::passed).containsOnly(false);
        assertThat(rows.getFirst().detail()).contains("Flint: not kept");
    }

    @Test
    void terms_goodModel_dropsEverydayWordsKeepsJargonAndSendsTheProductionPrompt() {
        final TermsEvalRunner runner = new TermsEvalRunner(new StageFakeModel(false));

        final List<StageRow> rows = runner.run(StageCases.terms());

        assertThat(rows)
                .extracting(StageRow::id)
                .contains("terms-en-uk-everyday-vs-jargon:choice", "terms-en-uk-everyday-vs-jargon:review");
        assertThat(rows).extracting(StageRow::passed).as(rows.toString()).containsOnly(true);
        assertThat(system(firstOf(runner.probe(), CallKind.REVIEW_TERMS)))
                .isEqualTo(new PromptTemplates()
                        .renderSystem(
                                PromptName.TERM_CHOICE, StageFrames.frame(StageFrames.brief("en", "uk"), "en", "uk"))
                        .strip());
    }

    @Test
    void terms_modelThatKeepsEverything_failsTheChoice() {
        final List<StageRow> rows = new TermsEvalRunner(new StageFakeModel(true)).run(StageCases.terms());

        assertThat(rows.stream().filter(row -> row.id().endsWith(":choice")))
                .allSatisfy(row -> assertThat(row.passed()).isFalse());
    }

    @Test
    void setup_goodModel_namesTheBookInTheTargetScriptAndTheNarrator() {
        final SetupEvalRunner runner = new SetupEvalRunner(new StageFakeModel(false), workDir);

        final List<StageRow> rows = runner.run(StageCases.setup());

        assertThat(rows)
                .extracting(StageRow::id)
                .contains("setup-en-uk-first-person:name", "setup-ru-uk-third-person:brief");
        assertThat(rows).extracting(StageRow::passed).as(rows.toString()).containsOnly(true);
        final ChatRequest sent = runner.probe().requests().stream()
                .filter(request -> request.responseFormat().name().equals(PromptName.FILE_NAME.responseFormatName()))
                .findFirst()
                .orElseThrow();
        assertThat(sent.messages().getLast().content()).contains("File name: j_ostler-the_glass_orchard");
    }

    @Test
    void setup_modelWithAGenreOutsideTheClass_failsTheBriefNamingTheGenre() {
        final StageFakeModel good = new StageFakeModel(false);
        final ChatModel western = request -> good.chat(request)
                .map(response -> new ChatResponse(
                        response.content()
                                .replace("\"genre\":{\"value\":\"drama\"", "\"genre\":{\"value\":\"western\""),
                        response.finishReason()));

        final List<StageRow> rows = new SetupEvalRunner(western, workDir).run(StageCases.setup());

        assertThat(rows.stream().filter(row -> row.id().endsWith(":brief")))
                .allSatisfy(row -> assertThat(row.passed()).isFalse())
                .allSatisfy(row -> assertThat(row.detail()).startsWith("missed genre:"));
    }

    @ParameterizedTest
    @CsvSource({
        "THIRD, UNKNOWN, NEUTRAL, family saga, narrator",
        "FIRST, FEMALE, NEUTRAL, family saga, narratorGender",
        "FIRST, UNKNOWN, CASUAL, family saga, register",
        "FIRST, UNKNOWN, NEUTRAL, space opera, genre"
    })
    void misses_oneFieldOffTheCase_namesThatField(
            final NarratorPerson narrator,
            final Gender gender,
            final Register register,
            final String genre,
            final String missed) {
        final BriefSuggestion brief = new BriefSuggestion(genre, register, null, null, narrator, gender);

        assertThat(SetupEvalRunner.misses(StageCases.setup().getFirst(), brief)).containsExactly(missed);
    }

    @Test
    void consistency_goodModel_repairsEveryPlantedDefect() {
        final List<RetryBook> books = StageCases.retry();
        final RetryEvalRunner runner = new RetryEvalRunner(new PlantedDrafts(draftsOf(books, true)), workDir);

        final List<StageRow> rows = runner.consistency(books);

        assertThat(rows.stream().filter(row -> row.id().endsWith(":pass"))).hasSize(2);
        assertThat(rows).extracting(StageRow::passed).as(rows.toString()).containsOnly(true);
        assertThat(rows.stream().filter(row -> row.id().endsWith(":pass")))
                .allSatisfy(row -> assertThat(row.calls()).isPositive());
        assertThat(runner.probe().requests())
                .extracting(request -> request.callKind())
                .contains(CallKind.DRAFT);
    }

    @Test
    void consistency_modelThatKeepsThePlantedDrafts_failsTheDefectiveCases() {
        final List<RetryBook> books = StageCases.retry();

        final List<StageRow> rows =
                new RetryEvalRunner(new PlantedDrafts(draftsOf(books, false)), workDir).consistency(books);

        assertThat(rows.stream().filter(row -> row.id().endsWith(":mixed-script")))
                .allSatisfy(row -> assertThat(row.passed()).isFalse());
    }

    @Test
    void retry_goodModel_fixesEachCaseAndSendsDraftRequests() {
        final List<RetryBook> books = StageCases.retry();
        final RetryEvalRunner runner = new RetryEvalRunner(new PlantedDrafts(draftsOf(books, true)), workDir);

        final List<StageRow> rows = runner.retry(books);

        assertThat(rows).hasSize(8);
        assertThat(rows).extracting(StageRow::passed).as(rows.toString()).containsOnly(true);
        final ChatRequest sent = firstOf(runner.probe(), CallKind.DRAFT);
        assertThat(sent.responseFormat().name()).isEqualTo(PromptName.DRAFT.responseFormatName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prescan", "terms", "setup", "consistency", "retry"})
    void suites_everyName_runsAndReports(final String suite) {
        final List<RetryBook> books = StageCases.retry();
        final var model = suite.equals("consistency") || suite.equals("retry")
                ? new PlantedDrafts(draftsOf(books, true))
                : new StageFakeModel(false);

        final StageReport report = StageSuites.run(suite, "fake", model, workDir);

        assertThat(report.suite()).isEqualTo(suite);
        assertThat(report.rows()).isNotEmpty();
        assertThat(report.json()).startsWith("{\"suite\":\"" + suite + "\",\"model\":\"fake\"");
        assertThat(report.table()).contains("promptEval " + suite);
    }

    @Test
    void report_countsRowsCallsAndRefusals() {
        final StageReport report = new StageReport(
                "terms",
                "m",
                List.of(new StageRow("a", true, "ok", 2, 0, 0, 0), new StageRow("b", false, "bad", 3, 1, 1, 2)));

        assertThat(report.passRate()).isEqualTo(0.5);
        assertThat(report.json())
                .isEqualTo("{\"suite\":\"terms\",\"model\":\"m\",\"cases\":2,\"passRate\":0.500,\"calls\":5,"
                        + "\"failed\":1,\"repeated\":1,\"refused\":2,\"wastedCallRate\":0.400,"
                        + "\"callsPerCase\":2.500}");
    }

    @Test
    void probe_since_countsFailedAndRepeatedCallsAfterTheMark() {
        final StageProbe probe = new StageProbe(new StageFakeModel(false));
        final ChatRequest request = new ChatRequest(List.of(
                new ua.bookloom.api.llm.ChatMessage(ua.bookloom.api.llm.ChatRole.SYSTEM, "s"),
                new ua.bookloom.api.llm.ChatMessage(ua.bookloom.api.llm.ChatRole.USER, "u")));
        probe.chat(request);
        final int mark = probe.mark();
        probe.chat(request);
        probe.chat(request);

        assertThat(probe.since(mark)).isEqualTo(new StageProbe.Counts(2, 0, 1));
    }
}
