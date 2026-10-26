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
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.revision.DeferralRegister;
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
 * @param languageTag the source language's tag for the name clusters, or null when it is not known
 */
@Slf4j
record GlossaryRowsImport(
        GlossaryRepository glossary,
        SegmentRepository segments,
        DeferralRepository deferrals,
        @Nullable String languageTag) {

    GlossaryRowsImport {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(deferrals, "deferrals");
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
        held.forEach(entry -> byKey.put(GlossaryKeys.of(entry.term()), entry));
        final List<Integer> refused = new ArrayList<>();
        final List<EntryChange> changes = new ArrayList<>();
        for (final GlossaryCsv.Row row : rows) {
            if (row.locked() && (row.target() == null || row.target().isBlank())) {
                refused.add(row.line());
                continue;
            }
            final GlossaryEntry before = byKey.get(GlossaryKeys.of(row.term()));
            changes.add(new EntryChange(before, before == null ? added(projectId, row) : GlossaryCsv.replaced(before, row)));
        }
        log.debug("Glossary rows import project={} storing={} refused={}", projectId, changes.size(), refused.size());
        return glossary.putAll(projectId, changes.stream().map(EntryChange::after).toList())
                .flatMap(stored -> deferChangedTargets(projectId, changes))
                .flatMap(deferred -> ClusterGender.propagateAll(glossary, projectId, changes, languageTag))
                .map(shared -> List.copyOf(refused));
    }

    private static GlossaryEntry added(final String projectId, final GlossaryCsv.Row row) {
        return new GlossaryEntry(
                GlossaryIds.of(projectId, row.term()),
                projectId,
                row.term(),
                row.target(),
                row.type(),
                row.gender(),
                row.locked());
    }

    // A target the file changes defers the decided segments that used the old one, as an edit of the cell does.
    private Result<Integer> deferChangedTargets(final String projectId, final List<EntryChange> changes) {
        final List<EntryChange> retargeted =
                changes.stream().filter(GlossaryRowsImport::isRetargeted).toList();
        log.debug("Glossary rows import project={} changedTargets={}", projectId, retargeted.size());
        if (retargeted.isEmpty()) {
            return Result.ok(0);
        }
        return segments.all(projectId).flatMap(decided -> deferAll(retargeted, decided));
    }

    private static boolean isRetargeted(final EntryChange change) {
        final GlossaryEntry before = change.before();
        return before != null && !Objects.equals(before.target(), change.after().target());
    }

    private Result<Integer> deferAll(final List<EntryChange> retargeted, final List<SegmentRecord> decided) {
        int added = 0;
        for (final EntryChange change : retargeted) {
            for (final Deferral deferral : DeferralRegister.termChanged(
                    Objects.requireNonNull(change.before(), "before"), change.after(), decided)) {
                final Result<Deferral> stored = deferrals.add(deferral);
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
                added++;
            }
        }
        return Result.ok(added);
    }
}
