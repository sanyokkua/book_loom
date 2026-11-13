package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/** Which earlier segment a window may show with its target: only what was accepted, kept or edited by the person. */
class KeptTargetsTest {

    private static final String PROJECT = "p1";
    private static final String TARGET = "Він сказав, что прийде.";

    private final Injector injector = Guice.createInjector(new InMemoryPersistenceModule());
    private final SegmentRepository segments = injector.getInstance(SegmentRepository.class);
    private final PendingCommit pending = new PendingCommit(injector.getInstance(CheckpointPort.class), PROJECT);
    private final KeptTargets kept = new KeptTargets(segments, PROJECT, pending);

    private static SegmentRecord record(final SegmentStatus status, final SegmentPath path) {
        return new SegmentRecord(
                PROJECT,
                "ch03:11",
                "ch03",
                11,
                SegmentKind.PARAGRAPH,
                status,
                TARGET,
                TARGET,
                null,
                null,
                1.0,
                null,
                List.of(),
                path,
                false,
                null);
    }

    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, DRAFT, 'Він сказав, что прийде.'",
        "REVISED, DRAFT, 'Він сказав, что прийде.'",
        "ACCEPTED, REPAIRED, 'Він сказав, что прийде.'",
        "FLAGGED, DRAFT, ''",
        "FLAGGED, REPAIRED, ''",
        "PENDING, DRAFT, ''",
        "FLAGGED, USER, 'Він сказав, что прийде.'"
    })
    void accepted_storedRecord_targetOnlyWhenKept(
            final SegmentStatus status, final SegmentPath path, final String expected) {
        segments.saveAll(PROJECT, List.of(record(status, path)));

        final Result<Optional<String>> target = kept.accepted(new ChunkDrafts()).acceptedTarget("ch03:11");

        assertThat(target.isOk()).isTrue();
        assertThat(Objects.requireNonNull(target.data()).orElse("")).isEqualTo(expected);
    }

    @Test
    void accepted_heldNotYetCommitted_readsThePendingDecision() {
        segments.saveAll(PROJECT, List.of(record(SegmentStatus.PENDING, SegmentPath.DRAFT)));
        pending.decided(record(SegmentStatus.ACCEPTED, SegmentPath.DRAFT), null);

        final Result<Optional<String>> target = kept.accepted(new ChunkDrafts()).acceptedTarget("ch03:11");

        assertThat(target.data()).contains(TARGET);
    }

    @Test
    void accepted_unknownSegment_sourceOnly() {
        assertThat(kept.accepted(new ChunkDrafts()).acceptedTarget("ch09:0").data())
                .isEmpty();
    }
}
