package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;

/**
 * The {@link CheckpointPort} contract, continuing {@link GlossaryTmSummaryDeferralRunContractTest} in its own file
 * by the project's class-length limit — a chunk's segments, TM entries, deferrals and glossary additions become
 * visible all together or not at all (ADR-0034, {@code design.md} D2).
 */
public abstract class CheckpointContractTest extends GlossaryTmSummaryDeferralRunContractTest {

    protected abstract CheckpointPort checkpointPort();

    @Test
    void commit_appliesEveryListTogether_andCountsThem() {
        final SegmentRepository segments = segmentRepository();
        segments.saveAll(
                "p1",
                List.of(
                        pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH),
                        pending("p1", "ch1:1", "ch1", 1, SegmentKind.PARAGRAPH)));
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(accepted("ch1:0", "ch1"), accepted("ch1:1", "ch1")),
                List.of(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт")),
                List.of(),
                List.of(new Deferral("d1", "p1", "ch1:0", DeferralReason.GENDER_UNKNOWN, null, null, null, null)));

        final Result<Integer> result = checkpointPort().commit(commit);

        assertThat(result.data()).isEqualTo(4);
        assertThat(segments.find("p1", "ch1:0").data())
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(SegmentStatus.ACCEPTED));
        assertThat(tmRepository().exact("p1", "h1").data()).hasSize(1);
        assertThat(deferralRepository().open("p1").data())
                .extracting(Deferral::id)
                .containsExactly("d1");
    }

    @Test
    void commit_unknownSegmentId_answersValidationAndAppliesNothing() {
        final SegmentRepository segments = segmentRepository();
        segments.saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(accepted("ch1:0", "ch1"), accepted("ch9:9", "ch9")),
                List.of(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт")),
                List.of(),
                List.of());

        final Result<Integer> result = checkpointPort().commit(commit);

        assertThat(result.isErr()).isTrue();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(segments.find("p1", "ch1:0").data())
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(SegmentStatus.PENDING));
        assertThat(tmRepository().exact("p1", "h1").data()).isEmpty();
    }

    @Test
    void commit_glossaryAdditionOfAnExistingLockedTerm_keepsTheExistingEntry() {
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(new GlossaryEntry("existing", "p1", "hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(),
                List.of(),
                List.of(new GlossaryEntry("proposed", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false)),
                List.of());

        final Result<Integer> result = checkpointPort().commit(commit);

        assertThat(result.data()).isEqualTo(0);
        assertThat(glossary.all("p1").data()).hasSize(1);
        assertThat(glossary.findByTerm("p1", "hale").data())
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.target()).isEqualTo("Гейл"));
    }

    @Test
    void commit_glossaryAdditionOfARemovedTerm_isNotReAdded() {
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(new GlossaryEntry("e1", "p1", "Chapter", null, TermType.TERM, Gender.NEUTER, false));
        glossary.remove("p1", "e1");
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(),
                List.of(),
                List.of(new GlossaryEntry("e2", "p1", "Chapter", null, TermType.TERM, Gender.NEUTER, false)),
                List.of());

        final Result<Integer> result = checkpointPort().commit(commit);

        assertThat(result.data()).isEqualTo(0);
        assertThat(glossary.all("p1").data()).isEmpty();
    }

    @Test
    void commit_glossaryAdditionOfANewTerm_addsItUnlockedWithNoTarget() {
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(),
                List.of(),
                List.of(new GlossaryEntry("e1", "p1", "Milton", null, TermType.CHARACTER, Gender.UNKNOWN, false)),
                List.of());

        final Result<Integer> result = checkpointPort().commit(commit);

        assertThat(result.data()).isEqualTo(1);
        assertThat(glossaryRepository().findByTerm("p1", "milton").data())
                .isPresent()
                .hasValueSatisfying(e -> {
                    assertThat(e.target()).isNull();
                    assertThat(e.locked()).isFalse();
                });
    }

    @Test
    void commit_underConcurrentReads_isNeverObservedHalfApplied() {
        final SegmentRepository segments = segmentRepository();
        segments.saveAll(
                "p1",
                List.of(
                        pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH),
                        pending("p1", "ch1:1", "ch1", 1, SegmentKind.PARAGRAPH)));
        final AtomicBoolean sawOddCount = new AtomicBoolean(false);
        final Runnable reader = () -> {
            for (int i = 0; i < 5000; i++) {
                final SegmentCounts counts =
                        segments.countsByStatus("p1", Set.of()).data();
                if (counts.accepted() % 2 != 0) {
                    sawOddCount.set(true);
                }
            }
        };

        runBothToCompletion(this::commitTwoSegmentsFiveHundredTimes, reader);

        assertThat(sawOddCount.get()).isFalse();
    }

    private void commitTwoSegmentsFiveHundredTimes() {
        for (int i = 0; i < 500; i++) {
            checkpointPort()
                    .commit(new ChunkCommit(
                            "p1",
                            List.of(accepted("ch1:0", "ch1"), accepted("ch1:1", "ch1")),
                            List.of(),
                            List.of(),
                            List.of()));
        }
    }

    private static void runBothToCompletion(final Runnable first, final Runnable second) {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Future<?> firstDone = pool.submit(first);
            final Future<?> secondDone = pool.submit(second);
            firstDone.get(30, TimeUnit.SECONDS);
            secondDone.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    private static SegmentRecord accepted(final String segmentId, final String unitId) {
        return pending("p1", segmentId, unitId, 0, SegmentKind.PARAGRAPH).withStatus(SegmentStatus.ACCEPTED);
    }
}
