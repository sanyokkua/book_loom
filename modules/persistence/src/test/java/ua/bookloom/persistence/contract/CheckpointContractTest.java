package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RunRecord;
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

    private static final Instant RUN_START = Instant.parse("2026-10-21T08:00:00Z");

    protected abstract CheckpointPort checkpointPort();

    @Test
    void commit_appliesEveryListTogether_andCountsThem() {
        givenProjects("p1");
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
    void commit_twoTermDeferralsOnOneSegment_keepsBothOpenAndRepeatingLeavesTwo() {
        givenProjects("p1");
        segmentRepository().saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(accepted("ch1:0", "ch1")),
                List.of(),
                List.of(),
                List.of(
                        new Deferral("d1", "p1", "ch1:0", DeferralReason.TERM, "Hale", "Хейл", null, null),
                        new Deferral("d2", "p1", "ch1:0", DeferralReason.TERM, "Milton", "Мілтон", null, null)));

        checkpointPort().commit(commit);
        assertThat(deferralRepository().open("p1").data())
                .extracting(Deferral::id)
                .containsExactlyInAnyOrder("d1", "d2");

        checkpointPort().commit(commit);
        assertThat(deferralRepository().open("p1").data()).hasSize(2);
    }

    @Test
    void commit_unknownSegmentId_answersValidationAndAppliesNothing() {
        givenProjects("p1");
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
        givenProjects("p1");
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
        givenProjects("p1");
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
        givenProjects("p1");
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
    void commit_carryingARun_storesItAsTheLatestRun() {
        givenProjects("p1");
        segmentRepository().saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        final RunRecord run = new RunRecord("r1", "p1", RUN_START, null, JobState.RUNNING, 1, 0);

        final Result<Integer> result = checkpointPort()
                .commit(new ChunkCommit("p1", List.of(accepted("ch1:0", "ch1")), List.of(), List.of(), List.of(), run));

        assertThat(result.data()).isEqualTo(1);
        assertThat(runRepository().latest("p1").data()).hasValue(run);
    }

    @Test
    void commit_carryingARunStartedEarlier_keepsTheLaterStoredRun() {
        givenProjects("p1");
        final RunRecord later = new RunRecord("r2", "p1", RUN_START.plusSeconds(60), null, JobState.RUNNING, 0, 0);
        runRepository().save(later);
        final RunRecord earlier = new RunRecord("r1", "p1", RUN_START, null, JobState.RUNNING, 3, 0);

        checkpointPort().commit(new ChunkCommit("p1", List.of(), List.of(), List.of(), List.of(), earlier));

        assertThat(runRepository().latest("p1").data()).hasValue(later);
    }

    @Test
    void commit_carryingAnotherRunStartedAtTheSameMoment_replacesTheStoredRun() {
        givenProjects("p1");
        final RunRecord first = new RunRecord("r2", "p1", RUN_START, null, JobState.RUNNING, 0, 0);
        final RunRecord second = new RunRecord("r1", "p1", RUN_START, null, JobState.RUNNING, 0, 0);

        checkpointPort().commit(new ChunkCommit("p1", List.of(), List.of(), List.of(), List.of(), first));
        checkpointPort().commit(new ChunkCommit("p1", List.of(), List.of(), List.of(), List.of(), second));

        assertThat(runRepository().latest("p1").data()).hasValue(second);
    }

    @Test
    void commit_underConcurrentReads_isNeverObservedHalfApplied() {
        givenProjects("p1");
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
