package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.audit.AuditFindings;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.context.InjectedCharacters;
import ua.bookloom.pipeline.qa.QaEvaluator;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.qa.SoftCheckInput;

/**
 * Runs every deterministic detector on an exported book against its source: per aligned segment the run's own
 * evaluation (the refusal gate, every text check and the soft checks, as {@link QaEvaluator#evaluate} runs them), then
 * the final audit over the whole book. A detector is named by its finding's {@code raisedBy}; the audit's names carry
 * the {@code audit:} prefix. The word validator is the offline one, so {@code unknown-word} never fires here.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecallDetectors {

    static RecallBookRun run(final RecallBook book, final DocumentPort documents) {
        Objects.requireNonNull(book, "book");
        log.info("Recall book {} source={} exported={}", book.name(), book.source(), book.exported());
        final Document original = ExportedBooks.open(documents, book.source());
        final Document written = ExportedBooks.open(documents, book.exported());
        final List<GlossaryEntry> glossary = ExportedBooks.glossary(book.glossary());
        final List<SegmentRecord> records = ExportedBooks.recordsOf(original, written);
        final Map<String, List<String>> audit = audit(book.brief(), original, glossary, records);
        final Map<String, Segment> sources = byId(original);
        final List<RecallSegment> segments = new ArrayList<>();
        for (final SegmentRecord record : records) {
            final Segment source = Objects.requireNonNull(sources.get(record.segmentId()), "aligned");
            final String target = Objects.requireNonNull(record.maskedMachineTarget(), "target");
            final Set<String> fired = new LinkedHashSet<>(checks(book, glossary, source, target));
            audit.getOrDefault(record.segmentId(), List.of()).forEach(check -> fired.add(AuditFindings.PREFIX + check));
            final String sourceText = DisplayText.of(source.masked());
            segments.add(new RecallSegment(
                    record.segmentId(), RecallGold.hash(sourceText, target), sourceText, target, List.copyOf(fired)));
        }
        final int unaligned = ExportedBooks.sourceCount(original) - records.size();
        log.info(
                "Recall book {} aligned={} unaligned={} glossary={}",
                book.name(),
                records.size(),
                unaligned,
                glossary.size());
        return new RecallBookRun(
                book.name(), segments, unaligned, RecallGold.read(book.dir().resolve(RecallGold.FILE)));
    }

    private static Map<String, List<String>> audit(
            final BookBrief brief,
            final Document original,
            final List<GlossaryEntry> glossary,
            final List<SegmentRecord> records) {
        final Map<String, List<String>> checks = new LinkedHashMap<>();
        FinalAudit.scan(new FinalAudit.Book(brief, original, glossary, WordValidator.none()), records)
                .forEach((id, findings) -> checks.put(id, AuditFindings.checksOf(findings)));
        log.debug("Recall audit doubts {} segment(s)", checks.size());
        return checks;
    }

    // The input a run's gate builds for one segment: the glossary's renderings as pairs and the character sheet of the
    // names the segment holds, so the vocative, swap, loss and name-gender checks see what a run shows them.
    private static List<String> checks(
            final RecallBook book, final List<GlossaryEntry> glossary, final Segment source, final String target) {
        final SoftCheckInput input = new SoftCheckInput(
                DisplayText.of(source.masked()),
                target,
                target,
                book.sourceLanguage(),
                book.targetLanguage(),
                book.brief().foreignPassages(),
                book.brief().names(),
                source.declaredLanguage(),
                glossary.stream().map(GlossaryEntry::term).toList(),
                List.of(),
                book.narrator(),
                pairs(glossary),
                InjectedCharacters.select(List.of(source), glossary, book.sourceLanguage()));
        final QaResult result = QaEvaluator.evaluate(List.of(), input, WordValidator.none());
        final List<String> fired =
                result.findings().stream().map(QaFinding::raisedBy).distinct().toList();
        log.debug("Recall checks segmentId={} fired={}", source.id(), fired);
        return fired;
    }

    private static List<String> pairs(final List<GlossaryEntry> glossary) {
        return glossary.stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .map(entry -> entry.term() + " → " + entry.target())
                .toList();
    }

    private static Map<String, Segment> byId(final Document document) {
        final Map<String, Segment> byId = new LinkedHashMap<>();
        document.units().forEach(unit -> unit.segments().forEach(segment -> byId.put(segment.id(), segment)));
        return byId;
    }
}
