package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/** What a stored chunk teaches the lexicon: counted once its records are stored, never for what was not kept. */
class TermLearningTest {

    private static final String PROJECT = "p1";
    private static final int FILLER = 12;
    private static final List<String[]> TERM_PAIRS = List.of(
            new String[] {"The master walked on.", "Господар ішов далі."},
            new String[] {"He bowed to the master.", "Він вклонився господарю."},
            new String[] {"The master's boat sank.", "Човен господаря затонув."},
            new String[] {"The master slept.", "Господар спав."});

    private RunStores stores;
    private TermLearning learning;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        stores = new RunStores(
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(CheckpointPort.class),
                injector.getInstance(OpenProjects.class),
                injector.getInstance(RunRepository.class),
                injector.getInstance(GlossaryRepository.class),
                injector.getInstance(TmRepository.class),
                injector.getInstance(SummaryRepository.class),
                injector.getInstance(LexiconRepository.class));
        learning = new TermLearning(stores, PROJECT);
        stores.lexicon().put(LexiconEntry.of(PROJECT, "master"));
    }

    private static Segment segment(final int order, final String source) {
        return new Segment(
                "u:" + order,
                "u",
                order,
                SegmentKind.PARAGRAPH,
                source,
                source,
                Map.of(),
                "h" + order,
                null,
                null,
                new ByteSpanAnchor(0, source.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static SegmentRecord decided(
            final Segment segment, final String target, final SegmentStatus status, final SegmentPath path) {
        return new SegmentRecord(
                PROJECT,
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                status,
                target,
                target,
                null,
                null,
                0.9,
                null,
                List.of(),
                path,
                0,
                false,
                null);
    }

    // Filler segments and the term's own, in book order, each with its decided record.
    private List<Map.Entry<Segment, SegmentRecord>> book(final int termPairs) {
        final List<Map.Entry<Segment, SegmentRecord>> book = new ArrayList<>();
        for (int i = 0; i < FILLER; i++) {
            final Segment segment = segment(book.size(), "It rained.");
            book.add(Map.entry(
                    segment, decided(segment, "Ішов дощ, і він мовчав.", SegmentStatus.ACCEPTED, SegmentPath.DRAFT)));
        }
        for (int i = 0; i < termPairs; i++) {
            final Segment segment = segment(book.size(), TERM_PAIRS.get(i)[0]);
            book.add(Map.entry(
                    segment, decided(segment, TERM_PAIRS.get(i)[1], SegmentStatus.ACCEPTED, SegmentPath.DRAFT)));
        }
        return book;
    }

    private LexiconEntry master() {
        return Objects.requireNonNull(stores.lexicon().all(PROJECT).data()).getFirst();
    }

    private void learnFrom(final List<Map.Entry<Segment, SegmentRecord>> book) {
        book.forEach(pair -> learning.decided(pair.getKey(), pair.getValue()));
        learning.committed();
    }

    @Test
    void committed_threeDecidedPairsOfTheTerm_publishTheLearnedBaseForm() {
        learnFrom(book(3));

        assertThat(master().learned()).isEqualTo(new LexiconEntry.Learned("господар", 3, 3));
        assertThat(master().established()).contains("господар");
    }

    @Test
    void decided_pairsNotYetCommitted_areNotCounted() {
        book(4).forEach(pair -> learning.decided(pair.getKey(), pair.getValue()));

        assertThat(master().learned()).isNull();
    }

    @Test
    void committed_termWithOnlyTwoPairs_learnsNothing() {
        learnFrom(book(2));

        assertThat(master().learned()).isNull();
    }

    @Test
    void committed_personsChoice_overridesTheLearnedRendering() {
        stores.lexicon().update(PROJECT, "master", entry -> entry.withChosen("володар"));

        learnFrom(book(4));

        assertThat(master().learned()).isNotNull();
        assertThat(master().established()).contains("володар");
    }

    @Test
    void committed_termTheGlossaryHolds_isNotTracked() {
        stores.glossary().add(new GlossaryEntry("g1", PROJECT, "master", "пан", TermType.TERM, Gender.UNKNOWN, true));

        learnFrom(book(4));

        assertThat(master().learned()).isNull();
    }

    @Test
    void committed_renderingThatIsAGlossaryName_isNotLearned() {
        stores.glossary()
                .add(new GlossaryEntry("g1", PROJECT, "Hosp", "Господар", TermType.CHARACTER, Gender.UNKNOWN, true));

        learnFrom(book(4));

        assertThat(master().learned()).isNull();
    }

    @Test
    void decidedPairs_flaggedMemoryReusedVerbatimAndEdited_teachNothing() {
        final Segment segment = segment(0, "The master walked on.");
        final String target = "Господар ішов далі.";

        assertThat(TermLearning.pairOf(segment, decided(segment, target, SegmentStatus.FLAGGED, SegmentPath.DRAFT)))
                .isEmpty();
        assertThat(TermLearning.pairOf(segment, decided(segment, target, SegmentStatus.ACCEPTED, SegmentPath.TM_REUSE)))
                .isEmpty();
        assertThat(TermLearning.pairOf(segment, decided(segment, target, SegmentStatus.ACCEPTED, SegmentPath.VERBATIM)))
                .isEmpty();
        assertThat(TermLearning.pairOf(segment, decided(segment, target, SegmentStatus.REVISED, SegmentPath.USER)))
                .isEmpty();
        assertThat(TermLearning.pairOf(segment, decided(segment, target, SegmentStatus.ACCEPTED, SegmentPath.REPAIRED)))
                .isPresent();
    }

    @Test
    void replay_storedDecisions_giveTheSameLearnedRenderingAsLearningChunkByChunk() {
        final List<Map.Entry<Segment, SegmentRecord>> book = book(4);
        final int chunk = 5;
        for (int from = 0; from < book.size(); from += chunk) {
            learnFrom(book.subList(from, Math.min(from + chunk, book.size())));
        }
        final LexiconEntry incremental = master();
        stores.lexicon().update(PROJECT, "master", entry -> entry.withLearned(null));
        stores.segments()
                .saveAll(PROJECT, book.stream().map(Map.Entry::getValue).toList());

        new TermLearning(stores, PROJECT)
                .replay(book.stream().map(Map.Entry::getKey).toList());

        assertThat(master().learned()).isEqualTo(incremental.learned()).isNotNull();
    }

    @Test
    void replay_nothingStoredForTheTerm_withdrawsAStaleLearnedRendering() {
        stores.lexicon().update(PROJECT, "master", entry -> entry.withLearned(new LexiconEntry.Learned("пан", 3, 3)));

        learning.replay(List.of());

        assertThat(master().learned()).isNull();
    }

    // Two terms that both keep company with one word never both claim it: the stronger one holds it.
    @Test
    void committed_twoTermsThatWouldShareARendering_leaveItToOne() {
        stores.lexicon().put(LexiconEntry.of(PROJECT, "lord"));
        final String[][] pairs = {
            {"The master and the lord spoke.", "Господар заговорив."},
            {"The master and the lord sat.", "Господар сів."},
            {"The master and the lord ate.", "Господар їв."},
            {"The master and the lord slept.", "Господар спав."}
        };
        final List<Map.Entry<Segment, SegmentRecord>> book = new ArrayList<>(book(0));
        for (final String[] pair : pairs) {
            final Segment segment = segment(book.size(), pair[0]);
            book.add(Map.entry(segment, decided(segment, pair[1], SegmentStatus.ACCEPTED, SegmentPath.DRAFT)));
        }

        learnFrom(book);

        final List<LexiconEntry> held =
                Objects.requireNonNull(stores.lexicon().all(PROJECT).data());
        assertThat(held)
                .filteredOn(entry -> entry.learned() != null)
                .extracting(LexiconEntry::term)
                .containsExactly("lord");
    }

    // The newspaper "The Times" makes no common word to learn.
    @Test
    void committed_termWrittenOnlyInATitleCasePhrase_isNotLearned() {
        stores.lexicon().put(LexiconEntry.of(PROJECT, "times"));
        final String[][] pairs = {
            {"He read The Times.", "Він читав часи."},
            {"She quoted The Times.", "Вона цитувала часи."},
            {"The Times printed it.", "Часи надрукували це."},
            {"The Times wrote so.", "Часи написали так."}
        };
        final List<Map.Entry<Segment, SegmentRecord>> book = new ArrayList<>(book(0));
        for (final String[] pair : pairs) {
            final Segment segment = segment(book.size(), pair[0]);
            book.add(Map.entry(segment, decided(segment, pair[1], SegmentStatus.ACCEPTED, SegmentPath.DRAFT)));
        }

        learnFrom(book);

        assertThat(Objects.requireNonNull(stores.lexicon().all(PROJECT).data()))
                .filteredOn(entry -> entry.term().equals("times"))
                .allSatisfy(entry -> assertThat(entry.learned()).isNull());
    }
}
