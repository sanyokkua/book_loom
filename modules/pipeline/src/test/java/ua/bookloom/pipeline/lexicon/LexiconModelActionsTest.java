package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
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
        // The twelve frequent words go in two batches of ten at most; a word nobody asked in a batch is ignored.
        final String answer = "{\"terms\":[{\"term\":\"pentacle\",\"keep\":true},{\"term\":\"table\",\"keep\":false}]}";
        model.answer(reply(answer)).answer(reply(answer));

        final EntryChanges<LexiconEntry> changes = Objects.requireNonNull(
                service.scanWithModel(desk.projectId(), model, event -> {}).data());

        assertThat(changes.added()).extracting(LexiconEntry::term).containsExactly("pentacle");
        assertThat(changes.removed()).isEmpty();
        assertThat(changes.changed()).isEmpty();
    }

    // IF a review removed what the person typed, what a draft verified or what the model never mentioned, THEN one
    // answer could undo the list.
    @Test
    void review_modelDropsATerm_removesOnlyThatTermAndSparesTheRest() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "table"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "lamp"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "room").withChosen("кімната"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "wall").seen("стіна"));
        model.answer(reply("{\"terms\":[{\"term\":\"pentacle\",\"keep\":true},{\"term\":\"table\",\"keep\":false}]}"));

        final EntryChanges<LexiconEntry> changes = Objects.requireNonNull(
                service.review(desk.projectId(), model, event -> {}).data());

        assertThat(changes.removed())
                .extracting(removal -> removal.entry().term())
                .containsExactly("table");
        assertThat(changes.added()).isEmpty();
        assertThat(lexicon.all(desk.projectId()).data())
                .extracting(LexiconEntry::term)
                .containsExactlyInAnyOrder("pentacle", "lamp", "room", "wall");
        assertThat(model.requests().getFirst().messages().get(1).content())
                .doesNotContain("- room")
                .doesNotContain("- wall");
    }

    @Test
    void review_unreadableAnswer_removesNothing() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        model.answer(reply("I would drop them all."));

        final Result<EntryChanges<LexiconEntry>> result = service.review(desk.projectId(), model, event -> {});

        assertThat(result.error()).isNotNull();
        assertThat(lexicon.all(desk.projectId()).data()).hasSize(1);
    }

    @Test
    void review_noModelAnswer_removesNothing() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        model.answer(Result.err(ua.bookloom.api.AppError.of(ua.bookloom.api.ErrorCode.unreachable, "Down", "No.")));

        final Result<EntryChanges<LexiconEntry>> result = service.review(desk.projectId(), model, event -> {});

        assertThat(result.error()).isNotNull();
        assertThat(lexicon.all(desk.projectId()).data()).hasSize(1);
    }

    // IF a revert could not put a dropped term back as it was, THEN "Undo" would lose the person's rendering.
    @Test
    void restore_droppedTermWithARendering_comesBackAsItWas() {
        final LexiconEntry before = LexiconEntry.of(desk.projectId(), "table").withChosen("стіл");
        lexicon.put(before);
        lexicon.remove(desk.projectId(), "table");

        final Result<LexiconEntry> restored = service.restore(before);

        assertThat(restored.data()).isEqualTo(before);
        assertThat(lexicon.all(desk.projectId()).data()).containsExactly(before);
    }

    @Test
    void restore_changedTerm_replacesTheNewStateWithTheOldOne() {
        final LexiconEntry before = LexiconEntry.of(desk.projectId(), "table");
        lexicon.put(before.withSuggested("стіл"));

        service.restore(before);

        assertThat(lexicon.all(desk.projectId()).data()).containsExactly(before);
    }

    @Test
    void restore_termTheGlossaryHoldsNow_answersValidation() {
        desk.glossary()
                .add(new GlossaryEntry("g1", desk.projectId(), "table", "стіл", TermType.TERM, Gender.UNKNOWN, false));

        final Result<LexiconEntry> restored = service.restore(LexiconEntry.of(desk.projectId(), "table"));

        assertThat(restored.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
    }
}
