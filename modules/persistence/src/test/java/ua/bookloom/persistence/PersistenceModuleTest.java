package ua.bookloom.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Proves the injector really resolves every storage port from {@link PersistenceModule}, each a singleton, and that
 * every adapter shares one underlying store (a segment saved through {@link SegmentRepository} is visible to a
 * {@link CheckpointPort} commit's validation).
 */
class PersistenceModuleTest {

    private final Injector injector = Guice.createInjector(new PersistenceModule());

    @Test
    void injector_resolvesEveryStoragePort() {
        assertThat(injector.getInstance(ProjectRepository.class)).isNotNull();
        assertThat(injector.getInstance(SegmentRepository.class)).isNotNull();
        assertThat(injector.getInstance(GlossaryRepository.class)).isNotNull();
        assertThat(injector.getInstance(TmRepository.class)).isNotNull();
        assertThat(injector.getInstance(SummaryRepository.class)).isNotNull();
        assertThat(injector.getInstance(DeferralRepository.class)).isNotNull();
        assertThat(injector.getInstance(LexiconRepository.class)).isNotNull();
        assertThat(injector.getInstance(RunRepository.class)).isNotNull();
        assertThat(injector.getInstance(CheckpointPort.class)).isNotNull();
    }

    @Test
    void injector_answersTheSameInstanceOnASecondLookup() {
        assertThat(injector.getInstance(SegmentRepository.class))
                .isSameAs(injector.getInstance(SegmentRepository.class));
    }

    @Test
    void segmentSavedThroughSegmentRepository_isVisibleToACheckpointCommit() {
        final SegmentRepository segments = injector.getInstance(SegmentRepository.class);
        final SegmentRecord record = new SegmentRecord(
                "p1",
                "ch1:0",
                "ch1",
                0,
                SegmentKind.PARAGRAPH,
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
        segments.saveAll("p1", List.of(record));

        final CheckpointPort checkpoint = injector.getInstance(CheckpointPort.class);
        final Result<Integer> result = checkpoint.commit(new ChunkCommit(
                "p1", List.of(record.withStatus(SegmentStatus.ACCEPTED)), List.of(), List.of(), List.of()));

        assertThat(result.isOk()).isTrue();
    }
}
