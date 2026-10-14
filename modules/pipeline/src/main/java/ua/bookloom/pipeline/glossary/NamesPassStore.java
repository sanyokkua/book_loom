package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * Writes what the names pass decided into the stored tables (15i.N8, 15i review): the glossary's removals, changes and
 * additions, then the lexicon's, as one step. A rendering the pass changes on an entry it leaves locked, over segments
 * already decided, leaves a term deferral as the person's own edit does ({@link DeferralRegister#termChanged}), so the
 * revision finds them. A write that fails half-way puts both tables back as they were before the step and returns its
 * error; the in-memory stores make that restore exact.
 */
@Slf4j
@RequiredArgsConstructor
final class NamesPassStore {

    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;

    /**
     * Writes both tables' changes, or none of them.
     *
     * @param projectId the project; never null
     * @param names the glossary's changes against its stored state; never null
     * @param terms the lexicon's changes against its stored state; never null
     * @return {@code true} once written; or the first failed write's or read's error, both tables restored
     */
    Result<Boolean> store(
            final String projectId, final EntryChanges<GlossaryEntry> names, final EntryChanges<LexiconEntry> terms) {
        Objects.requireNonNull(projectId, "projectId");
        final Result<List<GlossaryEntry>> namesBefore = glossary.all(projectId);
        final Result<List<LexiconEntry>> termsBefore = lexicon.all(projectId);
        final Result<List<SegmentRecord>> decided = segments.all(projectId);
        for (final Result<?> read : List.of(namesBefore, termsBefore, decided)) {
            if (read.isErr()) {
                return Result.err(Objects.requireNonNull(read.error(), "error"));
            }
        }
        log.debug(
                "Names pass store project={} names changed={} terms changed={}",
                projectId,
                names.changed().size(),
                terms.changed().size());
        final Result<Boolean> stored = storeNames(projectId, names, Objects.requireNonNull(decided.data(), "decided"))
                .flatMap(done -> storeTerms(projectId, terms));
        if (stored.isErr()) {
            log.warn("Names pass store project={} failed half-way: both tables restored", projectId);
            restore(projectId, Objects.requireNonNull(namesBefore.data()), Objects.requireNonNull(termsBefore.data()));
        }
        return stored;
    }

    private Result<Boolean> storeNames(
            final String projectId, final EntryChanges<GlossaryEntry> names, final List<SegmentRecord> decided) {
        for (final EntryChanges.Removal<GlossaryEntry> removal : names.removed()) {
            final Result<Boolean> done =
                    glossary.remove(projectId, removal.entry().id());
            if (done.isErr()) {
                return done;
            }
        }
        for (final EntryChanges.Change<GlossaryEntry> change : names.changed()) {
            final Result<Boolean> done =
                    glossary.update(change.after()).flatMap(stored -> deferred(change.before(), stored, decided));
            if (done.isErr()) {
                return done;
            }
        }
        for (final GlossaryEntry added : names.added()) {
            final Result<GlossaryEntry> done = glossary.add(added);
            if (done.isErr()) {
                return Result.err(Objects.requireNonNull(done.error(), "error"));
            }
        }
        return Result.ok(true);
    }

    // The same term deferrals the glossary service records for the person's edit of a locked rendering.
    private Result<Boolean> deferred(
            final GlossaryEntry before, final GlossaryEntry after, final List<SegmentRecord> decided) {
        for (final Deferral deferral : DeferralRegister.termChanged(before, after, decided)) {
            final Result<Deferral> added = deferrals.add(deferral);
            if (added.isErr()) {
                return Result.err(Objects.requireNonNull(added.error(), "error"));
            }
        }
        return Result.ok(true);
    }

    private Result<Boolean> storeTerms(final String projectId, final EntryChanges<LexiconEntry> terms) {
        for (final EntryChanges.Removal<LexiconEntry> removal : terms.removed()) {
            final Result<Boolean> done =
                    lexicon.remove(projectId, removal.entry().term());
            if (done.isErr()) {
                return done;
            }
        }
        final List<LexiconEntry> written = new ArrayList<>();
        terms.changed().forEach(change -> written.add(change.after()));
        written.addAll(terms.added());
        for (final LexiconEntry entry : written) {
            final Result<LexiconEntry> done = lexicon.put(entry);
            if (done.isErr()) {
                return Result.err(Objects.requireNonNull(done.error(), "error"));
            }
        }
        return Result.ok(true);
    }

    // Best effort: every entry the step left that was not there goes, every one that was is written back as it was.
    private void restore(final String projectId, final List<GlossaryEntry> names, final List<LexiconEntry> terms) {
        final Set<String> ids = new HashSet<>();
        names.forEach(entry -> ids.add(entry.id()));
        final List<GlossaryEntry> now = glossary.all(projectId).data();
        if (now != null) {
            now.stream()
                    .filter(entry -> !ids.contains(entry.id()))
                    .forEach(entry -> glossary.remove(projectId, entry.id()));
        }
        names.forEach(entry -> {
            final Result<GlossaryEntry> back = glossary.update(entry);
            if (back.isErr()) {
                glossary.add(entry);
            }
        });
        final Set<String> keys = new HashSet<>();
        terms.forEach(entry -> keys.add(LexiconEntry.keyOf(entry.term())));
        final List<LexiconEntry> held = lexicon.all(projectId).data();
        if (held != null) {
            held.stream()
                    .filter(entry -> !keys.contains(LexiconEntry.keyOf(entry.term())))
                    .forEach(entry -> lexicon.remove(projectId, entry.term()));
        }
        terms.forEach(lexicon::put);
        log.debug("Names pass store project={} restored {} names and {} terms", projectId, names.size(), terms.size());
    }
}
