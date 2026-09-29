package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.PROJECT;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.decide;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.decideAccepted;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.record;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.segment;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.versions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.TermType;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** When the rolling summary is refreshed on the deterministic path, and what the deterministic text holds. */
class RollingSummaryKeeperTest {

    private static final int TOKEN_LIMIT = 300;
    private static final int GLOSSARY_SIZE = 200;
    private static final int HEADINGS = 30;
    private static final int PARAGRAPHS = 10;

    private SummaryRepository summaries;
    private GlossaryRepository glossary;
    private ScriptedChatModel model;
    private RollingSummaryKeeper keeper;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new PersistenceModule());
        summaries = injector.getInstance(SummaryRepository.class);
        glossary = injector.getInstance(GlossaryRepository.class);
        model = new ScriptedChatModel();
        keeper = keeperFor(QualityDial.BALANCED);
    }

    private RollingSummaryKeeper keeperFor(final QualityDial dial) {
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        return new RollingSummaryKeeper(
                summaries,
                glossary,
                new PromptTemplates(),
                new ObjectMapper(),
                frame,
                DialParameters.of(dial),
                (kind, segmentId, request) -> model.chat(request));
    }

    @Test
    void onDecided_balancedChapterOf45Accepted_refreshesAtTwentyFortyAndUnitEndWithNoModelCall() {
        final List<Result<Optional<RollingSummary>>> decided = decideAccepted(keeper, "ch02.xhtml", 0, 45);
        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch02.xhtml");

        assertThat(versions(decided)).containsExactly(1, 2);
        assertThat(atEnd.data())
                .hasValueSatisfying(summary -> assertThat(summary.version()).isEqualTo(3));
        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(summary -> assertThat(summary.tokensSince()).isEqualTo(5));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void onUnitEnd_chapterOf25Accepted_refreshesAfterTwentiethAndAtItsEnd() {
        final List<Result<Optional<RollingSummary>>> decided = decideAccepted(keeper, "ch01.xhtml", 0, 25);
        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch01.xhtml");

        assertThat(versions(decided)).containsExactly(1);
        assertThat(atEnd.data()).hasValueSatisfying(summary -> {
            assertThat(summary.version()).isEqualTo(2);
            assertThat(summary.tokensSince()).isEqualTo(5);
            assertThat(summary.unitId()).isEqualTo("ch01.xhtml");
            assertThat(summary.lastSummarizedKey()).isEqualTo("ch01.xhtml:24");
        });
    }

    @Test
    void onDecided_firstEighteenOfTheNextChapterAfterAUnitEndRefresh_causeNoRefresh() {
        decideAccepted(keeper, "ch01.xhtml", 0, 25);
        keeper.onUnitEnd("ch01.xhtml");

        final List<Result<Optional<RollingSummary>>> next = decideAccepted(keeper, "ch02.xhtml", 0, 18);

        assertThat(versions(next)).isEmpty();
        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(summary -> assertThat(summary.version()).isEqualTo(2));
    }

    @Test
    void onDecided_flaggedRecords_doNotCountTowardTheRefresh() {
        decideAccepted(keeper, "ch01.xhtml", 0, 19);
        final List<Result<Optional<RollingSummary>>> flagged = IntStream.range(19, 24)
                .mapToObj(order -> decide(keeper, "ch01.xhtml", order, SegmentStatus.FLAGGED))
                .toList();

        final List<Result<Optional<RollingSummary>>> twentieth = decideAccepted(keeper, "ch01.xhtml", 24, 1);

        assertThat(versions(flagged)).isEmpty();
        assertThat(versions(twentieth)).containsExactly(1);
        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(
                        summary -> assertThat(summary.lastSummarizedKey()).isEqualTo("ch01.xhtml:24"));
    }

    @Test
    void latest_beforeAnyRefresh_isEmpty() {
        decideAccepted(keeper, "ch01.xhtml", 0, 19);

        assertThat(summaries.latest(PROJECT).data()).isEmpty();
    }

    @Test
    void onUnitEnd_unitWithNoAcceptedSegment_refreshesNothing() {
        decide(keeper, "ch01.xhtml", 0, SegmentStatus.FLAGGED);

        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch01.xhtml");

        assertThat(atEnd.data()).isEmpty();
        assertThat(summaries.latest(PROJECT).data()).isEmpty();
    }

    @Test
    void onDecided_storedVersionAlreadyHeld_continuesFromTheLatestPlusOne() {
        summaries.save(new RollingSummary(PROJECT, "ch01.xhtml", "old", "", 7, "ch01.xhtml:3", 4));

        final List<Result<Optional<RollingSummary>>> decided = decideAccepted(keeper, "ch02.xhtml", 0, 20);

        assertThat(versions(decided)).containsExactly(8);
    }

    @Test
    void onDecided_entriesAndHeadingsInTheDecidedSource_listsEntriesByFrequencyThenHeadings() {
        glossary.add(new GlossaryEntry("e1", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));
        glossary.add(new GlossaryEntry("e2", PROJECT, "Moreau", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        glossary.add(new GlossaryEntry("e3", PROJECT, "Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false));
        final Segment heading = segment("ch01.xhtml", 0, SegmentKind.HEADING, "Chapter One");
        keeper.onDecided(heading, record(heading, SegmentStatus.ACCEPTED, "Розділ перший"));
        final List<Segment> body = List.of(
                segment("ch01.xhtml", 1, SegmentKind.PARAGRAPH, "Hale met Moreau. A whale swam by."),
                segment("ch01.xhtml", 2, SegmentKind.PARAGRAPH, "Hale left."),
                segment("ch01.xhtml", 3, SegmentKind.PARAGRAPH, "Hale⟦g0⟧ returned."));
        body.forEach(segment -> keeper.onDecided(segment, record(segment, SegmentStatus.ACCEPTED, "Ціль")));
        decideAccepted(keeper, "ch01.xhtml", 4, 16);

        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(summary -> assertThat(summary.source())
                        .isEqualTo("Hale → Гейл (character, male)\nMoreau (character, unknown)\nChapter One"));
    }

    @Test
    void onDecided_termNextToAFootnoteMarker_isCounted() {
        glossary.add(new GlossaryEntry("e1", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false));
        final Segment noted = segment("ch01.xhtml", 0, SegmentKind.PARAGRAPH, "Hale⟦g0⟧1⟦g1⟧ opened the door.");
        keeper.onDecided(noted, record(noted, SegmentStatus.ACCEPTED, "Гейл відчинив двері."));

        decideAccepted(keeper, "ch01.xhtml", 1, 19);

        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(summary -> assertThat(summary.source()).isEqualTo("Hale → Гейл (character, male)"));
    }

    @Test
    void onDecided_entryAddedAfterAnEarlierRefreshWhoseTermWasAlreadyDecided_isCountedFromTheStart() {
        final Segment mentions = segment("ch01.xhtml", 0, SegmentKind.PARAGRAPH, "Milton and Milton again.");
        keeper.onDecided(mentions, record(mentions, SegmentStatus.ACCEPTED, "Мілтон"));
        decideAccepted(keeper, "ch01.xhtml", 1, 19);
        glossary.add(new GlossaryEntry("e1", PROJECT, "Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false));

        decideAccepted(keeper, "ch01.xhtml", 20, 20);

        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(
                        summary -> assertThat(summary.source()).isEqualTo("Milton → Мілтон (place, unknown)"));
    }

    @Test
    void onDecided_glossaryOf200EntriesAnd30Headings_condensesToLimitDroppingEveryHeadingBeforeAnyEntry() {
        glossaryOfTwoHundred();
        headingsThenBodyParagraphs();

        final String source = latestSource();

        assertThat(TokenEstimator.estimate(source, null)).isLessThanOrEqualTo(TOKEN_LIMIT);
        assertThat(source).doesNotContain("Heading");
        assertThat(source).contains("Name009 → Ціль009 (character, male)", "Name199 → Ціль199 (character, male)");
        assertThat(source).doesNotContain("Name000", "Name100");
    }

    private String latestSource() {
        return Objects.requireNonNull(summaries.latest(PROJECT).data(), "data")
                .orElseThrow()
                .source();
    }

    private void glossaryOfTwoHundred() {
        IntStream.range(0, GLOSSARY_SIZE).forEach(index -> {
            final String number = "%03d".formatted(index);
            glossary.add(new GlossaryEntry(
                    "e" + number, PROJECT, "Name" + number, "Ціль" + number, TermType.CHARACTER, Gender.MALE, false));
        });
    }

    /** Entry k occurs in paragraphs 0..(k mod 10), so entries ending in 9 are the most frequent. */
    private void headingsThenBodyParagraphs() {
        IntStream.range(0, HEADINGS).forEach(index -> {
            final Segment heading = segment("ch01.xhtml", index, SegmentKind.HEADING, "Heading %02d".formatted(index));
            keeper.onDecided(heading, record(heading, SegmentStatus.ACCEPTED, "Заголовок"));
        });
        IntStream.range(0, PARAGRAPHS).forEach(paragraph -> {
            final String text = String.join(
                    " ",
                    IntStream.range(0, GLOSSARY_SIZE)
                            .filter(entry -> entry % PARAGRAPHS >= paragraph)
                            .mapToObj("Name%03d"::formatted)
                            .toList());
            final Segment body = segment("ch01.xhtml", HEADINGS + paragraph, SegmentKind.PARAGRAPH, text);
            keeper.onDecided(body, record(body, SegmentStatus.ACCEPTED, "Ціль"));
        });
    }
}
