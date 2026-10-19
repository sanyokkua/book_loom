package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;

/**
 * The {@link GlossaryRepository}, {@link TmRepository}, {@link SummaryRepository}, {@link DeferralRepository} and
 * {@link RunRepository} contract, continuing {@link RepositoryContractTest} in its own file by the project's
 * class-length limit (ADR-0034, {@code design.md} D2).
 */
public abstract class GlossaryTmSummaryDeferralRunContractTest extends RepositoryContractTest {

    protected abstract GlossaryRepository glossaryRepository();

    protected abstract TmRepository tmRepository();

    protected abstract SummaryRepository summaryRepository();

    protected abstract DeferralRepository deferralRepository();

    protected abstract RunRepository runRepository();

    // ===== GlossaryRepository =================================================================================

    @Test
    void add_duplicateTermDifferentCase_answersValidationAndKeepsOneEntry() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));

        final Result<GlossaryEntry> duplicate = repository.add(glossaryEntry("e2", "hale"));

        assertThat(duplicate.isErr()).isTrue();
        assertThat(duplicate.error().code()).isEqualTo(ua.bookloom.api.ErrorCode.validation);
        assertThat(repository.all("p1").data()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hale's", "Hale’s", " HALE ", "\"Hale,\""})
    void add_possessiveOrPunctuatedSpelling_answersValidationAsDuplicate(final String spelling) {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));

        final Result<GlossaryEntry> duplicate = repository.add(glossaryEntry("e2", spelling));

        assertThat(duplicate.isErr()).isTrue();
        assertThat(repository.findByTerm("p1", spelling).data()).isPresent();
    }

    @Test
    void wasRemoved_possessiveSpellingOfRemovedTerm_isTrue() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));
        repository.remove("p1", "e1");

        assertThat(repository.wasRemoved("p1", "Hale’s").data()).isTrue();
    }

    @Test
    void findByTerm_matchesCaseInsensitively() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));

        assertThat(repository.findByTerm("p1", "HALE").data())
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.term()).isEqualTo("Hale"));
    }

    @Test
    void remove_thenReAdd_clearsTheRemovalMemory() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));

        repository.remove("p1", "e1");
        assertThat(repository.wasRemoved("p1", "HALE").data()).isTrue();
        assertThat(repository.all("p1").data()).isEmpty();

        repository.add(glossaryEntry("e2", "hale"));
        assertThat(repository.wasRemoved("p1", "HALE").data()).isFalse();
        assertThat(repository.all("p1").data()).hasSize(1);
    }

    @Test
    void removedTerm_ofOneProject_isNotRememberedForAnother() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));
        repository.remove("p1", "e1");

        assertThat(repository.wasRemoved("p2", "hale").data()).isFalse();
    }

    @Test
    void update_existingEntry_replacesItsTarget() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));

        final GlossaryEntry updated =
                new GlossaryEntry("e1", "p1", "Hale", "Гейл 2", TermType.CHARACTER, Gender.MALE, true);
        assertThat(repository.update(updated).isOk()).isTrue();

        assertThat(repository.findByTerm("p1", "hale").data())
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.target()).isEqualTo("Гейл 2"));
    }

    @Test
    void update_suggestedTarget_readsBackAsASuggestion() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));

        repository.update(glossaryEntry("e1", "Hale").withLocked(false).withSuggestedTarget("Гейл"));

        assertThat(repository.findByTerm("p1", "Hale").data())
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.origin()).isEqualTo(TargetOrigin.SUGGESTED));
    }

    @Test
    void updateByTerm_changeSeesAPersonsEarlierEdit_andKeepsIt() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale"));
        repository.update(glossaryEntry("e1", "Hale").withTarget("Гейл"));

        final Result<Optional<GlossaryEntry>> stored =
                repository.update("p1", "hale", held -> held.target() == null ? held.withSuggestedTarget("X") : held);

        assertThat(stored.data())
                .isPresent()
                .hasValueSatisfying(entry -> assertThat(entry.target()).isEqualTo("Гейл"));
    }

    @Test
    void updateByTerm_unknownTerm_answersEmptyAndNeverCallsTheChange() {
        final Result<Optional<GlossaryEntry>> stored = glossaryRepository().update("p1", "Nobody", held -> {
            throw new AssertionError("the change must not run");
        });

        assertThat(stored.data()).isEmpty();
    }

    @Test
    void updateByTerm_manyConcurrentChanges_loseNone() {
        final GlossaryRepository repository = glossaryRepository();
        repository.add(glossaryEntry("e1", "Hale").withTarget(""));
        final int writers = 64;
        java.util.stream.IntStream.range(0, writers)
                .parallel()
                .forEach(i -> repository.update("p1", "Hale", held -> held.withTarget(held.target() + "x")));

        assertThat(repository.findByTerm("p1", "Hale").data())
                .isPresent()
                .hasValueSatisfying(entry -> assertThat(entry.target()).hasSize(writers));
    }

    @Test
    void update_unknownEntry_answersValidation() {
        final Result<GlossaryEntry> updated = glossaryRepository().update(glossaryEntry("unknown", "Hale"));

        assertThat(updated.isErr()).isTrue();
        assertThat(updated.error().code()).isEqualTo(ua.bookloom.api.ErrorCode.validation);
    }

    @Test
    void findByTerm_noMatch_answersEmpty() {
        assertThat(glossaryRepository().findByTerm("p1", "unknown").data()).isEmpty();
    }

    private static GlossaryEntry glossaryEntry(final String id, final String term) {
        return new GlossaryEntry(id, "p1", term, "Гейл", TermType.CHARACTER, Gender.MALE, true);
    }

    // ===== TmRepository ========================================================================================

    @Test
    void put_thenExactAndContext_readBackTheStoredEntries() {
        final TmRepository repository = tmRepository();
        repository.put(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт"));
        repository.put(new TmEntry("t2", "p1", "h1", "c2", "Hello", "Вітаю"));

        assertThat(repository.exact("p1", "h1").data()).hasSize(2);
        assertThat(repository.context("p1", "h1", "c2").data())
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.targetInner()).isEqualTo("Вітаю"));
    }

    @Test
    void put_sameKeyAgain_replacesThePriorEntry() {
        final TmRepository repository = tmRepository();
        repository.put(new TmEntry("t1", "p1", "h1", "c1", "Hello", "Привіт"));

        repository.put(new TmEntry("t1b", "p1", "h1", "c1", "Hello", "Здоров"));

        assertThat(repository.exact("p1", "h1").data()).hasSize(1);
        assertThat(repository.context("p1", "h1", "c1").data())
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.targetInner()).isEqualTo("Здоров"));
    }

    @Test
    void candidates_filtersBySourceLengthBand() {
        final TmRepository repository = tmRepository();
        repository.put(new TmEntry("short", "p1", "h1", "c1", "abc", "xyz"));
        repository.put(new TmEntry("long", "p1", "h2", "c1", "a".repeat(20), "b".repeat(20)));

        assertThat(repository.candidates("p1", 3, 6).data())
                .extracting(TmEntry::id)
                .containsExactly("short");
    }

    @Test
    void candidates_measuresSourceLengthInCodePoints() {
        final TmRepository repository = tmRepository();
        repository.put(new TmEntry("faces", "p1", "h1", "c1", "\uD83D\uDE00".repeat(3), "xyz"));

        assertThat(repository.candidates("p1", 3, 3).data())
                .extracting(TmEntry::id)
                .containsExactly("faces");
        assertThat(repository.candidates("p1", 6, 6).data()).isEmpty();
    }

    @Test
    void exactAndContext_unknownProject_answerEmpty() {
        final TmRepository repository = tmRepository();

        assertThat(repository.exact("unknown", "h1").data()).isEmpty();
        assertThat(repository.context("unknown", "h1", "c1").data()).isEmpty();
    }

    // ===== SummaryRepository ===================================================================================

    @Test
    void save_higherVersion_becomesLatest() {
        final SummaryRepository repository = summaryRepository();
        repository.save(new RollingSummary("p1", null, "src v1", "tgt v1", 1, null, 0));

        repository.save(new RollingSummary("p1", null, "src v2", "tgt v2", 2, null, 0));

        assertThat(repository.latest("p1").data())
                .isPresent()
                .hasValueSatisfying(s -> assertThat(s.version()).isEqualTo(2));
    }

    @Test
    void latest_unknownProject_answersEmpty() {
        assertThat(summaryRepository().latest("unknown").data()).isEmpty();
    }

    // ===== DeferralRepository ===================================================================================

    @Test
    void add_sameSegmentAndReasonTwice_keepsOneOpenDeferral() {
        final DeferralRepository repository = deferralRepository();
        repository.add(new Deferral("d1", "p1", "ch1:4", DeferralReason.GENDER_UNKNOWN, null, null, null, null));

        repository.add(new Deferral("d2", "p1", "ch1:4", DeferralReason.GENDER_UNKNOWN, null, null, null, null));

        assertThat(repository.open("p1").data()).extracting(Deferral::id).containsExactly("d1");
    }

    @Test
    void add_sameSegmentAndReasonDifferentWaitingOn_keepsBothOpenDeferrals() {
        final DeferralRepository repository = deferralRepository();
        repository.add(new Deferral("d1", "p1", "ch1:4", DeferralReason.TERM, "Hale", "Хейл", null, null));

        repository.add(new Deferral("d2", "p1", "ch1:4", DeferralReason.TERM, "Milton", "Мілтон", null, null));
        repository.add(new Deferral("d3", "p1", "ch1:4", DeferralReason.TERM, "Hale", "Хейл", null, null));

        assertThat(repository.open("p1").data()).extracting(Deferral::id).containsExactlyInAnyOrder("d1", "d2");
    }

    @Test
    void resolve_removesTheDeferralFromOpen() {
        final DeferralRepository repository = deferralRepository();
        repository.add(new Deferral("d1", "p1", "ch1:4", DeferralReason.GENDER_UNKNOWN, null, null, null, null));

        assertThat(repository.resolve("p1", "d1").data()).isTrue();
        assertThat(repository.open("p1").data()).isEmpty();
    }

    @Test
    void resolve_unknownDeferralId_answersFalse() {
        assertThat(deferralRepository().resolve("p1", "unknown").data()).isFalse();
    }

    // ===== RunRepository ========================================================================================

    @Test
    void save_upsertedStateSequence_isReadBackAtEachStep() {
        final RunRepository repository = runRepository();
        final Instant started = Instant.parse("2026-01-01T00:00:00Z");

        repository.save(runningRecord("r1", started));
        assertThat(latestState(repository, "p1")).contains(JobState.RUNNING);

        repository.save(pausedRecord("r1", started));
        assertThat(latestState(repository, "p1")).contains(JobState.PAUSED);

        repository.save(runningRecord("r1", started));
        assertThat(latestState(repository, "p1")).contains(JobState.RUNNING);

        repository.save(new RunRecord("r1", "p1", started, started.plusSeconds(60), JobState.COMPLETED, 10, 0));
        assertThat(latestState(repository, "p1")).contains(JobState.COMPLETED);
    }

    @Test
    void save_terminalStates_areReadBackAsStoppedOrFailed() {
        final RunRepository repository = runRepository();
        final Instant started = Instant.parse("2026-01-01T00:00:00Z");

        repository.save(new RunRecord("r1", "p1", started, started.plusSeconds(5), JobState.CANCELLED, 0, 0));
        assertThat(latestState(repository, "p1")).contains(JobState.CANCELLED);

        repository.save(new RunRecord("r2", "p2", started, started.plusSeconds(5), JobState.FAILED, 0, 0));
        assertThat(latestState(repository, "p2")).contains(JobState.FAILED);
    }

    @Test
    void latest_answersTheRunStartedMostRecently() {
        final RunRepository repository = runRepository();
        final Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        final Instant later = Instant.parse("2026-01-02T00:00:00Z");
        repository.save(runningRecord("r1", earlier));

        repository.save(runningRecord("r2", later));

        assertThat(repository.latest("p1").data())
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.runId()).isEqualTo("r2"));
    }

    @Test
    void latest_projectWithNoRuns_answersEmpty() {
        assertThat(runRepository().latest("unknown").data()).isEmpty();
    }

    private static RunRecord runningRecord(final String runId, final Instant startedAt) {
        return new RunRecord(runId, "p1", startedAt, null, JobState.RUNNING, 0, 0);
    }

    private static RunRecord pausedRecord(final String runId, final Instant startedAt) {
        return new RunRecord(runId, "p1", startedAt, null, JobState.PAUSED, 0, 0);
    }

    private static Optional<JobState> latestState(final RunRepository repository, final String projectId) {
        return repository.latest(projectId).data().map(RunRecord::state);
    }
}
