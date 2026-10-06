package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.eval.EvalContext;
import ua.bookloom.pipeline.eval.EvalContext.Pair;
import ua.bookloom.pipeline.eval.EvalProject;
import ua.bookloom.pipeline.eval.EvalTerm;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftEvaluation;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.SegmentFindings;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewedPair;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * What the prompt evals send is what a running job sends: the same scripted book is run through the job, whose model
 * records every request, and the eval path — {@link EvalProject} over the run's own request factory, the calls through
 * {@link JobModelCalls} — builds each request again from the same case; the two must be equal in messages, response
 * format and every sampling and size parameter.
 */
class PromptRequestsEquivalenceTest {

    private static final String BATCH = "draft-batch-json";
    private static final String DRAFT = "draft";
    private static final String REVIEW = "reviewer";
    private static final String FIX = "directed-fix";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PARAGRAPHS = 8;
    private static final String SUMMARY_TEXT = "Раніше: Натаніель шукав стару мапу.";

    private static final List<String> SOURCES = List.of(
            "Bartimaeus waited by the old fireplace for hours.",
            "Nathaniel opened the door and the master smiled.",
            "I walked home through the cold rain.",
            "The lamp burned low on the table.",
            "Nobody spoke until the clock struck nine.",
            "The master looked at the map for a long time.",
            "Outside, the wind shook the shutters.",
            "I closed the book and went to sleep.");
    private static final List<String> TARGETS = List.of(
            "Бартімеус годинами чекав біля старого каміна.",
            "⟦g0⟧ відчинив двері, і господар усміхнувся.",
            "Я йшов додому крізь холодний дощ.",
            "Лампа тьмяно горіла на столі біля вікна.",
            "Ніхто не озвався до першого удару дзиґаря.",
            "Господар довго дивився на мапу, не кажучи нічого.",
            "Надворі вітер сильно трусив віконниці.",
            "Я закрив книжку й одразу ліг спати.");
    private static final String ECHO_OF_LAST = "I CLOSED THE BOOK AND WENT TO SLEEP.";

    private static final List<EvalTerm> GLOSSARY = List.of(
            new EvalTerm("Bartimaeus", "Бартімеус", TermType.CHARACTER, Gender.MALE, false),
            new EvalTerm("Nathaniel", "Натаніель", TermType.CHARACTER, Gender.MALE, true));
    private static final Narrator FIRST_PERSON_MALE = new Narrator(NarratorPerson.FIRST, Gender.MALE);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject book(final boolean withLexicon) {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", SOURCES), "en"),
                brief("en", "uk", QualityDial.BALANCED).withNarrator(FIRST_PERSON_MALE));
        GLOSSARY.forEach(term -> project.stores().glossary().add(term.entry(project.id())));
        if (withLexicon) {
            project.stores().lexicon().put(new LexiconEntry(project.id(), "master", List.of(), "господар", null, null));
        }
        project.stores().summaries().save(new RollingSummary(project.id(), null, "Earlier.", SUMMARY_TEXT, 1, null, 0));
        return project;
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private static String items(final List<String> targets) {
        final List<String> entries = IntStream.range(0, targets.size())
                .mapToObj(i -> "{\"id\":\"" + (i + 1) + "\",\"target\":\"" + targets.get(i) + "\"}")
                .toList();
        return "{\"items\":[" + String.join(",", entries) + "]}";
    }

    private static Result<ChatResponse> draftReply(final String target) {
        return reply(TranslationJobTestSupport.targetReply(target));
    }

    /** The eval path's calls: the run's own seam in front of a model that records what reaches it. */
    private static JobModelCalls evalCalls(final ScriptedChatModel recording) {
        return new JobModelCalls(onSent -> recording, event -> {}, Clock.systemUTC(), "uk", EvalProject.window());
    }

    private static ScriptedChatModel answeringAnything() {
        return new ScriptedChatModel().answerTimes(PARAGRAPHS * 2, reply("{\"results\":[],\"items\":[]}"));
    }

    private static EvalProject.Setup setup(final EvalContext context) {
        return new EvalProject.Setup("en", "uk", "en", GLOSSARY, context, SOURCES, List.of());
    }

    private static EvalContext context(final boolean withLexicon, final int earlierPairs) {
        return new EvalContext(
                IntStream.range(0, earlierPairs)
                        .mapToObj(i -> new Pair(SOURCES.get(i), TARGETS.get(i).replace("⟦g0⟧", "Натаніель")))
                        .toList(),
                SUMMARY_TEXT,
                withLexicon ? List.of(new EvalContext.Rendering("master", "господар")) : List.of(),
                FIRST_PERSON_MALE);
    }

    private static ChatRequest sent(final ScriptedChatModel recording, final int index) {
        return recording.requests().get(index);
    }

    private ScriptedChatModel runBatchedJob(final boolean withLexicon) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, reply(items(TARGETS.subList(0, 4))))
                .answerTo(BATCH, reply(items(TARGETS.subList(4, 8))))
                .answerTo(REVIEW, reply("{\"results\":[]}"));
        final TestProject project = book(withLexicon);

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 4)
                .run());
        return model;
    }

    @Test
    void batchRequest_firstBatchWithKeyTerms_equalsTheRequestTheJobSends() {
        final ScriptedChatModel job = runBatchedJob(true);
        final ScriptedChatModel recording = answeringAnything();
        final EvalProject eval = EvalProject.of(setup(context(true, 0)), evalCalls(recording));
        final PreparedBatch prepared = eval.batch(0, 4).orElseThrow();

        evalCalls(recording).callAbout(CallKind.DRAFT, List.of("a"), eval.batchRequest(prepared));

        assertThat(job.requests().getFirst().messages().get(1).content()).contains("master");
        assertThat(sent(recording, 0)).isEqualTo(sent(job, 0));
    }

    @Test
    void batchRequest_secondBatchWithEarlierPairsAndSummary_equalsTheRequestTheJobSends() {
        final ScriptedChatModel job = runBatchedJob(true);
        final ScriptedChatModel recording = answeringAnything();
        final EvalProject eval = EvalProject.of(setup(context(true, 4)), evalCalls(recording));
        final PreparedBatch prepared = eval.batch(4, 4).orElseThrow();

        evalCalls(recording).callAbout(CallKind.DRAFT, List.of("a"), eval.batchRequest(prepared));

        assertThat(sent(job, 1).messages().get(1).content()).contains(TARGETS.get(3), SUMMARY_TEXT);
        assertThat(sent(recording, 0)).isEqualTo(sent(job, 1));
    }

    @Test
    void batchRequest_noRecurringTerms_equalsTheRequestTheJobSendsAndAsksForNoTerms() {
        final ScriptedChatModel job = runBatchedJob(false);
        final ScriptedChatModel recording = answeringAnything();
        final EvalProject eval = EvalProject.of(setup(context(false, 0)), evalCalls(recording));

        evalCalls(recording)
                .callAbout(
                        CallKind.DRAFT,
                        List.of("a"),
                        eval.batchRequest(eval.batch(0, 4).orElseThrow()));

        assertThat(sent(recording, 0)).isEqualTo(sent(job, 0));
        assertThat(sent(job, 0).messages().get(1).content()).doesNotContain("\"terms\"");
    }

    @Test
    void reviewerRequest_wholeBatchWithTermPairsAndCharacters_equalsTheRequestTheJobSends() {
        final ScriptedChatModel job = runBatchedJob(true);
        final ScriptedChatModel recording = answeringAnything();
        final EvalProject eval = EvalProject.of(setup(context(true, 0)), evalCalls(recording));
        final List<ReviewedPair> pairs = IntStream.range(0, PARAGRAPHS)
                .mapToObj(
                        i -> new ReviewedPair(eval.segment(i).id(), eval.mask(i).maskedText(), TARGETS.get(i)))
                .toList();

        TranslationJobTestSupport.qualityLoop().review(pairs, eval.loop(), ReviewPass.FIRST, evalCalls(recording));

        assertThat(sent(job, 2).messages().get(1).content()).contains("Бартімеус", "господар", "<Pair id=\"s8\">");
        assertThat(sent(recording, 0)).isEqualTo(sent(job, 2));
    }

    private ScriptedChatModel runSingleDraftJobWithAnEchoedLastSegment() {
        final ScriptedChatModel model = new ScriptedChatModel();
        TARGETS.subList(0, PARAGRAPHS - 1).forEach(target -> model.answerTo(DRAFT, draftReply(target)));
        model.answerTo(DRAFT, draftReply(ECHO_OF_LAST))
                .answerTo(REVIEW, reply("{\"results\":[]}"))
                .answerTo(FIX, draftReply(TARGETS.getLast()));
        final TestProject project = book(true);

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 1)
                .run());
        return model;
    }

    @Test
    void draftRequest_singleDraftWithEarlierTargets_equalsTheRequestTheJobSends() {
        final ScriptedChatModel job = runSingleDraftJobWithAnEchoedLastSegment();
        final ScriptedChatModel recording = answeringAnything();
        final EvalProject eval = EvalProject.of(setup(context(true, 3)), evalCalls(recording));

        evalCalls(recording).call(CallKind.DRAFT, "a", eval.draftRequest(3));

        assertThat(sent(job, 3).messages().get(1).content()).contains("господар усміхнувся", "холодний дощ");
        assertThat(sent(recording, 0)).isEqualTo(sent(job, 3));
    }

    @Test
    void fixRequest_echoedDraft_equalsTheRequestTheJobSends() {
        final ScriptedChatModel job = runSingleDraftJobWithAnEchoedLastSegment();
        final ScriptedChatModel recording = answeringAnything();
        final EvalProject eval = EvalProject.of(setup(context(true, 0)), evalCalls(recording));
        final int last = PARAGRAPHS - 1;
        final ProtectedMask mask = eval.mask(last);
        final List<QaFinding> findings = SegmentFindings.concrete(DraftEvaluation.evaluate(
                new DraftOutcome.Drafted(
                        eval.segment(last),
                        mask.maskedText(),
                        mask.presentLocked(),
                        ECHO_OF_LAST,
                        ECHO_OF_LAST,
                        ECHO_OF_LAST,
                        null),
                eval.loop()));

        new DirectedFix(new PromptTemplates(), new DraftReplyParser(MAPPER))
                .fix(eval.segment(last), eval.frame(), mask.maskedText(), ECHO_OF_LAST, findings, evalCalls(recording));

        assertThat(findings).isNotEmpty();
        assertThat(sent(recording, 0)).isEqualTo(sent(job, PARAGRAPHS + 1));
    }
}
