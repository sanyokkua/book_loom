package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.glossary.GlossaryModelScans;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The service changes an entry as it stands, never from a snapshot read earlier: a run's job thread records counts
 * and the person edits or removes entries at the same time. {@link Interleaved} lets that other writer act in the
 * window between the service's read and its write.
 */
class LexiconServiceImplTest {

    private static final String PROJECT = "p1";

    private final Injector injector = Guice.createInjector(
            new DocumentModule(), new PersistenceModule(), new PipelineModule(), new ReviewModeTestModule());
    private final LexiconRepository real = injector.getInstance(LexiconRepository.class);
    private final Interleaved interleaved = new Interleaved(real);
    private final LexiconServiceImpl service = new LexiconServiceImpl(
            interleaved,
            injector.getInstance(GlossaryRepository.class),
            injector.getInstance(ProjectRepository.class),
            injector.getInstance(OpenProjects.class),
            injector.getInstance(GlossaryModelScans.class));

    /** Runs {@code other} just before the service's write reaches the repository, once. */
    private static final class Interleaved implements LexiconRepository {

        private final LexiconRepository inner;
        private Runnable other = () -> {};

        Interleaved(final LexiconRepository inner) {
            this.inner = inner;
        }

        void before(final Runnable action) {
            other = action;
        }

        private void interleave() {
            final Runnable action = other;
            other = () -> {};
            action.run();
        }

        @Override
        public Result<List<LexiconEntry>> all(final String projectId) {
            return inner.all(projectId);
        }

        @Override
        public Result<LexiconEntry> put(final LexiconEntry entry) {
            interleave();
            return inner.put(entry);
        }

        @Override
        public Result<Optional<LexiconEntry>> update(
                final String projectId, final String term, final UnaryOperator<LexiconEntry> change) {
            interleave();
            return inner.update(projectId, term, change);
        }

        @Override
        public Result<LexiconEntry> record(final String projectId, final String term, final String rendering) {
            return inner.record(projectId, term, rendering);
        }

        @Override
        public Result<Boolean> remove(final String projectId, final String term) {
            return inner.remove(projectId, term);
        }
    }

    private List<LexiconEntry> held() {
        return Objects.requireNonNull(real.all(PROJECT).data());
    }

    @Test
    void edit_countRecordedWhileEditing_keepsTheCountAndSetsTheChoice() {
        real.record(PROJECT, "master", "господар");
        interleaved.before(() -> real.record(PROJECT, "master", "господар"));

        final Result<LexiconEntry> edited = service.edit(PROJECT, "master", "пан");

        assertThat(edited.isOk()).isTrue();
        assertThat(held().getFirst().chosen()).isEqualTo("пан");
        assertThat(held().getFirst().renderings()).containsExactly(new LexiconEntry.Rendering("господар", 2));
    }

    @Test
    void edit_entryRemovedWhileEditing_isUnknownAndNotCreatedAgain() {
        real.record(PROJECT, "master", "господар");
        interleaved.before(() -> real.remove(PROJECT, "master"));

        final Result<LexiconEntry> edited = service.edit(PROJECT, "master", "пан");

        assertThat(Objects.requireNonNull(edited.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(held()).isEmpty();
    }

    @Test
    void edit_unknownTerm_isAValidationError() {
        final Result<LexiconEntry> edited = service.edit(PROJECT, "nothing", "пан");

        assertThat(Objects.requireNonNull(edited.error()).code()).isEqualTo(ErrorCode.validation);
    }
}
