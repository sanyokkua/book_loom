package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The review runs one path for every entry (15i.N5): an entry the dial's {@link NamesPlan} reads is read in context
 * ({@code term-reading}, one term per call) and decided by the code from the readings; every other open entry goes
 * to the batched {@code review-terms} call. A
 * recording model answers each call by its response format; the book is invented.
 */
class TermReviewReadingTest {

    private static final String PROJECT = "p1";
    private static final NamePolicy POLICY = NamePolicy.TRANSLITERATE;
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final Pattern LISTED_TERM = Pattern.compile("^- (.+?) — ", Pattern.MULTILINE);
    private static final String READING = "term-reading";
    private static final String REVIEW = "review-terms";
    private static final String HE_NODDED = "Brann met Tove. He nodded.";
    private static final String SHE_LEFT = "Brann smiled. She left.";

    private GlossaryRepository glossary;
    private TermReview review;
    private final List<String> formats = new ArrayList<>();
    private final List<CallKind> kinds = new ArrayList<>();
    private final List<String> users = new ArrayList<>();

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

    private static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry(PROJECT + ":" + term, PROJECT, term, null, type, Gender.UNKNOWN, false);
    }

    /** The lines, each opened by its own day so no two paragraphs read alike. */
    private static List<String> days(final List<String> lines) {
        return IntStream.range(0, lines.size())
                .mapToObj(index -> "Day " + index + ". " + lines.get(index))
                .toList();
    }

    private static List<String> repeated(final String line, final int times) {
        return IntStream.range(0, times).mapToObj(index -> line).toList();
    }

    /** Four "she" after the name and four "he" after another name, alternating: she ×4 for the code, he for a reader. */
    private static BookSlice alternating() {
        return GlossaryTestSegments.of(days(IntStream.range(0, 8)
                .mapToObj(index -> index % 2 == 0 ? SHE_LEFT : HE_NODDED)
                .toList()));
    }

    /** A reading reply that answers windows 1 to 12 alike; the reader drops the numbers a call did not show. */
    private static String readingReply(final String usage, final String kind, final String quote, final String gender) {
        final String windows = IntStream.rangeClosed(1, 12)
                .mapToObj(
                        number -> "{\"window\":%d,\"quote\":\"%s\",\"usage\":\"%s\",\"kind\":\"%s\",\"gender\":\"%s\"}"
                                .formatted(number, quote, usage, kind, gender))
                .collect(Collectors.joining(","));
        return "{\"windows\":[" + windows + "],\"speaking\":false,\"brand\":false}";
    }

    /**
     * A model that answers each call by its response format: a reading with the given reply, a review batch with "a
     * name, other, no gender" for each listed term, and no target suggestions.
     */
    private ModelCalls model(final String reading) {
        return (kind, segmentId, request) -> {
            final String format = formatOf(request);
            kinds.add(kind);
            formats.add(format);
            users.add(request.messages().getLast().content());
            return reply(
                    switch (format) {
                        case READING -> reading;
                        case REVIEW -> reviewReply(request.messages().getLast().content());
                        default -> "{\"suggestions\":[]}";
                    });
        };
    }

    private static String formatOf(final ChatRequest request) {
        return request.responseFormat() == null
                ? ""
                : Objects.requireNonNull(request.responseFormat()).name();
    }

    private static String reviewReply(final String user) {
        final Matcher listed = LISTED_TERM.matcher(user);
        final List<String> verdicts = new ArrayList<>();
        while (listed.find()) {
            verdicts.add("{\"term\":\"" + listed.group(1)
                    + "\",\"verdict\":\"name\",\"type\":\"other\",\"gender\":\"unknown\",\"windows\":[]}");
        }
        return "{\"verdicts\":[" + String.join(",", verdicts) + "]}";
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private List<String> usersOf(final String format) {
        return IntStream.range(0, formats.size())
                .filter(index -> formats.get(index).equals(format))
                .mapToObj(users::get)
                .toList();
    }

    private Map<String, Long> callsByFormat() {
        return formats.stream().collect(Collectors.groupingBy(format -> format, Collectors.counting()));
    }

    // IF the review sent every entry through the batch, THEN Review and the autonomous pass would decide names two
    // ways: at Balanced the name the story writes six times is read in context, one call for its six windows, and the
    // name written once goes to the batch alone.
    @Test
    void review_balanced_readsTheEntriesThePlanReadsAndBatchesTheRest() {
        glossary.add(entry("Brann", TermType.CHARACTER));
        glossary.add(entry("Ostley", TermType.OTHER));
        final BookSlice book = GlossaryTestSegments.of(
                Stream.concat(days(repeated(HE_NODDED, 6)).stream(), Stream.of("Ostley was far away."))
                        .toList());

        review.review(
                PROJECT,
                book,
                FRAME,
                POLICY,
                QualityDial.BALANCED,
                model(readingReply("name", "person", "He nodded", "male")));

        assertThat(callsByFormat()).containsEntry(READING, 1L).containsEntry(REVIEW, 1L);
        assertThat(usersOf(READING))
                .singleElement()
                .asString()
                .contains("Brann — ")
                .doesNotContain("Ostley — ");
        assertThat(usersOf(REVIEW))
                .singleElement()
                .asString()
                .contains("- Ostley — ")
                .doesNotContain("- Brann — ");
        assertThat(kinds).contains(CallKind.TERM_READING, CallKind.REVIEW_TERMS);
    }

    // A pronoun seed made the word a character ("he" after it four times: its neighbour's); the readings' type replaces
    // the seeded one and takes its gender away (15i.N gate: a city and a spell ended as men).
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}/{1} -> {2}")
    @org.junit.jupiter.params.provider.CsvSource({"name,place,PLACE", "title,other,TITLE", "name,thing,TERM"})
    void review_seededCharacterTheReadingsTypeOtherwise_takesTheirTypeAndNoGender(
            final String usage, final String kind, final TermType type) {
        glossary.add(entry("Varnholt", TermType.CHARACTER).withSuggestedGender(Gender.MALE));
        final BookSlice book = GlossaryTestSegments.of(days(repeated("Varnholt stood there. He waited.", 4)));

        review.review(
                PROJECT,
                book,
                FRAME,
                POLICY,
                QualityDial.BALANCED,
                model(readingReply(usage, kind, "Varnholt stood there", "none")));

        assertThat(formats).contains(READING);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::type, GlossaryEntry::gender)
                .containsExactly(tuple("Varnholt", type, Gender.UNKNOWN));
    }

    // The decision is the code's from the readings. No model has passed the coreference gate, so its votes only break
    // ties: with no pronoun the code reads after the name, six verified "he" make the character a man, suggested.
    @Test
    void review_readEntryWithSixVerifiedVotesAndNoTally_endsWithThatGenderSuggested() {
        glossary.add(entry("Brann", TermType.CHARACTER));
        final BookSlice book = GlossaryTestSegments.of(days(repeated(HE_NODDED, 6)));

        review.review(
                PROJECT,
                book,
                FRAME,
                POLICY,
                QualityDial.BALANCED,
                model(readingReply("name", "person", "He nodded", "male")));

        assertThat(glossary.all(PROJECT).data())
                .extracting(
                        GlossaryEntry::term, GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple("Brann", TermType.CHARACTER, Gender.MALE, GenderOrigin.SUGGESTED));
    }

    // Fast reads only a likely character (typed, or with a gender already); an untyped name goes to the batch.
    @Test
    void review_fastAndAnUntypedName_batchesItWithNoReading() {
        glossary.add(entry("Brann", TermType.OTHER));
        final BookSlice book = GlossaryTestSegments.of(days(repeated(HE_NODDED, 6)));

        review.review(
                PROJECT,
                book,
                FRAME,
                POLICY,
                QualityDial.FAST,
                model(readingReply("name", "person", "He nodded", "male")));

        assertThat(formats).doesNotContain(READING);
        assertThat(usersOf(REVIEW)).singleElement().asString().contains("- Brann — ");
    }

    // IF "Mark the spot" kept the word in the glossary, THEN every "mark" would be held as a name: three quoted
    // ordinary-word uses in a book that writes "mark" in lower case two times in five remove it, and nothing is left
    // to batch.
    @Test
    void review_entryReadAsAnOrdinaryWordTheBookAlsoWritesInLowerCase_isRemoved() {
        glossary.add(entry("Mark", TermType.OTHER));
        final BookSlice book = GlossaryTestSegments.of(days(List.of(
                "Mark the spot, said the guard.",
                "Mark the spot, said the guard again.",
                "Mark the spot, said the guard once more.",
                "There was a mark on the wall.",
                "Nobody knew who had made the mark.")));

        review.review(
                PROJECT,
                book,
                FRAME,
                POLICY,
                QualityDial.BALANCED,
                model(readingReply("word", "other", "Mark the spot", "none")));

        assertThat(glossary.all(PROJECT).data()).isEmpty();
        assertThat(formats).doesNotContain(REVIEW);
    }

    // The readings say "he" (after another name) and the code reads "she" ×4 after the name. Votes only break ties
    // for every model today (15i.N4 coref round), so the clear tally stands and no tiebreak call is made; no vote
    // agrees with it, so it is a suggestion (15i.N gate).
    @Test
    void review_readingsAgainstADominantTally_askNoTiebreakAndTheTallyStandsSuggested() {
        glossary.add(entry("Brann", TermType.CHARACTER));

        review.review(
                PROJECT,
                alternating(),
                FRAME,
                POLICY,
                QualityDial.BALANCED,
                model(readingReply("name", "person", "He nodded", "male")));

        assertThat(formats).contains(READING);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple("Brann", Gender.FEMALE, GenderOrigin.SUGGESTED));
    }

    // The person's gender is never re-decided: the same tie costs no call and the person's "male" stays.
    @Test
    void review_personSetGenderInATie_isUntouched() {
        glossary.add(entry("Brann", TermType.CHARACTER).withGender(Gender.MALE));

        review.review(
                PROJECT,
                alternating(),
                FRAME,
                POLICY,
                QualityDial.BALANCED,
                model(readingReply("name", "person", "He nodded", "male")));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(tuple("Brann", Gender.MALE, GenderOrigin.PERSON));
    }
}
