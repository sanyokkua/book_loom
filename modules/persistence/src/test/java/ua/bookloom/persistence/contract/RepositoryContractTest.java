package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The {@link ProjectRepository} and {@link SegmentRepository} contract, proved once here and reused unchanged by
 * every adapter (ADR-0034) — the in-memory adapters today, a SQLite adapter later. The glossary, translation-memory,
 * summary, deferral and run repositories' contract continues in {@link
 * GlossaryTmSummaryDeferralRunContractTest}, kept in its own file by the project's class-length limit.
 */
public abstract class RepositoryContractTest {

    protected abstract ProjectRepository projectRepository();

    protected abstract SegmentRepository segmentRepository();

    // ===== ProjectRepository ================================================================================

    @Test
    void save_thenFind_returnsTheSavedProject() {
        final ProjectRepository repository = projectRepository();
        final Project project = project("p1");

        final Result<Project> saved = repository.save(project);
        assertThat(saved.isOk()).isTrue();

        final Result<Optional<Project>> found = repository.find("p1");
        assertThat(found.isOk()).isTrue();
        assertThat(found.data())
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo("p1"));
    }

    @Test
    void find_unknownId_answersOkAndEmpty() {
        final Result<Optional<Project>> found = projectRepository().find("unknown");
        assertThat(found.isOk()).isTrue();
        assertThat(found.data()).isEmpty();
    }

    @Test
    void delete_existingProject_removesItAndAnswersTrue() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("p1"));

        assertThat(repository.delete("p1").data()).isTrue();
        assertThat(repository.find("p1").data()).isEmpty();
    }

    @Test
    void delete_unknownProject_answersFalse() {
        assertThat(projectRepository().delete("unknown").data()).isFalse();
    }

    // ===== SegmentRepository =================================================================================

    @Test
    void firstPending_afterAcceptAndFlag_skipsFlaggedAndMovesToTheNextUnit() {
        final SegmentRepository repository = segmentRepository();
        final Set<SegmentKind> keptFrontmatter = seedFiveSegments(repository);

        assertThat(firstPendingId(repository, keptFrontmatter)).contains("ch1:0");

        repository.update("p1", "ch1:0", r -> r.withStatus(SegmentStatus.ACCEPTED));
        assertThat(firstPendingId(repository, keptFrontmatter)).contains("ch1:1");
        assertThat(repository.countsByStatus("p1", keptFrontmatter).data()).isEqualTo(new SegmentCounts(3, 1, 0, 0, 1));

        repository.update("p1", "ch1:1", r -> r.withStatus(SegmentStatus.FLAGGED));
        assertThat(repository.flagged("p1", keptFrontmatter).data())
                .extracting(SegmentRecord::segmentId)
                .containsExactly("ch1:1");
        assertThat(firstPendingId(repository, keptFrontmatter)).contains("ch2:0");
    }

    @Test
    void firstPending_auxiliarySegments_respectKeptAuxiliaryKinds() {
        final SegmentRepository repository = segmentRepository();
        final Set<SegmentKind> keptFrontmatter = seedFiveSegments(repository);
        final Set<SegmentKind> keptNone = Set.of();
        repository.update("p1", "ch1:0", r -> r.withStatus(SegmentStatus.ACCEPTED));
        repository.update("p1", "ch1:1", r -> r.withStatus(SegmentStatus.ACCEPTED));
        repository.update("p1", "ch2:0", r -> r.withStatus(SegmentStatus.ACCEPTED));

        assertThat(firstPendingId(repository, keptFrontmatter)).contains("aux:title");
        assertThat(firstPendingId(repository, keptFrontmatter)).isNotEqualTo(Optional.of("aux:fm:title"));

        repository.update("p1", "aux:title", r -> r.withStatus(SegmentStatus.ACCEPTED));
        assertThat(firstPendingId(repository, keptNone)).contains("aux:fm:title");

        repository.update("p1", "aux:fm:title", r -> r.withStatus(SegmentStatus.FLAGGED));
        assertThat(repository.flagged("p1", keptFrontmatter).data())
                .extracting(SegmentRecord::segmentId)
                .doesNotContain("aux:fm:title");
        assertThat(repository.countsByStatus("p1", keptFrontmatter).data().sourceKept())
                .isEqualTo(1);
    }

    @Test
    void countsByStatus_countsARevisedSegment() {
        final SegmentRepository repository = segmentRepository();
        repository.saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));

        repository.update("p1", "ch1:0", r -> r.withStatus(SegmentStatus.REVISED));

        assertThat(repository.countsByStatus("p1", Set.of()).data()).isEqualTo(new SegmentCounts(0, 0, 1, 0, 0));
    }

    @Test
    void byUnit_returnsThatUnitsSegmentsInDocumentOrder() {
        final SegmentRepository repository = segmentRepository();
        repository.saveAll(
                "p1",
                List.of(
                        pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH),
                        pending("p1", "ch1:1", "ch1", 1, SegmentKind.PARAGRAPH),
                        pending("p1", "ch2:0", "ch2", 0, SegmentKind.PARAGRAPH)));

        assertThat(repository.byUnit("p1", "ch1").data())
                .extracting(SegmentRecord::segmentId)
                .containsExactly("ch1:0", "ch1:1");
    }

    @Test
    void update_unknownSegmentId_answersValidation() {
        final SegmentRepository repository = segmentRepository();
        repository.saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));

        final Result<SegmentRecord> updated =
                repository.update("p1", "ch9:0", r -> r.withStatus(SegmentStatus.ACCEPTED));

        assertThat(updated.isErr()).isTrue();
        assertThat(updated.error().code()).isEqualTo(ua.bookloom.api.ErrorCode.validation);
    }

    @Test
    void records_ofOneProject_areInvisibleToAnother() {
        final SegmentRepository repository = segmentRepository();
        repository.saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        repository.saveAll("p2", List.of(pending("p2", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));

        assertThat(repository.all("p1").data())
                .extracting(SegmentRecord::projectId)
                .containsOnly("p1");
        assertThat(repository.all("p2").data())
                .extracting(SegmentRecord::projectId)
                .containsOnly("p2");
    }

    @Test
    void update_concurrentIncrements_endsAtTheExpectedTotal() {
        final SegmentRepository repository = segmentRepository();
        repository.saveAll("p1", List.of(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));

        final int threads = 2;
        final int perThread = 1000;
        runInParallel(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                repository.update("p1", "ch1:0", RepositoryContractTest::incrementRepairRounds);
            }
        });

        final SegmentRecord result = repository.find("p1", "ch1:0").data().orElseThrow();
        assertThat(result.repairRounds()).isEqualTo(threads * perThread);
    }

    private static Set<SegmentKind> seedFiveSegments(final SegmentRepository repository) {
        final List<SegmentRecord> seed = List.of(
                pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH),
                pending("p1", "ch1:1", "ch1", 1, SegmentKind.PARAGRAPH),
                pending("p1", "ch2:0", "ch2", 0, SegmentKind.PARAGRAPH),
                pending("p1", "aux:title", "aux", 0, SegmentKind.METADATA_TITLE),
                pending("p1", "aux:fm:title", "aux", 1, SegmentKind.FRONTMATTER_VALUE));
        assertThat(repository.saveAll("p1", seed).data()).isEqualTo(5);
        return Set.of(SegmentKind.FRONTMATTER_VALUE);
    }

    private static SegmentRecord incrementRepairRounds(final SegmentRecord record) {
        return new SegmentRecord(
                record.projectId(),
                record.segmentId(),
                record.unitId(),
                record.ord(),
                record.kind(),
                record.status(),
                record.machineTarget(),
                record.maskedMachineTarget(),
                record.userTarget(),
                record.maskedUserTarget(),
                record.confidence(),
                record.judgeScore(),
                record.findings(),
                record.path(),
                record.repairRounds() + 1,
                record.reviewed(),
                record.context());
    }

    private static Optional<String> firstPendingId(
            final SegmentRepository repository, final Set<SegmentKind> keptAuxiliaryKinds) {
        return repository.firstPending("p1", keptAuxiliaryKinds).data().map(SegmentRecord::segmentId);
    }

    protected static Project project(final String id) {
        return new Project(id, Path.of(id + ".epub"), BookFormat.EPUB, "hash-" + id, BookBrief.defaults("en"));
    }

    protected static SegmentRecord pending(
            final String projectId,
            final String segmentId,
            final String unitId,
            final int ord,
            final SegmentKind kind) {
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                SegmentStatus.PENDING,
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    /**
     * Runs {@code count} copies of {@code task} on their own threads, released together after all have started, and
     * waits for every one to finish — the shared barrier a concurrency test needs to make a race actually likely.
     */
    protected static void runInParallel(final int count, final Runnable task) {
        final ExecutorService pool = Executors.newFixedThreadPool(count);
        final CountDownLatch ready = new CountDownLatch(count);
        final CountDownLatch go = new CountDownLatch(1);
        final List<Future<?>> tasks = new ArrayList<>(count);
        try {
            for (int t = 0; t < count; t++) {
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    await(go);
                    task.run();
                }));
            }
            ready.await();
            go.countDown();
            for (final Future<?> future : tasks) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
