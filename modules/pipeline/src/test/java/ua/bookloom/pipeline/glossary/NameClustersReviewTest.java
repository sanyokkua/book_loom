package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
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
 * One person, one gender in the name review (15i.N2): the pronoun tally of a person's short and full name is pooled,
 * so the evidence decides (or vetoes a model) where each spelling alone would not; one gender is written to every
 * member of a cluster whose gender is not the person's; a gender the person set fixes the cluster for the others; an
 * ambiguous surname joins nobody.
 */
class NameClustersReviewTest {

    private static final SuggestTargets SUGGEST = new SuggestTargets(new PromptTemplates(), new ObjectMapper());
    private static final NamePolicy POLICY = NamePolicy.TRANSLITERATE;
    private static final String PROJECT = "p1";
    private static final Pattern LISTED_TERM = Pattern.compile("^- (.+?) — ", Pattern.MULTILINE);
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private GlossaryRepository glossary;
    private TermReview review;
    private final List<CallKind> kinds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        review = new TermReview(new PromptTemplates(), new ObjectMapper(), glossary, SUGGEST);
    }

    // Three "she" after each spelling: neither is a strong case (five, nine in ten) alone, six of six pooled is.
    // IF the tally were read per spelling, THEN the model would be asked about both and could end with two genders.
    @Test
    void review_shortAndFullNameEachWithThreeShe_arePooledIntoAStrongCaseDecidedByTheCode() {
        glossary.add(character("Dax"));
        glossary.add(character("Dax Orlo"));
        final BookSlice book = GlossaryTestSegments.of(
                both(repeated("Dax smiled. She left.", 3), repeated("Dax Orlo smiled. She left.", 3)));

        review.review(PROJECT, book, FRAME, POLICY, answering(Map.of()));

        assertThat(kinds).doesNotContain(CallKind.REVIEW_TERMS);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Dax", Gender.FEMALE, GenderOrigin.VERIFIED),
                        tuple("Dax Orlo", Gender.FEMALE, GenderOrigin.VERIFIED));
    }

    // The short name has one "he", the full name three: alone the short name's tally is too thin to veto the model's
    // "female", pooled four of four favour the man. Both end male, as a suggestion.
    // IF the veto were read per spelling, THEN the same man would end with two genders.
    @Test
    void review_pooledTallyFavoursTheMan_overrulesTheModelsFemaleForTheShortNameToo() {
        glossary.add(character("Dax"));
        glossary.add(character("Dax Orlo"));
        final BookSlice book = GlossaryTestSegments.of(
                both(repeated("Dax glared. He left.", 1), repeated("Dax Orlo glared. He left.", 3)));

        review.review(PROJECT, book, FRAME, POLICY, answering(Map.of("Dax", "female", "Dax Orlo", "female")));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Dax", Gender.MALE, GenderOrigin.SUGGESTED),
                        tuple("Dax Orlo", Gender.MALE, GenderOrigin.SUGGESTED));
    }

    // A gender the person set on any member fixes it for the others, as the person's, whatever the book or the model
    // says of them.
    // IF the review gave the full name the model's verified "she", THEN the person's "he" for the short name would be
    // a half of one person.
    @Test
    void review_personSetTheShortNamesGender_theFullNameTakesItAsThePersons() {
        glossary.add(character("Dax").withGender(Gender.MALE));
        glossary.add(character("Dax Orlo"));
        final BookSlice book = GlossaryTestSegments.of(repeated("Dax Orlo smiled. She left.", 2));

        review.review(PROJECT, book, FRAME, POLICY, answering(Map.of("Dax", "male", "Dax Orlo", "female")));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Dax", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Dax Orlo", Gender.MALE, GenderOrigin.PERSON));
    }

    // Nothing the person set is overwritten, so two members the person gave different genders stay as they are.
    @Test
    void review_twoMembersWithTheirOwnPersonSetGenders_areBothLeftAlone() {
        glossary.add(character("Dax").withGender(Gender.MALE));
        glossary.add(character("Dax Orlo").withGender(Gender.FEMALE));
        final BookSlice book = GlossaryTestSegments.of(repeated("Dax Orlo smiled. He left.", 3));

        review.review(PROJECT, book, FRAME, POLICY, answering(Map.of()));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Dax", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Dax Orlo", Gender.FEMALE, GenderOrigin.PERSON));
    }

    // A surname two people share joins neither: the bare surname keeps the gender nobody gave it.
    // IF the surname took a gender from a cluster, THEN a brother and a sister would hand it to a third person.
    @Test
    void review_ambiguousSurname_takesNeitherFullNamesGender() {
        glossary.add(character("Venn"));
        glossary.add(character("Arlo Venn").withGender(Gender.MALE));
        glossary.add(character("Mira Venn").withGender(Gender.FEMALE));
        final BookSlice book = GlossaryTestSegments.of(List.of("Venn nodded.", "Arlo Venn left.", "Mira Venn left."));

        review.review(PROJECT, book, FRAME, POLICY, answering(Map.of()));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Venn", Gender.UNKNOWN, null),
                        tuple("Arlo Venn", Gender.MALE, GenderOrigin.PERSON),
                        tuple("Mira Venn", Gender.FEMALE, GenderOrigin.PERSON));
    }

    private static GlossaryEntry character(final String term) {
        return new GlossaryEntry("p1:" + term, PROJECT, term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static List<String> repeated(final String line, final int times) {
        return IntStream.range(0, times)
                .mapToObj(index -> "Day " + index + ". " + line)
                .toList();
    }

    private static List<String> both(final List<String> first, final List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    // A model that answers a review batch with the given gender for each listed term (unknown for the rest), citing
    // windows 1 and 2, and gives no target suggestions; it records the kinds of the calls it gets.
    private ModelCalls answering(final Map<String, String> genders) {
        return (kind, segmentId, request) -> {
            kinds.add(kind);
            if (kind != CallKind.REVIEW_TERMS) {
                return reply("{\"suggestions\":[]}");
            }
            final Matcher listed =
                    LISTED_TERM.matcher(request.messages().getLast().content());
            final List<String> verdicts = new ArrayList<>();
            while (listed.find()) {
                final String term = listed.group(1);
                verdicts.add("{\"term\":\"" + term + "\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\""
                        + genders.getOrDefault(term, "unknown") + "\",\"windows\":[1,2]}");
            }
            return reply("{\"verdicts\":[" + String.join(",", verdicts) + "]}");
        };
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
