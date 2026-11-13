package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * What the name review does with a gender by where it came from (15i.N0): only a person's gender is fixed, a verified
 * one is corrected by a stronger (verified) opposite verdict, a suggested one by any verified verdict, and an
 * unverified verdict never overwrites a gender somebody or something already verified.
 */
class GenderOriginReviewTest {

    private static final SuggestTargets SUGGEST = new SuggestTargets(new PromptTemplates(), new ObjectMapper());
    private static final NamePolicy POLICY = NamePolicy.TRANSLITERATE;
    private static final String PROJECT = "p1";
    private static final String NO_SUGGESTIONS = "{\"suggestions\":[]}";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private GlossaryRepository glossary;
    private TermReview review;
    private PreScan preScan;

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        review = new TermReview(new PromptTemplates(), new ObjectMapper(), glossary, SUGGEST);
        preScan = new PreScan(new PromptTemplates(), new ObjectMapper(), glossary, review, SUGGEST);
    }

    // The commit rule on its own: which held origin a verdict may replace. A "verified" verdict is one a cited window
    // shows by a pronoun the code counted, or one the code decided itself from a strong pronoun case.
    // IF an unverified verdict replaced a verified gender, THEN a small model's guess would undo what the book proved.
    @ParameterizedTest
    @CsvSource(
            nullValues = "NONE",
            value = {
                // held origin, held gender, verdict gender, verdict verified, expected gender, expected origin
                "NONE,    UNKNOWN, FEMALE,  true,  FEMALE,  BOOK",
                "NONE,    UNKNOWN, FEMALE,  false, FEMALE,  LIKELY",
                "NONE,    UNKNOWN, UNKNOWN, true,  UNKNOWN, NONE",
                "USER,  MALE,    FEMALE,  true,  MALE,    USER",
                "USER,  MALE,    FEMALE,  false, MALE,    USER",
                "USER,  MALE,    MALE,    true,  MALE,    USER",
                "BOOK, MALE,   FEMALE,  true,  FEMALE,  BOOK",
                "BOOK, MALE,   FEMALE,  false, MALE,    BOOK",
                "BOOK, MALE,   MALE,    true,  MALE,    BOOK",
                "BOOK, MALE,   MALE,    false, MALE,    BOOK",
                "LIKELY, MALE,  FEMALE,  true,  FEMALE,  BOOK",
                "LIKELY, MALE,  MALE,    true,  MALE,    BOOK",
                "LIKELY, MALE,  FEMALE,  false, MALE,    LIKELY",
                "LIKELY, MALE,  MALE,    false, MALE,    LIKELY"
            })
    void guessed_heldOriginAndVerdict_decideWhoWins(
            final @Nullable Origin held,
            final Gender heldGender,
            final Gender verdictGender,
            final boolean verified,
            final Gender expectedGender,
            final @Nullable Origin expectedOrigin) {
        final GlossaryEntry now = holding(held, heldGender);
        final Verdict verdict = new Verdict(now, Kind.NAME, TermType.CHARACTER, verdictGender, "", verified);

        final GlossaryEntry after = ReviewCommit.guessed(now, verdict);

        assertThat(after.gender()).isEqualTo(expectedGender);
        assertThat(after.genderOrigin()).isEqualTo(expectedOrigin);
    }

    // 15i.N5: the pass's reading found nothing for a person, and the entry says so.
    @Test
    void guessed_characterReadWithNoEvidence_isMarkedAsNoEvidenceAndStaysUnknown() {
        final GlossaryEntry now = holding(null, Gender.UNKNOWN);
        final Verdict verdict = new Verdict(now, Kind.NAME, TermType.CHARACTER, Gender.UNKNOWN, "", false, true, true);

        final GlossaryEntry after = ReviewCommit.guessed(now, verdict);

        assertThat(after.noGenderEvidence()).isTrue();
        assertThat(after.gender()).isEqualTo(Gender.UNKNOWN);
    }

    // IF a later verdict kept the mark, THEN the table would say "no evidence" beside a gender the book later proved.
    @Test
    void guessed_markedCharacterLaterVerified_losesTheMark() {
        final GlossaryEntry now = holding(null, Gender.UNKNOWN).withNoGenderEvidence();
        final Verdict verdict = new Verdict(now, Kind.NAME, TermType.CHARACTER, Gender.FEMALE, "", true);

        final GlossaryEntry after = ReviewCommit.guessed(now, verdict);

        assertThat(after.noGenderEvidence()).isFalse();
        assertThat(after.gender()).isEqualTo(Gender.FEMALE);
    }

    // A verdict that found nothing and does not report "no evidence" (a batched review's) leaves a mark alone.
    @Test
    void guessed_markedCharacterAndAVerdictWithNothingToSay_keepsTheMark() {
        final GlossaryEntry now = holding(null, Gender.UNKNOWN).withNoGenderEvidence();
        final Verdict verdict = new Verdict(now, Kind.NAME, TermType.CHARACTER, Gender.UNKNOWN, "", false);

        assertThat(ReviewCommit.guessed(now, verdict).noGenderEvidence()).isTrue();
    }

    // A thing or a place takes no gender from the review, whatever the verdict says.
    @Test
    void guessed_nonCharacterEntry_takesNoGenderAndHasNoOrigin() {
        final GlossaryEntry term = GlossaryEntry.of("e1", PROJECT, "Wren", null, TermType.TERM, Gender.UNKNOWN, false);
        final Verdict verdict = new Verdict(term, Kind.NAME, TermType.CHARACTER, Gender.FEMALE, "", true);

        final GlossaryEntry after = ReviewCommit.guessed(term, verdict);

        assertThat(after.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(after.genderOrigin()).isNull();
    }

    // IF the replacement dropped the tried flag, THEN the first-name list could propose a gender for the entry again.
    @Test
    void guessed_suggestedGenderReplacedByAVerifiedVerdict_keepsTheSeedTriedFlag() {
        final GlossaryEntry now = holding(Origin.LIKELY, Gender.MALE);
        final Verdict verdict = new Verdict(now, Kind.NAME, TermType.CHARACTER, Gender.FEMALE, "", true);

        assertThat(ReviewCommit.guessed(now, verdict).genderSeedTried()).isTrue();
    }

    // The review end to end: three characters held male by three different origins, one verified "she" window each.
    // IF the review treated every known non-suggested gender alike, THEN the book's proof could not correct the
    // verified one, or the person's choice would not be safe.
    @Test
    void review_verifiedFemaleVerdict_correctsSuggestedAndVerifiedButNeverAPersonsGender() {
        glossary.add(holding(Origin.LIKELY, Gender.MALE, "p1:hale", "Hale"));
        glossary.add(holding(Origin.BOOK, Gender.MALE, "p1:rook", "Rook"));
        glossary.add(holding(Origin.USER, Gender.MALE, "p1:wren", "Wren"));
        final BookSlice book = GlossaryTestSegments.of(
                List.of("Hale smiled. She left.", "Rook smiled. She left.", "Wren smiled. She left."));
        final String verdicts = "{\"verdicts\":[" + female("Hale") + "," + female("Rook") + "," + female("Wren") + "]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(verdicts)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, book, FRAME, POLICY, QualityDial.FAST, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Hale", Gender.FEMALE, Origin.BOOK),
                        tuple("Rook", Gender.FEMALE, Origin.BOOK),
                        tuple("Wren", Gender.MALE, Origin.USER));
    }

    // IF a verdict the window does not show (a cited window with no "she") replaced a verified gender, THEN a guess
    // would undo a proof; it also never turns the held gender into a suggestion.
    @Test
    void review_unverifiedOppositeVerdict_leavesAVerifiedGenderVerified() {
        glossary.add(holding(Origin.BOOK, Gender.MALE, "p1:rook", "Rook"));
        final BookSlice book = GlossaryTestSegments.of(List.of("Rook stood. He left."));
        final String verdicts = "{\"verdicts\":[" + female("Rook") + "]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(verdicts)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, book, FRAME, POLICY, QualityDial.FAST, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(Gender.MALE, Origin.BOOK));
    }

    // A strong pronoun case is the code's own verified verdict (no model call for a character); it corrects a
    // suggested or a verified gender and still never a person's.
    @Test
    void review_strongPronounCase_correctsSuggestedAndVerifiedButNeverAPersonsGender() {
        glossary.add(holding(Origin.LIKELY, Gender.MALE, "p1:hale", "Hale"));
        glossary.add(holding(Origin.BOOK, Gender.MALE, "p1:rook", "Rook"));
        glossary.add(holding(Origin.USER, Gender.MALE, "p1:wren", "Wren"));
        final List<CallKind> kinds = new ArrayList<>();
        final ModelCalls calls = (kind, segmentId, request) -> {
            kinds.add(kind);
            return reply(NO_SUGGESTIONS);
        };

        review.review(PROJECT, strongBook("Hale", "Rook", "Wren"), FRAME, POLICY, QualityDial.FAST, calls);

        assertThat(kinds).doesNotContain(CallKind.REVIEW_TERMS);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Hale", Gender.FEMALE, Origin.BOOK),
                        tuple("Rook", Gender.FEMALE, Origin.BOOK),
                        tuple("Wren", Gender.MALE, Origin.USER));
    }

    // An untyped entry a first-name seed or a pre-scan marked as a person (a SUGGESTED gender) is still decided by the
    // code from a strong pronoun case; the seed's origin is what makes it person-like.
    @Test
    void review_untypedEntryWithASuggestedGender_isDecidedByTheCodeFromStrongPronouns() {
        glossary.add(holding(Origin.LIKELY, Gender.MALE, "p1:hale", "Hale").withType(TermType.OTHER, Origin.RULE));
        final List<CallKind> kinds = new ArrayList<>();
        final ModelCalls calls = (kind, segmentId, request) -> {
            kinds.add(kind);
            return reply(NO_SUGGESTIONS);
        };

        review.review(PROJECT, strongBook("Hale"), FRAME, POLICY, QualityDial.FAST, calls);

        assertThat(kinds).doesNotContain(CallKind.REVIEW_TERMS);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(TermType.CHARACTER, Gender.FEMALE, Origin.BOOK));
    }

    // The pre-scan's first-sentence guess is a suggestion at best and never the person's: the review's verified answer
    // wins over it, and with no answer it stays SUGGESTED.
    @ParameterizedTest
    @CsvSource({"female,FEMALE,BOOK", "unknown,MALE,LIKELY"})
    void scan_proposalGender_endsVerifiedFromTheReviewOrSuggestedNeverThePersons(
            final String reviewGender, final Gender expected, final Origin expectedOrigin) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"terms\":[{\"term\":\"Justine\",\"type\":\"person\",\"gender\":\"male\"}]}"));
        final String verdict = "{\"verdicts\":[{\"term\":\"Justine\",\"verdict\":\"name\",\"type\":\"person\","
                + "\"gender\":\"" + reviewGender + "\",\"windows\":[1],\"evidence\":\"\"}]}";
        final ModelCalls calls = (kind, segmentId, request) -> switch (kind) {
            case REVIEW_TERMS -> reply(verdict);
            case SUGGEST_TARGETS -> reply("{\"suggestions\":[]}");
            default -> model.chat(request);
        };

        preScan.scan(
                PROJECT,
                GlossaryTestSegments.of(List.of("We met Justine today. She smiled at us.")),
                FRAME,
                POLICY,
                calls);

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple(expected, expectedOrigin));
    }

    private static String female(final String term) {
        return "{\"term\":\"" + term + "\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"female\","
                + "\"windows\":[1]}";
    }

    // Five "she"s after each name: a strong pronoun case, three names in separate paragraphs.
    private static BookSlice strongBook(final String... names) {
        return GlossaryTestSegments.of(Stream.of(names)
                .flatMap(name ->
                        IntStream.range(0, 5).mapToObj(index -> "Day " + index + ". " + name + " smiled. She left."))
                .toList());
    }

    private static GlossaryEntry holding(@Nullable final Origin origin, final Gender gender) {
        return holding(origin, gender, "p1:wren", "Wren");
    }

    // The held entry as each writer leaves it: the person's edit, a verified answer, a suggestion, or nothing.
    private static GlossaryEntry holding(
            @Nullable final Origin origin, final Gender gender, final String id, final String term) {
        final GlossaryEntry unknown =
                GlossaryEntry.of(id, PROJECT, term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
        if (origin == null) {
            return unknown;
        }
        return switch (origin) {
            case USER -> unknown.withGender(gender);
            case BOOK -> unknown.withInferredGender(gender);
            case RULE -> unknown.withGenderFrom(gender, Origin.RULE);
            case LIKELY -> unknown.withSuggestedGender(gender);
        };
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }
}
