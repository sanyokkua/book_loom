package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.LexiconReplies.reading;
import static ua.bookloom.pipeline.glossary.LexiconReplies.termOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RenderingOrigin;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.glossary.GlossaryModelScans;
import ua.bookloom.pipeline.glossary.LexiconReading;
import ua.bookloom.pipeline.glossary.PreScan;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.glossary.TermChoice;
import ua.bookloom.pipeline.glossary.TermReview;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * The Recurring terms section's model actions read each term in context at the project's dial (15i.N7): at Balanced
 * the model's reading is decided by the code — a term used in one sense is kept with its rendering written as the
 * chosen one, an ordinary word is dropped, a named thing moves to the glossary — and at Fast the one-sentence batch
 * choice still decides keep or drop with no rendering. The book is invented: eight paragraphs of two sentences.
 */
class LexiconReadingActionsTest {

    private static final String PARAGRAPH =
            "The pentacle glowed in the dark room tonight. A wooden table stood against the grey wall.";
    private static final String PENTACLE_KEPT = "пентакль";
    private static final String LEXICON_READING = "lexicon-reading";
    private static final String TERM_CHOICE = "term-choice";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private LexiconRepository lexicon;
    private LexiconServiceImpl service;
    private final List<ChatRequest> requests = new ArrayList<>();
    private final Map<String, String> readings = new java.util.HashMap<>();
    private String unreadable = "";

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, String.join("\n\n", Collections.nCopies(8, PARAGRAPH)) + "\n");
        lexicon = Guice.createInjector(new PersistenceModule()).getInstance(LexiconRepository.class);
        final PromptTemplates templates = new PromptTemplates();
        final ObjectMapper mapper = new ObjectMapper();
        final SuggestTargets suggest = new SuggestTargets(templates, mapper);
        final TermReview review = new TermReview(templates, mapper, desk.glossary(), suggest);
        final TermChoice choice = new TermChoice(templates, mapper);
        final GlossaryModelScans scans = new GlossaryModelScans(
                desk.projects(),
                desk.openProjects(),
                new PreScan(templates, mapper, desk.glossary(), review, suggest),
                review,
                suggest,
                new LexiconReading(templates, mapper, choice),
                Clock.systemUTC());
        service = new LexiconServiceImpl(lexicon, desk.glossary(), desk.projects(), desk.openProjects(), scans);
        useDial(QualityDial.BALANCED);
    }

    private void useDial(final QualityDial dial) {
        final Project stored = Objects.requireNonNull(
                        desk.projects().find(desk.projectId()).data(), "found")
                .orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withDial(dial)));
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private static String formatOf(final ChatRequest request) {
        return request.responseFormat() == null
                ? ""
                : Objects.requireNonNull(request.responseFormat()).name();
    }

    /**
     * A model that answers a reading by the term the prompt marks — with the reply scripted for that term, else "an
     * ordinary word" — and a batch choice by keeping only "pentacle".
     */
    private final ChatModel model = request -> {
        requests.add(request);
        final String user = request.messages().getLast().content();
        if (formatOf(request).equals(TERM_CHOICE)) {
            return reply("{\"terms\":[{\"term\":\"pentacle\",\"keep\":true},{\"term\":\"table\",\"keep\":false}]}");
        }
        final String term = termOf(user);
        if (unreadable.equals(term)) {
            return reply("I would keep it.");
        }
        return reply(readings.getOrDefault(term, reading(term, "word", 1, "")));
    };

    private EntryChanges<LexiconEntry> changes(final Result<EntryChanges<LexiconEntry>> result) {
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    // IF the model scan still sent one batch of keep-or-drop at Balanced, THEN a word of two senses ("table") would
    // be kept as a term with nothing to say which sense: each candidate is read in its own windows.
    @Test
    void scanWithModel_balanced_readsEachCandidateInItsOwnCallAndKeepsTheOneSenseTermWithItsRendering() {
        readings.put("pentacle", reading("pentacle", "term", 1, PENTACLE_KEPT));

        final EntryChanges<LexiconEntry> changes = changes(service.scanWithModel(desk.projectId(), model, event -> {}));

        assertThat(requests).isNotEmpty().allSatisfy(request -> {
            assertThat(formatOf(request)).isEqualTo(LEXICON_READING);
            assertThat(request.callKind()).isEqualTo(CallKind.TERM_READING);
        });
        assertThat(changes.added()).singleElement().satisfies(entry -> {
            assertThat(entry.term()).isEqualTo("pentacle");
            assertThat(entry.chosen()).isEqualTo(PENTACLE_KEPT);
            assertThat(entry.established()).hasValue(PENTACLE_KEPT);
            // A reading writes the model's rendering: a person's typing or file will replace it, never the reverse.
            assertThat(entry.chosenOrigin()).isEqualTo(RenderingOrigin.MODEL);
            assertThat(entry.hasPersonsRendering()).isFalse();
        });
        assertThat(changes.removed()).isEmpty();
    }

    @Test
    void scanWithModel_balancedTermReadAsAnOrdinaryWord_isNotAdded() {
        final EntryChanges<LexiconEntry> changes = changes(service.scanWithModel(desk.projectId(), model, event -> {}));

        assertThat(changes.added()).isEmpty();
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
    }

    @Test
    void scanWithModel_balancedTermOfTwoSenses_isNotAdded() {
        readings.put("table", reading("table", "term", 2, "стіл"));

        service.scanWithModel(desk.projectId(), model, event -> {});

        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
    }

    // promote: a named thing is the glossary's, with the rendering that fits every window as its target; the lexicon
    // never holds it.
    @Test
    void scanWithModel_balancedNamedThing_goesToTheGlossaryAndNotTheLexicon() {
        readings.put("pentacle", reading("pentacle", "name", 1, "Пентакль"));

        final EntryChanges<LexiconEntry> changes = changes(service.scanWithModel(desk.projectId(), model, event -> {}));

        assertThat(changes.added()).isEmpty();
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
        assertThat(desk.glossary().findByTerm(desk.projectId(), "pentacle").data())
                .hasValueSatisfying(entry -> {
                    assertThat(entry.target()).isEqualTo("Пентакль");
                    // The model's rendering is a suggestion in the glossary until a person confirms it.
                    assertThat(entry.origin()).isEqualTo(TargetOrigin.SUGGESTED);
                    assertThat(entry.locked()).isFalse();
                });
    }

    // A term the glossary already holds is no recurring term to read (15i review): the review sends no call about it,
    // and moving it to the glossary only takes it out of the lexicon instead of failing on the duplicate.
    @Test
    void review_termTheGlossaryHolds_isNotAskedAndAPromotionOnlyRemovesIt() {
        desk.glossary()
                .add(new GlossaryEntry(
                        desk.projectId() + ":pentacle",
                        desk.projectId(),
                        "Pentacle",
                        "Пентакль",
                        ua.bookloom.api.project.TermType.TERM,
                        ua.bookloom.api.project.Gender.UNKNOWN,
                        false));
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        readings.put("pentacle", reading("pentacle", "name", 1, "Пентаграма"));

        assertThat(service.review(desk.projectId(), model, event -> {}).isOk()).isTrue();
        assertThat(requests)
                .noneMatch(request -> request.messages().getLast().content().contains("[[pentacle]]"));

        assertThat(service.promote(desk.projectId(), "pentacle").isOk()).isTrue();
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
        assertThat(desk.glossary().all(desk.projectId()).data())
                .singleElement()
                .satisfies(entry -> assertThat(entry.target()).isEqualTo("Пентакль"));
    }

    // An unreadable reading decides nothing: the term is neither added nor does the scan fail.
    @Test
    void scanWithModel_balancedUnreadableReading_addsNothingAndDoesNotFail() {
        unreadable = "pentacle";

        final Result<EntryChanges<LexiconEntry>> result = service.scanWithModel(desk.projectId(), model, event -> {});

        assertThat(result.isOk()).isTrue();
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
    }

    // IF the rendering were the source word, THEN the book would "keep" the English leftover as its rendering: the term
    // is kept (it is a term) but no rendering is written.
    @Test
    void scanWithModel_balancedIdentityRendering_keepsTheTermWithNoRendering() {
        readings.put("pentacle", reading("pentacle", "term", 1, "pentacle"));

        final EntryChanges<LexiconEntry> changes = changes(service.scanWithModel(desk.projectId(), model, event -> {}));

        assertThat(changes.added())
                .singleElement()
                .satisfies(entry -> assertThat(entry.chosen()).isNull());
    }

    // IF a failed reading were read as "drop", THEN an outage would empty the list; nothing is written when any call
    // failed.
    @Test
    void scanWithModel_balancedProviderFailure_returnsTheErrorAndAddsNothing() {
        final ChatModel failing = request -> Result.err(AppError.of(ErrorCode.unreachable, "Down", "No provider."));

        final Result<EntryChanges<LexiconEntry>> result = service.scanWithModel(desk.projectId(), failing, event -> {});

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
    }

    // Fast keeps the one batch of keep-or-drop: no windows, no rendering, and the call is labelled as a term choice.
    @Test
    void scanWithModel_fast_asksTheBatchChoiceAndWritesNoRendering() {
        useDial(QualityDial.FAST);

        final EntryChanges<LexiconEntry> changes = changes(service.scanWithModel(desk.projectId(), model, event -> {}));

        assertThat(requests).isNotEmpty().allSatisfy(request -> {
            assertThat(formatOf(request)).isEqualTo(TERM_CHOICE);
            assertThat(request.callKind()).isEqualTo(CallKind.TERM_CHOICE);
        });
        assertThat(changes.added()).singleElement().satisfies(entry -> {
            assertThat(entry.term()).isEqualTo("pentacle");
            assertThat(entry.chosen()).isNull();
        });
    }

    // The review writes a kept term's rendering onto the entry the lexicon holds.
    @Test
    void review_balancedOneSenseTerm_writesTheRenderingAsChosen() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        readings.put("pentacle", reading("pentacle", "term", 1, PENTACLE_KEPT));

        final EntryChanges<LexiconEntry> changes = changes(service.review(desk.projectId(), model, event -> {}));

        assertThat(changes.changed()).singleElement().satisfies(change -> {
            assertThat(change.before().chosen()).isNull();
            assertThat(change.after().chosen()).isEqualTo(PENTACLE_KEPT);
        });
        assertThat(lexicon.all(desk.projectId()).data())
                .extracting(LexiconEntry::chosen)
                .containsExactly(PENTACLE_KEPT);
    }

    @Test
    void review_balancedOrdinaryWord_removesTheTerm() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "table"));

        final EntryChanges<LexiconEntry> changes = changes(service.review(desk.projectId(), model, event -> {}));

        assertThat(changes.removed())
                .extracting(removal -> removal.entry().term())
                .containsExactly("table");
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
    }

    // promote through the lexicon service: the entry leaves the lexicon and the glossary holds it with the rendering.
    @Test
    void review_balancedNamedThing_movesTheTermToTheGlossary() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        readings.put("pentacle", reading("pentacle", "name", 1, "Пентакль"));

        final EntryChanges<LexiconEntry> changes = changes(service.review(desk.projectId(), model, event -> {}));

        assertThat(changes.removed())
                .extracting(removal -> removal.entry().term())
                .containsExactly("pentacle");
        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
        assertThat(desk.glossary().all(desk.projectId()).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactly(org.assertj.core.api.Assertions.tuple("pentacle", "Пентакль"));
        assertThat(desk.glossary().findByTerm(desk.projectId(), "pentacle").data())
                .hasValueSatisfying(entry -> assertThat(entry.origin()).isEqualTo(TargetOrigin.SUGGESTED));
    }

    // The person promoting a term whose rendering they typed makes it the glossary's own; a reading's stays a
    // suggestion.
    @Test
    void promote_termWithAPersonsRendering_entersTheGlossaryAsThePersons() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle").withChosen("мій пентакль"));

        service.promote(desk.projectId(), "pentacle");

        assertThat(desk.glossary().findByTerm(desk.projectId(), "pentacle").data())
                .hasValueSatisfying(entry -> assertThat(entry.origin()).isEqualTo(TargetOrigin.PERSON));
    }

    @Test
    void promote_termWithAModelsRendering_entersTheGlossaryAsASuggestion() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle").withModelRendering(PENTACLE_KEPT));

        service.promote(desk.projectId(), "pentacle");

        assertThat(desk.glossary().findByTerm(desk.projectId(), "pentacle").data())
                .hasValueSatisfying(entry -> {
                    assertThat(entry.target()).isEqualTo(PENTACLE_KEPT);
                    assertThat(entry.origin()).isEqualTo(TargetOrigin.SUGGESTED);
                });
    }

    // An edit by the person replaces what a reading wrote, and a reading can never replace the person's edit.
    @Test
    void edit_termWithAModelsRendering_becomesThePersons() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle").withModelRendering(PENTACLE_KEPT));

        service.edit(desk.projectId(), "pentacle", "зірка");

        assertThat(lexicon.all(desk.projectId()).data()).singleElement().satisfies(entry -> {
            assertThat(entry.chosen()).isEqualTo("зірка");
            assertThat(entry.hasPersonsRendering()).isTrue();
        });
    }

    // IF the reading overwrote or re-asked a rendering the person typed, THEN one review could undo the list: a term
    // with a rendering is not even put to the model.
    @Test
    void review_termWithAPersonsRendering_isNeitherAskedNorChanged() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle").withChosen("мій пентакль"));
        lexicon.put(LexiconEntry.of(desk.projectId(), "table"));
        readings.put("table", reading("table", "term", 1, "стіл"));

        service.review(desk.projectId(), model, event -> {});

        assertThat(requests)
                .extracting(request -> request.messages().getLast().content())
                .noneMatch(user -> user.contains("[[pentacle]]"));
        assertThat(lexicon.all(desk.projectId()).data())
                .extracting(LexiconEntry::term, LexiconEntry::chosen)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("pentacle", "мій пентакль"),
                        org.assertj.core.api.Assertions.tuple("table", "стіл"));
    }

    @Test
    void review_balancedProviderFailure_changesNothing() {
        lexicon.put(LexiconEntry.of(desk.projectId(), "pentacle"));
        final ChatModel failing = request -> Result.err(AppError.of(ErrorCode.unreachable, "Down", "No provider."));

        final Result<EntryChanges<LexiconEntry>> result = service.review(desk.projectId(), failing, event -> {});

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(lexicon.all(desk.projectId()).data())
                .extracting(LexiconEntry::term, LexiconEntry::chosen)
                .containsExactly(org.assertj.core.api.Assertions.tuple("pentacle", null));
    }
}
