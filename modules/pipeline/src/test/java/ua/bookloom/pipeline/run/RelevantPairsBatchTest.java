package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchFormat;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.PromptRequests.BatchSlot;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * How a batch carries the relevant earlier pairs it is given (15i.T8): sized inside a quarter of the dynamic
 * allowance, dropped before the previous pairs when the window is short, left out when the switch is off. No model
 * is called to build a batch, so the model of these tests fails the test if it is ever asked.
 */
class RelevantPairsBatchTest {

    private static final String PROJECT = "p1";
    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DocumentPort DOCUMENTS =
            Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    private static final TmLookup NO_MEMORY = new TmLookup(null, List.of(), List.of());
    private static final int ROOMY_WINDOW = 16_384;
    private static final GlossaryRepository GLOSSARY =
            Guice.createInjector(new PersistenceModule()).getInstance(GlossaryRepository.class);
    private static final LexiconRepository LEXICON =
            Guice.createInjector(new PersistenceModule()).getInstance(LexiconRepository.class);

    // About 150 estimated source tokens each (600 letters at four a token, with the estimator's margin).
    private static final String LONG_A = "Ann kept the brass key in the drawer by the window. ".repeat(12);
    private static final String LONG_B = "Bea walked the long road to the harbour every morning. ".repeat(11);
    private static final String LONG_A_TARGET = "Енн тримала мідний ключ у шухляді біля вікна. ".repeat(14);
    private static final String LONG_B_TARGET = "Беа щоранку ходила довгою дорогою до гавані. ".repeat(14);

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private static RunSettings settings(final int window, final boolean relevantPairs) {
        return new RunSettings(
                PROJECT,
                ReviewMode.UNATTENDED,
                DialParameters.of(QualityDial.BALANCED),
                FRAME,
                NamePolicy.TRANSLITERATE,
                window,
                relevantPairs);
    }

    private static PromptRequests requests(final RunSettings settings, final List<Segment> chunk) {
        final GateFunction gate = GateFunction.of(DOCUMENTS, BookFormat.TXT);
        final var calls = (ua.bookloom.pipeline.prompt.ModelCalls) (kind, segmentId, request) -> {
            throw new AssertionError("building a batch must not call the model: " + kind);
        };
        final SegmentTranslator translator = new SegmentTranslator(
                gate, calls, BookFormat.TXT, new DraftPromptBuilder(TEMPLATES, FRAME), new DraftReplyParser(MAPPER));
        final BatchDrafter drafter = new BatchDrafter(
                new BatchPromptBuilder(TEMPLATES, FRAME, BatchFormat.JSON),
                new BatchReplyParser(MAPPER),
                calls,
                "en",
                "uk",
                BatchDrafter.DEFAULT_INITIAL_SIZE);
        final Result<PromptRequests> read = PromptRequests.read(
                GLOSSARY, LEXICON, settings, new Chunk("u", chunk, false), gate, translator, drafter);
        return Objects.requireNonNull(read.data(), () -> "chunk context: " + read.error());
    }

    private static List<Segment> segmentsOf(final String unit, final String... lines) {
        return IndexTestBooks.unit(unit, UnitRole.BODY, lines).segments();
    }

    private static List<EarlierPair> pairs(final String unit, final List<String> sources, final List<String> targets) {
        final List<Segment> segments = segmentsOf(unit, sources.toArray(String[]::new));
        return IntStream.range(0, segments.size())
                .mapToObj(i -> new EarlierPair(segments.get(i), targets.get(i)))
                .toList();
    }

    private static Optional<PreparedBatch> prepare(
            final RunSettings settings,
            final List<Segment> unit,
            final List<EarlierPair> earlier,
            final List<EarlierPair> relevant) {
        final PromptRequests requests = requests(settings, unit);
        final List<BatchSlot> slots = unit.stream()
                .map(s -> new BatchSlot(s, requests.mask(s), NO_MEMORY))
                .toList();
        return requests.batch(slots, unit, earlier, relevant, null);
    }

    private static final List<String> SHORT_ITEMS = List.of("Ann told Bea the news.", "Bea nodded slowly.");

    private static List<BatchContext.Pair> relevantOf(final Optional<PreparedBatch> batch) {
        return batch.orElseThrow().context().relevantPairs();
    }

    // IF two relevant pairs are given and both fit, THEN both ride in the batch, best first, as given.
    @Test
    void batch_relevantPairsThatFit_areCarriedBestFirst() {
        final List<Segment> unit = segmentsOf("u", SHORT_ITEMS.toArray(String[]::new));
        final List<EarlierPair> relevant = pairs(
                "old", List.of("Ann met Bea.", "Ann ran home."), List.of("Енн зустріла Беа.", "Енн побігла додому."));

        final Optional<PreparedBatch> batch = prepare(settings(ROOMY_WINDOW, true), unit, List.of(), relevant);

        assertThat(relevantOf(batch))
                .containsExactly(
                        new BatchContext.Pair("Ann met Bea.", "Енн зустріла Беа."),
                        new BatchContext.Pair("Ann ran home.", "Енн побігла додому."));
    }

    // The block has a quarter of the 2000-token allowance, about 500 tokens: one pair of ~400 fits, two do not, and
    // the best pair is the one that stays.
    @Test
    void batch_relevantPairsAboveTheirShare_keepsTheBestOnesThatFit() {
        final List<Segment> unit = segmentsOf("u", SHORT_ITEMS.toArray(String[]::new));
        final List<EarlierPair> relevant = pairs("old", List.of(LONG_A, LONG_B), List.of(LONG_A_TARGET, LONG_B_TARGET));

        final Optional<PreparedBatch> batch = prepare(settings(ROOMY_WINDOW, true), unit, List.of(), relevant);

        assertThat(relevantOf(batch)).hasSize(1);
        assertThat(relevantOf(batch).getFirst().source()).startsWith("Ann kept the brass key");
    }

    // IF the person-visible switch is off, THEN pairs handed to the batch are not shown, and the previous pairs are.
    @Test
    void batch_switchOff_showsNoRelevantPairsButKeepsThePreviousOnes() {
        final List<Segment> unit = segmentsOf("u", SHORT_ITEMS.toArray(String[]::new));
        final List<EarlierPair> earlier = pairs("prev", List.of("It rained."), List.of("Ішов дощ."));
        final List<EarlierPair> relevant = pairs("old", List.of("Ann met Bea."), List.of("Енн зустріла Беа."));

        final Optional<PreparedBatch> batch = prepare(settings(ROOMY_WINDOW, false), unit, earlier, relevant);

        assertThat(relevantOf(batch)).isEmpty();
        assertThat(batch.orElseThrow().context().precedingPairs())
                .containsExactly(new BatchContext.Pair("It rained.", "Ішов дощ."));
    }

    @Test
    void batch_switchOnWithNoRelevantPairs_showsNone() {
        final List<Segment> unit = segmentsOf("u", SHORT_ITEMS.toArray(String[]::new));

        assertThat(relevantOf(prepare(settings(ROOMY_WINDOW, true), unit, List.of(), List.of())))
                .isEmpty();
    }

    // Six long items, two short previous pairs and two related pairs of ~150 tokens each, offered at windows from
    // roomy to short. The relevant block must go before the previous pairs do: somewhere along the way the batch
    // keeps its previous pairs without it, and it is never shown without them.
    private static List<Optional<PreparedBatch>> sweep() {
        final List<Segment> unit = segmentsOf(
                "u",
                IntStream.range(0, 6)
                        .mapToObj(i -> "Item " + i + " " + "the old harbour lay quiet under the grey sky. ".repeat(21))
                        .toArray(String[]::new));
        final List<EarlierPair> earlier = pairs(
                "prev",
                List.of("It rained all day.", "The town slept."),
                List.of("Цілий день ішов дощ.", "Містечко спало."));
        final List<EarlierPair> relevant = pairs(
                "old",
                List.of(LONG_A.substring(0, 500), LONG_B.substring(0, 500)),
                List.of(LONG_A_TARGET.substring(0, 500), LONG_B_TARGET.substring(0, 500)));
        return IntStream.iterate(9_000, window -> window >= 4_800, window -> window - 25)
                .mapToObj(window -> prepare(settings(window, true), unit, earlier, relevant))
                .toList();
    }

    @Test
    void batch_shortWindow_dropsTheRelevantPairsBeforeThePreviousPairs() {
        final List<Optional<PreparedBatch>> sweep = sweep();

        assertThat(sweep.getFirst().orElseThrow().context().relevantPairs()).isNotEmpty();
        assertThat(sweep).filteredOn(Optional::isPresent).map(Optional::get).anySatisfy(batch -> {
            assertThat(batch.shown()).hasSize(6);
            assertThat(batch.context().precedingPairs()).isNotEmpty();
            assertThat(batch.context().relevantPairs()).isEmpty();
        });
        assertThat(sweep).filteredOn(Optional::isPresent).map(Optional::get).noneSatisfy(batch -> {
            assertThat(batch.context().relevantPairs()).isNotEmpty();
            assertThat(batch.context().precedingPairs()).isEmpty();
        });
    }
}
