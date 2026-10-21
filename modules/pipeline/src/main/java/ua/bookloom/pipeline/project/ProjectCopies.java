package ua.bookloom.pipeline.project;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TargetOrigin;

/**
 * Copies what is about the book, not about one translation, into the project of another target language: the
 * segments as pending, the glossary's terms with their type, gender, lock and aliases but no target, and the
 * recurring terms without renderings. Translation memory, runs, summaries, deferrals and removals stay behind.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ProjectCopies {

    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;
    private final SegmentRepository segments;
    private final IdSource ids;

    Result<Integer> copy(final String fromId, final String toId) {
        log.debug("copy contents from={} to={}", fromId, toId);
        return segments.all(fromId)
                .flatMap(records -> segments.saveAll(toId, pending(records, toId)))
                .flatMap(count -> glossary.all(fromId).flatMap(entries -> copyGlossary(entries, toId)))
                .flatMap(count -> lexicon.all(fromId).flatMap(entries -> copyLexicon(entries, toId)))
                .map(count -> {
                    log.info("project contents copied from={} to={}", fromId, toId);
                    return count;
                });
    }

    private static List<SegmentRecord> pending(final List<SegmentRecord> records, final String toId) {
        return records.stream()
                .map(record -> new SegmentRecord(
                        toId,
                        record.segmentId(),
                        record.unitId(),
                        record.ord(),
                        record.kind(),
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
                        null))
                .toList();
    }

    private Result<Integer> copyGlossary(final List<GlossaryEntry> entries, final String toId) {
        for (final GlossaryEntry entry : entries) {
            final Result<GlossaryEntry> added = glossary.add(new GlossaryEntry(
                    ids.newId(),
                    toId,
                    entry.term(),
                    null,
                    entry.type(),
                    entry.gender(),
                    entry.locked(),
                    TargetOrigin.PERSON,
                    entry.genderOrigin(),
                    entry.genderSeedTried(),
                    entry.aliases(),
                    entry.noGenderEvidence()));
            if (added.isErr()) {
                return Result.err(failure(added));
            }
        }
        return Result.ok(entries.size());
    }

    private Result<Integer> copyLexicon(final List<LexiconEntry> entries, final String toId) {
        for (final LexiconEntry entry : entries) {
            final Result<LexiconEntry> put = lexicon.put(LexiconEntry.of(toId, entry.term()));
            if (put.isErr()) {
                return Result.err(failure(put));
            }
        }
        return Result.ok(entries.size());
    }

    private static AppError failure(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
