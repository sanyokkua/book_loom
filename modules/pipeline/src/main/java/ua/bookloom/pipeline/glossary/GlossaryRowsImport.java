package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Stores the rows of a glossary file with the rules an edit follows — a row locked with no target is refused, a held
 * term keeps what the row leaves unknown, a changed target defers the decided segments that used it, and a gender the
 * file chooses goes to the other spellings of the person — but reads the glossary once and writes every entry in one
 * step. Row by row, each read and write handled the whole glossary, so 5,000 rows took minutes.
 *
 * @param glossary where the entries are stored
 * @param segments the decided segments a changed target is checked against
 * @param deferrals where a segment a changed target touches is deferred
 * @param summaries the kept chapter summaries a changed pinned or locked target is written into
 * @param openProjects where the segments naming an entry are read from, for a gender that arrives
 * @param languageTag the source language's tag for the name clusters, or null when it is not known
 * @param targetLanguage the project's target language, whose file says whether a row's stress marks are removed; null
 *     when none is chosen
 */
@Slf4j
record GlossaryRowsImport(
        GlossaryRepository glossary,
        SegmentRepository segments,
        DeferralRepository deferrals,
        SummaryRepository summaries,
        OpenProjects openProjects,
        @Nullable String languageTag,
        @Nullable String targetLanguage) {

    GlossaryRowsImport {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(deferrals, "deferrals");
        Objects.requireNonNull(summaries, "summaries");
        Objects.requireNonNull(openProjects, "openProjects");
    }

    /**
     * Stores the rows.
     *
     * @param projectId the project
     * @param rows the rows, one per term
     * @return the lines of the rows refused because they are locked with no target, or the store's error
     */
    Result<List<Integer>> apply(final String projectId, final List<GlossaryCsv.Row> rows) {
        log.debug("Glossary rows import project={} rows={}", projectId, rows.size());
        return glossary.all(projectId).flatMap(held -> store(projectId, rows, held));
    }

    private Result<List<Integer>> store(
            final String projectId, final List<GlossaryCsv.Row> rows, final List<GlossaryEntry> held) {
        final Map<String, GlossaryEntry> byKey = new HashMap<>();
        // A row names an entry by its lemma, else by one of its forms ({@code Велемира} edits {@code Велемир}).
        held.forEach(entry -> byKey.putIfAbsent(GlossaryKeys.of(entry.term()), entry));
        held.forEach(entry -> entry.forms().forEach(form -> byKey.putIfAbsent(GlossaryKeys.of(form), entry)));
        final List<Integer> refused = new ArrayList<>();
        final List<EntryChange> changes = new ArrayList<>();
        for (final GlossaryCsv.Row written : rows) {
            final GlossaryCsv.Row row = cleaned(written);
            if (row.locked() && (row.target() == null || row.target().isBlank())) {
                refused.add(row.line());
                continue;
            }
            final GlossaryEntry before = byKey.get(GlossaryKeys.of(row.term()));
            changes.add(new EntryChange(
                    before, before == null ? added(projectId, row) : GlossaryCsv.replaced(before, row)));
        }
        log.debug("Glossary rows import project={} storing={} refused={}", projectId, changes.size(), refused.size());
        return glossary.putAll(
                        projectId, changes.stream().map(EntryChange::after).toList())
                .flatMap(stored -> deferChangedTargets(projectId, changes))
                .flatMap(deferred -> ClusterGender.propagateAll(glossary, projectId, changes, languageTag))
                .map(shared -> List.copyOf(refused));
    }

    // A row's target is stored without the stress marks its language asks to remove; one left blank is no target.
    private GlossaryCsv.Row cleaned(final GlossaryCsv.Row row) {
        final String clean = TargetText.clean(row.target(), targetLanguage);
        if (Objects.equals(clean, row.target())) {
            return row;
        }
        log.debug("Glossary rows import line {} target cleaned", row.line());
        return new GlossaryCsv.Row(
                row.line(),
                row.term(),
                clean == null || clean.isBlank() ? null : clean,
                row.type(),
                row.gender(),
                row.locked());
    }

    private static GlossaryEntry added(final String projectId, final GlossaryCsv.Row row) {
        return GlossaryEntry.of(
                GlossaryIds.of(projectId, row.term()),
                projectId,
                row.term(),
                row.target(),
                row.type(),
                row.gender(),
                row.locked());
    }

    // A target or an enforced gender the file changes defers the decided segments, as an edit of the cell does.
    private Result<Integer> deferChangedTargets(final String projectId, final List<EntryChange> changes) {
        final List<EntryChange> retargeted =
                changes.stream().filter(GlossaryRowsImport::isRetargeted).toList();
        log.debug("Glossary rows import project={} changedEntries={}", projectId, retargeted.size());
        if (retargeted.isEmpty()) {
            return Result.ok(0);
        }
        return segments.all(projectId).flatMap(decided -> deferAll(retargeted, decided));
    }

    private static boolean isRetargeted(final EntryChange change) {
        final GlossaryEntry before = change.before();
        return before != null && TermChanges.touchesDecided(before, change.after());
    }

    private Result<Integer> deferAll(final List<EntryChange> retargeted, final List<SegmentRecord> decided) {
        final TermChanges.Stores stores = new TermChanges.Stores(deferrals, summaries, openProjects);
        int added = 0;
        for (final EntryChange change : retargeted) {
            final Result<Integer> recorded = TermChanges.record(
                    stores, Objects.requireNonNull(change.before(), "before"), change.after(), decided, targetLanguage);
            if (recorded.isErr()) {
                return recorded;
            }
            added += Objects.requireNonNull(recorded.data(), "recorded");
        }
        return Result.ok(added);
    }
}
