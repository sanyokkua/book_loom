package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.persistence.PersistenceModule;

/**
 * How the names pass writes what it decided (15i review): a rendering it changes and locks over segments already
 * decided leaves a term deferral, as the person's edit does, and a store that fails half-way puts both tables back as
 * they were. The book is invented ({@link NamesPassFixture}).
 */
class NamesPassStoreTest {

    /** A glossary store that refuses the first add once armed, as a full disk would. */
    static final class FailingGlossary implements GlossaryRepository {

        private final GlossaryRepository delegate =
                Guice.createInjector(new PersistenceModule()).getInstance(GlossaryRepository.class);
        private boolean armed;

        void arm() {
            armed = true;
        }

        @Override
        public Result<GlossaryEntry> add(final GlossaryEntry entry) {
            if (armed) {
                armed = false;
                return Result.err(AppError.of(ErrorCode.internal, "Disk full", "The glossary could not be written."));
            }
            return delegate.add(entry);
        }

        @Override
        public Result<GlossaryEntry> update(final GlossaryEntry entry) {
            return delegate.update(entry);
        }

        @Override
        public Result<Optional<GlossaryEntry>> update(
                final String projectId, final String term, final UnaryOperator<GlossaryEntry> change) {
            return delegate.update(projectId, term, change);
        }

        @Override
        public Result<Boolean> remove(final String projectId, final String entryId) {
            return delegate.remove(projectId, entryId);
        }

        @Override
        public Result<List<GlossaryEntry>> all(final String projectId) {
            return delegate.all(projectId);
        }

        @Override
        public Result<Optional<GlossaryEntry>> findByTerm(final String projectId, final String term) {
            return delegate.findByTerm(projectId, term);
        }

        @Override
        public Result<Boolean> wasRemoved(final String projectId, final String term) {
            return delegate.wasRemoved(projectId, term);
        }
    }

    private final FailingGlossary failing = new FailingGlossary();
    private final NamesPassFixture fixture =
            new NamesPassFixture(binder -> binder.bind(GlossaryRepository.class).toInstance(failing));
    private final NamesPassTestBase base = new NamesPassTestBase() {};

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private void seeded(final String target) {
        fixture.open(NamesPassFixture.brief(target, NamePolicy.TRANSLITERATE));
        fixture.seedOpenNames().seedPersonsEntries().seedTerms();
    }

    // IF the pass wrote its renderings past the glossary service, THEN a name it locks with a new rendering over
    // segments already decided would leave them holding the old one with nothing to revise them.
    @Test
    void run_lockedRenderingChangedOverADecidedSegment_recordsATermDeferral() {
        seeded("de");
        fixture.glossary.update(PROJECT, "Brann", entry -> entry.withSuggestedTarget("Bran"));
        fixture.injector
                .getInstance(SegmentRepository.class)
                .saveAll(PROJECT, List.of(GlossaryServiceFixtures.decided("ch1:0", "Bran ging fort.")));

        final var result = fixture.pass().run(PROJECT, base.model().suggests("Brann", "Brann"), null, event -> {});

        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        assertThat(fixture.injector
                        .getInstance(DeferralRepository.class)
                        .open(PROJECT)
                        .data())
                .extracting(Deferral::reason, Deferral::segmentId, Deferral::waitingOn, Deferral::replacedRendering)
                .containsExactly(tuple(DeferralReason.TERM, "ch1:0", "Brann", "Bran"));
    }

    // IF a failed write stopped half-way, THEN the names would hold some of the pass's changes and the terms none.
    @Test
    void run_storeFailingHalfWay_leavesBothTablesAsTheyWere() {
        seeded("uk");
        final List<GlossaryEntry> glossaryBefore = fixture.glossaryNow();
        final List<LexiconEntry> lexiconBefore = fixture.lexiconNow();
        failing.arm();

        final var result = fixture.pass().run(PROJECT, base.model(), null, event -> {});

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.internal);
        assertThat(fixture.glossaryNow()).containsExactlyInAnyOrderElementsOf(glossaryBefore);
        assertThat(fixture.lexiconNow()).containsExactlyInAnyOrderElementsOf(lexiconBefore);
    }
}
