package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.narrator.NarratorInference;
import ua.bookloom.pipeline.narrator.NarratorInference.Verdict;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * The narrator's gender from the run's own work (15i.T5, 15f.A4): when translation has ended and the brief still holds
 * no gender for a first-person narrator, the decided targets of the segments told in the first person are counted
 * ({@link NarratorInference}), and a gender that holds is stored with the origin INFERRED. The segments holding the
 * other gender's forms are left a gender deferral waiting on the narrator, so backward revision re-renders them. A
 * narrator the person set, the brief verified or the run already inferred, and a third-person one, are never replaced; a
 * merely suggested gender is, because the decided targets are stronger evidence than an unverified guess. Nothing here
 * asks a model.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class NarratorStage {

    private final RunStores stores;
    private final RunSinks sinks;
    private final Document document;
    private final RunSettings settings;

    /**
     * Creates the stage of one run.
     *
     * @param stores the non-null places the brief and the decided records are read from and the brief is written to
     * @param sinks the non-null pending decisions, flushed before the count and after the deferrals are held
     * @param document the non-null book being translated, whose source segments say which are told as "I"
     * @param settings the non-null settings, whose frame names the source and target languages
     */
    public NarratorStage(
            final RunStores stores, final RunSinks sinks, final Document document, final RunSettings settings) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.document = Objects.requireNonNull(document, "document");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Counts the narrator's forms and stores the gender they show.
     *
     * @return empty when nothing failed, whether or not a gender was inferred; the storage error of a failed flush,
     *     read or write otherwise
     */
    public Optional<AppError> settle() {
        final String projectId = settings.projectId();
        log.debug("Narrator stage project={}", projectId);
        final Result<Integer> flushed = sinks.pending().flush();
        if (flushed.isErr()) {
            return Optional.of(Objects.requireNonNull(flushed.error(), "error"));
        }
        final Result<Optional<Project>> found = stores.projects().find(projectId);
        if (found.isErr()) {
            return Optional.of(Objects.requireNonNull(found.error(), "error"));
        }
        final Project project = Objects.requireNonNull(found.data(), "found").orElse(null);
        if (project == null || !isOpenToInference(project.brief().narrator())) {
            return Optional.empty();
        }
        return infer(project);
    }

    private static boolean isOpenToInference(final Narrator narrator) {
        final boolean open = narrator.person() != NarratorPerson.THIRD
                && (narrator.gender() == Gender.UNKNOWN || narrator.hasSuggestedGender());
        log.debug(
                "Narrator inference open: {} ({} {} {})",
                open,
                narrator.person(),
                narrator.gender(),
                narrator.genderOrigin());
        return open;
    }

    private Optional<AppError> infer(final Project project) {
        final @Nullable String language = settings.frame().analysisLanguage();
        final Result<List<SegmentRecord>> read = stores.segments().all(project.id());
        if (read.isErr()) {
            return Optional.of(Objects.requireNonNull(read.error(), "error"));
        }
        final FirstPerson told = firstPerson(Objects.requireNonNull(read.data(), "records"), language);
        if (told.ids().size() < NarratorInference.MIN_SEGMENTS || !isFirstPersonBook(project, language)) {
            log.debug(
                    "Narrator inference waits: {} first-person segments",
                    told.ids().size());
            return Optional.empty();
        }
        final Optional<Verdict> verdict =
                NarratorInference.infer(told.targets(), settings.frame().targetLanguage());
        verdict.ifPresent(found -> log.info(
                "Narrator inferred {} from {} forms, {} agreeing, {} segments to re-render",
                found.gender(),
                found.forms(),
                found.agreeing(),
                found.minority().size()));
        return verdict.isEmpty() ? Optional.empty() : store(project, verdict.get(), told);
    }

    // A narrator of unspecified person is first person only when the whole book reads as told by "I".
    private boolean isFirstPersonBook(final Project project, final @Nullable String language) {
        if (project.brief().narrator().person() != NarratorPerson.UNSPECIFIED) {
            return true;
        }
        final boolean first = NarratorDetector.detect(document, language)
                .filter(NarratorHint::hasFirstPersonShare)
                .isPresent();
        log.debug("Narrator person unspecified; the detector reads the book as first person: {}", first);
        return first;
    }

    private Optional<AppError> store(final Project project, final Verdict verdict, final FirstPerson told) {
        final BookBrief brief = project.brief();
        if (brief.narrator().hasSuggestedGender()) {
            log.info(
                    "Narrator inference replaces the suggested {} with {}",
                    brief.narrator().gender(),
                    verdict.gender());
        }
        if (!brief.narrator().evidence().isEmpty()) {
            log.info(
                    "Narrator inference drops {} stored evidence quotes: the run's own count replaces them",
                    brief.narrator().evidence().size());
        }
        final Narrator inferred =
                new Narrator(NarratorPerson.FIRST, verdict.gender(), GenderOrigin.INFERRED, List.of());
        final Result<Project> saved = stores.projects().save(project.withBrief(brief.withNarrator(inferred)));
        if (saved.isErr()) {
            return Optional.of(Objects.requireNonNull(saved.error(), "error"));
        }
        final List<String> minority =
                verdict.minority().stream().map(told.ids()::get).toList();
        sinks.pending().deferred(DeferralRegister.narratorGender(project.id(), minority));
        final Result<Integer> flushed = sinks.pending().flush();
        return flushed.isErr() ? Optional.of(Objects.requireNonNull(flushed.error(), "error")) : Optional.empty();
    }

    /** The first-person segments' ids and decided targets, in book order, side by side. */
    private record FirstPerson(List<String> ids, List<String> targets) {}

    private FirstPerson firstPerson(final List<SegmentRecord> records, final @Nullable String language) {
        log.debug("Narrator first-person scan: {} records, language={}", records.size(), language);
        final Map<String, SegmentRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        final List<String> ids = new ArrayList<>();
        final List<String> targets = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary() || unit.role() != UnitRole.BODY) {
                continue;
            }
            for (final Segment segment : unit.segments()) {
                final SegmentRecord record = byId.get(segment.id());
                final Optional<String> target = record == null || record.status() == SegmentStatus.PENDING
                        ? Optional.empty()
                        : record.effectiveTarget();
                if (segment.kind() == SegmentKind.PARAGRAPH
                        && target.isPresent()
                        && NarratorDetector.isFirstPerson(segment.masked(), language)) {
                    ids.add(segment.id());
                    targets.add(target.get());
                }
            }
        }
        log.debug("Narrator first-person scan found {} segments told as \"I\"", ids.size());
        return new FirstPerson(ids, targets);
    }
}
