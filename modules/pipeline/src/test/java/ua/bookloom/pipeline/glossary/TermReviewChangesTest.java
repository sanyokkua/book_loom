package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The exact changes a glossary review reports: which entries went, which were rewritten, and the model's reason. */
class TermReviewChangesTest {

    private static final String PROJECT = "p1";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<Segment> BOOK =
            GlossaryTestSegments.of(List.of("Well, Hale said.", "He knew it well.", "We met Hale today."));
    private static final String VERDICTS = "{\"verdicts\":["
            + "{\"term\":\"Well\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\","
            + "\"evidence\":\"He knew it well\"},"
            + "{\"term\":\"Hale\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"male\"}]}";
    private static final String SUGGESTIONS = "{\"suggestions\":[{\"term\":\"Hale\",\"target\":\"Гейл\"}]}";

    private GlossaryRepository glossary;
    private TermReview review;

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        review = new TermReview(
                new PromptTemplates(),
                new ObjectMapper(),
                glossary,
                new SuggestTargets(new PromptTemplates(), new ObjectMapper()));
    }

    @Test
    void review_removalAndRewrite_listExactlyThoseEntriesWithTheQuotedReason() {
        final GlossaryEntry well = entry("p1:well", "Well", null, TermType.OTHER, Gender.UNKNOWN);
        final GlossaryEntry hale = entry("p1:hale", "Hale", null, TermType.OTHER, Gender.UNKNOWN);
        glossary.add(well);
        glossary.add(hale);
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(VERDICTS)).answer(reply(SUGGESTIONS));

        final EntryChanges<GlossaryEntry> changes = Objects.requireNonNull(review.review(
                                PROJECT,
                                BOOK,
                                FRAME,
                                NamePolicy.TRANSLITERATE,
                                (kind, segment, request) -> model.chat(request))
                        .data())
                .changes();

        assertThat(changes.added()).isEmpty();
        assertThat(changes.removed()).containsExactly(new EntryChanges.Removal<>(well, "He knew it well"));
        assertThat(changes.changed()).hasSize(1);
        assertThat(changes.changed().getFirst().before()).isEqualTo(hale);
        assertThat(changes.changed().getFirst().after().type()).isEqualTo(TermType.CHARACTER);
        assertThat(changes.changed().getFirst().after().target()).isEqualTo("Гейл");
    }

    @Test
    void review_nothingToChange_reportsNoChanges() {
        glossary.add(entry("p1:hale", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE));
        final ModelCalls noCalls = (kind, segment, request) -> reply("{}");

        final EntryChanges<GlossaryEntry> changes = Objects.requireNonNull(
                        review.review(PROJECT, BOOK, FRAME, NamePolicy.TRANSLITERATE, noCalls)
                                .data())
                .changes();

        assertThat(changes.isEmpty()).isTrue();
    }

    private static GlossaryEntry entry(
            final String id,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender) {
        return new GlossaryEntry(id, PROJECT, term, target, type, gender, target != null);
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
