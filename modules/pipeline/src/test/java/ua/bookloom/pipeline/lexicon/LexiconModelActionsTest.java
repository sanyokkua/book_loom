package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.GlossaryModelScans;
import ua.bookloom.pipeline.glossary.PreScan;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.glossary.TermChoice;
import ua.bookloom.pipeline.glossary.TermReview;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The Recurring terms section's two model actions: what the scan adds and what the review may remove. */
class LexiconModelActionsTest {

    private static final String PENTACLE = "The pentacle glowed in the dark room tonight. ";
    private static final String TABLE = "A wooden table stood against the grey wall. ";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private LexiconRepository lexicon;
    private ScriptedChatModel model;
    private LexiconServiceImpl service;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, (PENTACLE + TABLE).repeat(8) + "\n");
        lexicon = Guice.createInjector(new PersistenceModule()).getInstance(LexiconRepository.class);
        model = new ScriptedChatModel();
        final PromptTemplates templates = new PromptTemplates();
        final ObjectMapper mapper = new ObjectMapper();
        final SuggestTargets suggest = new SuggestTargets(templates, mapper);
        final TermReview review = new TermReview(templates, mapper, desk.glossary(), suggest);
        final GlossaryModelScans scans = new GlossaryModelScans(
                desk.projects(),
                desk.openProjects(),
                new PreScan(templates, mapper, desk.glossary(), review, suggest),
                review,
                suggest,
                new TermChoice(templates, mapper),
                Clock.systemUTC());
        service = new LexiconServiceImpl(lexicon, desk.glossary(), desk.projects(), desk.openProjects(), scans);
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    // IF the scan added every frequent word, THEN the list would fill with ordinary words and cost every batch reply.
    @Test
    void scanWithModel_modelKeepsOneWord_addsOnlyThatWord() {
        model.answer(reply("{\"terms\":[{\"term\":\"pentacle\",\"keep\":true},{\"term\":\"table\",\"keep\":false}]}"));

        final List<LexiconEntry> after =
                service.scanWithModel(desk.projectId(), model, event -> {}).data();

        assertThat(after).extracting(LexiconEntry::term).containsExactly("pentacle");
    }

    // IF a review removed a term whose rendering the person chose, THEN their decision would vanish.
    @Test
    void review_modelDropsATerm_removesItButNeverATermThePersonChose() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "table"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "room").withChosen("кімната"));
        model.answer(reply("{\"terms\":[{\"term\":\"pentacle\",\"keep\":true}]}"));

        final List<LexiconEntry> after =
                service.review(desk.projectId(), model, event -> {}).data();

        assertThat(after).extracting(LexiconEntry::term).containsExactlyInAnyOrder("pentacle", "room");
        assertThat(model.requests().getFirst().messages().get(1).content()).doesNotContain("- room");
    }

    @Test
    void review_noModelAnswer_removesNothing() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        model.answer(Result.err(ua.bookloom.api.AppError.of(ua.bookloom.api.ErrorCode.unreachable, "Down", "No.")));

        final Result<List<LexiconEntry>> result = service.review(desk.projectId(), model, event -> {});

        assertThat(result.error()).isNotNull();
        assertThat(lexicon.all(desk.projectId()).data()).hasSize(1);
    }
}
