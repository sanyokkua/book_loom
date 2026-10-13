package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Where the lock rule is applied (15i.T4): the review writes a target, and for an entry the rule locks it writes the
 * lock with it — a brand the reading flagged and a name the policy keeps in Latin take their source as target, locked;
 * a name the readings verified is locked only where the target language does not decline names. A recording model
 * answers each call by its response format; the book is invented.
 */
class TermReviewLockTest {

    private static final String PROJECT = "p1";
    private static final BookBrief BRIEF = BookBrief.defaults("en");
    private static final CallFrame EN_UK = new CallFrame("en", "uk", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final CallFrame EN_DE = new CallFrame("en", "de", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final Pattern LISTED_TERM = Pattern.compile("^- (.+?) — ", Pattern.MULTILINE);
    private static final String READING = "term-reading";
    private static final String REVIEW = "review-terms";
    private static final String NO_SUGGESTIONS = "{\"suggestions\":[]}";
    private static final int MENTIONS = 6;

    private GlossaryRepository glossary;
    private TermReview review;
    private final List<String> formats = new ArrayList<>();

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

    // IF the reading says the entry is a brand but the lock waited for the person, THEN the model's paragraph keeps
    // the brand in Latin letters unmasked and the Latin-run check blocks it as an untranslated phrase.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"KESSEL OPTIK", "Orrin & Vale"})
    void review_entryReadAsABrand_getsItsSourceAsTargetAndIsLocked(final String brand) {
        glossary.add(entry(brand, TermType.OTHER));

        review.review(
                PROJECT,
                bookAbout(brand, "Nell bought a lens from %s on her way home."),
                EN_UK,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(readingReply("organisation", false, true), NO_SUGGESTIONS));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::locked, GlossaryEntry::origin)
                .containsExactly(tuple(brand, brand, true, TargetOrigin.PERSON));
    }

    // IF the model's transliteration of a brand were taken, THEN the brand would be written in Cyrillic in some
    // paragraphs and kept in Latin in others; the brand's rendering is its source whatever the model suggests.
    @Test
    void review_brandTheModelTransliterates_stillGetsItsSourceAsLockedTarget() {
        glossary.add(entry("KESSEL OPTIK", TermType.OTHER));

        review.review(
                PROJECT,
                bookAbout("KESSEL OPTIK", "Nell bought a lens from %s on her way home."),
                EN_UK,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(
                        readingReply("organisation", false, true),
                        "{\"suggestions\":[{\"term\":\"KESSEL OPTIK\",\"target\":\"Кессел Оптік\"}]}"));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("KESSEL OPTIK", true));
    }

    // The same name read as an organisation but not as a brand is rendered like any name: guidance, not a lock.
    @Test
    void review_organisationNotReadAsABrand_keepsAnUnlockedSuggestedRendering() {
        glossary.add(entry("Harbour Guild", TermType.OTHER));

        review.review(
                PROJECT,
                bookAbout("Harbour Guild", "The %s met on the quay."),
                EN_UK,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(
                        readingReply("organisation", false, false),
                        "{\"suggestions\":[{\"term\":\"Harbour Guild\",\"target\":\"Гавань Гільдія\"}]}"));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked, GlossaryEntry::origin)
                .containsExactly(tuple("Гавань Гільдія", false, TargetOrigin.SUGGESTED));
    }

    // IF a title the policy keeps in Latin stayed unlocked, THEN the paragraph that holds it would ship only when the
    // Latin-run check let it through; locked, the title is masked and never read by the check.
    @ParameterizedTest(name = "{0} ({1})")
    @CsvSource({"Weekly Courier Digest,TITLE", "Orrin & Vale,OTHER", "Harbour Guild,OTHER"})
    void review_keepOriginalPolicy_nameThatIsNoTermKeepsItsSourceAsLockedTarget(
            final String term, final TermType type) {
        glossary.add(entry(term, type));

        review.review(
                PROJECT,
                bookAbout(term, "On the train he read the latest %s from cover to cover."),
                EN_UK,
                NamePolicy.KEEP_ORIGINAL,
                QualityDial.FAST,
                model(readingReply("organisation", false, false), NO_SUGGESTIONS));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple(term, term, true));
    }

    // A term is a word the book renders, not a name it keeps: the policy does not copy it.
    @Test
    void review_keepOriginalPolicy_termIsNeitherCopiedNorLocked() {
        glossary.add(entry("Amulet", TermType.TERM));

        review.review(
                PROJECT,
                bookAbout("Amulet", "She wore the %s every day."),
                EN_UK,
                NamePolicy.KEEP_ORIGINAL,
                QualityDial.FAST,
                model(
                        readingReply("thing", false, false),
                        "{\"suggestions\":[{\"term\":\"Amulet\",\"target\":\"Амулет\"}]}"));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Амулет", false));
    }

    // IF a name the readings verified were locked where the language declines names, THEN «Венн» could never become
    // «Венна» or «Венну»; where names stay as written (German), the verified name is the rendering and stays so.
    @Test
    void review_verifiedNameWithACheckedRenderingWhereNamesDoNotInflect_isLocked() {
        glossary.add(entry("Venn", TermType.CHARACTER));

        review.review(
                PROJECT,
                bookAbout("Venn", "%s crossed the yard with a lamp."),
                EN_DE,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(
                        readingReply("person", false, false),
                        "{\"suggestions\":[{\"term\":\"Venn\",\"target\":\"Venn\"}]}"));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Venn", true));
    }

    @Test
    void review_verifiedNameWithACheckedRenderingWhereNamesInflect_staysAnUnlockedSuggestion() {
        glossary.add(entry("Venn", TermType.CHARACTER));

        review.review(
                PROJECT,
                bookAbout("Venn", "%s crossed the yard with a lamp."),
                EN_UK,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(
                        readingReply("person", false, false),
                        "{\"suggestions\":[{\"term\":\"Venn\",\"target\":\"Венн\"}]}"));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked, GlossaryEntry::origin)
                .containsExactly(tuple("Венн", false, TargetOrigin.SUGGESTED));
    }

    // A speaking name is translated for its meaning, so no rule locks its rendering.
    @Test
    void review_speakingNameWhereNamesDoNotInflect_staysUnlocked() {
        glossary.add(entry("Venn", TermType.CHARACTER));

        review.review(
                PROJECT,
                bookAbout("Venn", "%s crossed the yard with a lamp."),
                EN_DE,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(
                        readingReply("person", true, false),
                        "{\"suggestions\":[{\"term\":\"Venn\",\"target\":\"Venn\"}]}"));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Venn", false));
    }

    // A person the pronouns decided with no call is as verified as one the readings decided, in a review and in the
    // names pass alike, so where names are not declined the rendering is locked.
    @Test
    void review_personDecidedByPronounsWhereNamesDoNotInflect_isLocked() {
        glossary.add(entry("Wren", TermType.CHARACTER));

        review.review(
                PROJECT,
                GlossaryTestSegments.of(List.of(
                        "Wren sat. She ate.",
                        "Wren ran. She fell.",
                        "Wren woke. She yawned.",
                        "Wren hid. She waited.",
                        "Wren sang. She smiled.")),
                EN_DE,
                NamePolicy.TRANSLITERATE,
                QualityDial.FAST,
                model(
                        readingReply("person", false, false),
                        "{\"suggestions\":[{\"term\":\"Wren\",\"target\":\"Wren\"}]}"));

        assertThat(formats).doesNotContain(READING, REVIEW);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Wren", true));
    }

    // A name only the batch judged (written once, never read in context) is not verified, so it is not locked.
    @Test
    void review_nameNobodyReadInContext_staysUnlockedWhereNamesDoNotInflect() {
        glossary.add(entry("Ostley", TermType.OTHER));

        review.review(
                PROJECT,
                GlossaryTestSegments.of(List.of("Ostley was far away.")),
                EN_DE,
                NamePolicy.TRANSLITERATE,
                QualityDial.BALANCED,
                model(
                        readingReply("person", false, false),
                        "{\"suggestions\":[{\"term\":\"Ostley\",\"target\":\"Ostley\"}]}"));

        assertThat(formats).doesNotContain(READING);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Ostley", false));
    }

    private static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry(PROJECT + ":" + term, PROJECT, term, null, type, Gender.UNKNOWN, false);
    }

    /** The term in {@value #MENTIONS} paragraphs, each opened by its own day so no two read alike. */
    private static BookSlice bookAbout(final String term, final String sentence) {
        return GlossaryTestSegments.of(IntStream.range(0, MENTIONS)
                .mapToObj(index -> "Day " + index + ". " + sentence.formatted(term))
                .toList());
    }

    /** A reading reply that answers windows 1 to 12 alike with a name of the given kind, and sets the two flags. */
    private static String readingReply(final String kind, final boolean speaking, final boolean brand) {
        final String windows = IntStream.rangeClosed(1, 12)
                .mapToObj(number ->
                        "{\"window\":%d,\"quote\":\"\",\"usage\":\"name\",\"kind\":\"%s\",\"gender\":\"none\"}"
                                .formatted(number, kind))
                .collect(Collectors.joining(","));
        return "{\"windows\":[" + windows + "],\"speaking\":" + speaking + ",\"brand\":" + brand + "}";
    }

    /** Answers a reading with {@code reading}, a review batch with "a name, other" for each term, and the rest alike. */
    private ModelCalls model(final String reading, final String suggestions) {
        return (kind, segmentId, request) -> {
            final String format = formatOf(request);
            formats.add(format);
            return reply(
                    switch (format) {
                        case READING -> reading;
                        case REVIEW -> reviewReply(request.messages().getLast().content());
                        default -> suggestions;
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
}
