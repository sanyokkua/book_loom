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
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;

/**
 * Which earlier pairs a batch is shown besides the chapter's previous ones (15i.T8): the accepted decided segments
 * that share the most of the batch's names and recurring terms, found through the book index with no model call. A
 * segment scores one per distinct term; the ties go to the nearer one.
 */
class RelevantPairsTest {

    private static final String PROJECT = "p1";
    private static final List<String> TERMS = List.of("Ann", "Bea");

    // Ordinals 0..5 are earlier; 6 is the batch's first segment; 7 is later in the book.
    private static final List<String> LINES = List.of(
            "Ann met Bea at the gate.", // 0: two terms
            "Cy was alone.", // 1: none
            "Ann sat down.", // 2: one
            "Bea sat down.", // 3: one
            "Ann and Bea walked home.", // 4: two
            "Dot waited.", // 5: none
            "Ann told Bea the news.", // 6: the batch starts here
            "Ann and Bea left together."); // 7: later, two terms

    private SegmentRepository segments;
    private PendingCommit pending;
    private Document document;
    private BookIndex index;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new InMemoryPersistenceModule());
        segments = injector.getInstance(SegmentRepository.class);
        pending = new PendingCommit(injector.getInstance(CheckpointPort.class), PROJECT);
        document = IndexTestBooks.oneUnit(LINES.toArray(String[]::new));
        index = BookIndex.of(document);
    }

    private static String id(final int ordinal) {
        return "body:" + ordinal;
    }

    private static String target(final int ordinal) {
        return "Переклад " + ordinal + ".";
    }

    private static SegmentRecord record(final int ordinal, final SegmentStatus status, final String maskedTarget) {
        return new SegmentRecord(
                PROJECT,
                id(ordinal),
                "body",
                ordinal,
                SegmentKind.PARAGRAPH,
                status,
                maskedTarget,
                maskedTarget,
                null,
                null,
                0.0,
                null,
                new ArrayList<>(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    /** Every earlier segment decided and accepted, but the ones named in {@code others}. */
    private void store(final Map<Integer, SegmentRecord> others) {
        final List<SegmentRecord> all = new ArrayList<>();
        for (int ordinal = 0; ordinal < LINES.size(); ordinal++) {
            all.add(others.getOrDefault(ordinal, record(ordinal, SegmentStatus.ACCEPTED, target(ordinal))));
        }
        segments.saveAll(PROJECT, all);
    }

    private Segment segment(final int ordinal) {
        return document.units().getFirst().segments().get(ordinal);
    }

    private List<EarlierPair> select(final List<String> terms, final int firstOrdinal, final EarlierPair... preceding) {
        final Result<List<EarlierPair>> selected = new RelevantPairs(segments, PROJECT, pending, index, document)
                .select(terms, segment(firstOrdinal), List.of(preceding));
        assertThat(selected.isOk()).as("selection").isTrue();
        return Objects.requireNonNull(selected.data(), "data");
    }

    private static List<String> ids(final List<EarlierPair> pairs) {
        return pairs.stream().map(pair -> pair.segment().id()).toList();
    }

    @Test
    void limit_isTwo() {
        assertThat(RelevantPairs.LIMIT).isEqualTo(2);
    }

    // Segments 0 and 4 hold both names; 4 is the nearer, so it comes first. 2 and 3 hold one name each and lose.
    @Test
    void select_manyAcceptedPairs_keepsTheTwoSharingTheMostBestFirstWithTiesToTheNearer() {
        store(Map.of());

        final List<EarlierPair> chosen = select(TERMS, 6);

        assertThat(ids(chosen)).containsExactly("body:4", "body:0");
        assertThat(chosen.getFirst().segment().masked()).isEqualTo("Ann and Bea walked home.");
        assertThat(chosen.getFirst().target()).isEqualTo("Переклад 4.");
    }

    // IF the best-sharing segment was flagged, THEN it is never shown and the next accepted one takes its place.
    @Test
    void select_flaggedSegmentSharingTheMost_isNeverChosen() {
        store(Map.of(4, record(4, SegmentStatus.FLAGGED, target(4))));

        assertThat(ids(select(TERMS, 6))).containsExactly("body:0", "body:3");
    }

    // A pending segment has no accepted target yet, even when a draft is stored beside it.
    @Test
    void select_pendingSegmentSharingTheMost_isNeverChosen() {
        store(Map.of(4, record(4, SegmentStatus.PENDING, target(4))));

        assertThat(ids(select(TERMS, 6))).containsExactly("body:0", "body:3");
    }

    // IF a segment after the batch's first one holds both names, THEN it is still not an earlier pair.
    @Test
    void select_laterSegment_isNeverChosen() {
        store(Map.of());

        assertThat(ids(select(TERMS, 6))).containsExactly("body:4", "body:0");
    }

    // IF a segment is already among the previous pairs, THEN it is not repeated as a related one.
    @Test
    void select_pairAmongThePrecedingPairs_isNotRepeated() {
        store(Map.of());
        final EarlierPair previous = new EarlierPair(segment(4), target(4));

        assertThat(ids(select(TERMS, 6, previous))).containsExactly("body:0", "body:3");
    }

    @Test
    void select_noTermIsShared_isEmpty() {
        store(Map.of());

        assertThat(select(List.of("Zed"), 6)).isEmpty();
    }

    @Test
    void select_noTerms_isEmpty() {
        store(Map.of());

        assertThat(select(List.of(), 6)).isEmpty();
    }

    @Test
    void select_firstSegmentOfTheBook_isEmpty() {
        store(Map.of());

        assertThat(select(TERMS, 0)).isEmpty();
    }

    // Only one earlier segment holds a name: one pair, not two.
    @Test
    void select_onlyOneSegmentSharesATerm_isThatOnePair() {
        store(Map.of());

        assertThat(ids(select(List.of("Cy"), 6))).containsExactly("body:1");
    }

    // An accepted record whose target is blank has nothing to show.
    @Test
    void select_acceptedWithoutATarget_isSkipped() {
        store(Map.of(4, record(4, SegmentStatus.ACCEPTED, " ")));

        assertThat(ids(select(TERMS, 6))).containsExactly("body:0", "body:3");
    }

    // The person's own edit is the best pair there is, so a revised segment counts as accepted and shows the edit.
    @Test
    void select_personRevisedSegment_isShownWithTheEditedTarget() {
        store(Map.of(
                4,
                record(4, SegmentStatus.REVISED, target(4))
                        .withUserTarget("Редагований переклад.", "Редагований переклад.")));

        final List<EarlierPair> chosen = select(TERMS, 6);

        assertThat(ids(chosen)).containsExactly("body:4", "body:0");
        assertThat(chosen.getFirst().target()).isEqualTo("Редагований переклад.");
    }

    // A segment the run decided in this chunk is held by the pending commit and not yet in the repository.
    @Test
    void select_acceptedAndNotYetStored_isReadFromThePendingCommit() {
        store(Map.of(4, record(4, SegmentStatus.PENDING, "")));
        pending.decided(record(4, SegmentStatus.ACCEPTED, "Щойно прийнятий переклад."), null);

        final List<EarlierPair> chosen = select(TERMS, 6);

        assertThat(ids(chosen)).containsExactly("body:4", "body:0");
        assertThat(chosen.getFirst().target()).isEqualTo("Щойно прийнятий переклад.");
    }

    @Test
    void select_flaggedAndNotYetStored_isNeverChosen() {
        store(Map.of());
        pending.decided(record(4, SegmentStatus.FLAGGED, target(4)), null);

        assertThat(ids(select(TERMS, 6))).containsExactly("body:0", "body:3");
    }
}
