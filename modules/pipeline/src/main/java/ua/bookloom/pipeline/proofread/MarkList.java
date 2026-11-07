package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.audit.AuditFindings;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;

/**
 * The proofread mark list, built by code over every decided story segment (design D15 "Order"): tier 1 holds the
 * FLAGGED segments, those with a repair-level finding the one repair left, and those with an open deferral whose fact
 * has arrived; tier 2 holds the note-level findings and audit doubts, and the doubts of {@link MarkDetectors}. A model
 * asked to "improve" a paragraph with no named defect changes little or the wrong thing, so every entry names its
 * reasons. Nothing is stored: the list is read from the stored records, the open deferrals and the map.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarkList {

    /**
     * The book as the list reads it.
     *
     * @param brief the non-null Book Brief, whose target language, narrator and kept kinds the list reads
     * @param document the non-null opened book, whose body units are the story
     * @param glossary the non-null glossary entries as they are now
     * @param lexicon the non-null recurring terms as they are now
     */
    public record Book(BookBrief brief, Document document, List<GlossaryEntry> glossary, List<LexiconEntry> lexicon) {

        /** Rejects a missing part and copies the lists. */
        public Book {
            Objects.requireNonNull(brief, "brief");
            Objects.requireNonNull(document, "document");
            glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
            lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
        }
    }

    /**
     * Lists the decided story segments worth a second look.
     *
     * @param book the non-null book and map
     * @param records the non-null stored records
     * @param open the non-null open deferrals
     * @return one mark per listed segment, in reading order; never null, empty when nothing is listed
     */
    public static List<Mark> of(final Book book, final List<SegmentRecord> records, final List<Deferral> open) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(open, "open");
        final Map<String, SegmentRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        final Map<String, List<ArrivedGender>> arrived = arrivedFacts(book, open);
        final Set<SegmentKind> kept = book.brief().alsoTranslate().keptKinds();
        final MarkDetectors.MapNow map = new MarkDetectors.MapNow(book.glossary(), book.lexicon());
        final String language = Objects.requireNonNull(book.brief().targetLanguage(), "target language");
        final List<Mark> marks = new ArrayList<>();
        for (final Segment segment : storySegments(book.document())) {
            final SegmentRecord record = byId.get(segment.id());
            if (record != null && isListed(record, kept)) {
                final List<ArrivedGender> facts = arrived.getOrDefault(segment.id(), List.of());
                final List<String> marked = marked(record, facts);
                final List<String> doubtful = doubtful(record, segment, map, language);
                facts.forEach(fact -> doubtful.remove(MarkDetectors.genderEndingReason(fact.name(), fact.gender())));
                markOf(segment.id(), marked, doubtful).ifPresent(marks::add);
            }
        }
        log.debug("Mark list built segments={} marks={}", records.size(), marks.size());
        return List.copyOf(marks);
    }

    private static Optional<Mark> markOf(
            final String segmentId, final List<String> marked, final List<String> doubtful) {
        if (marked.isEmpty() && doubtful.isEmpty()) {
            return Optional.empty();
        }
        final Set<String> reasons = new LinkedHashSet<>(marked);
        reasons.addAll(doubtful);
        final Mark.Tier tier = marked.isEmpty() ? Mark.Tier.DOUBTFUL : Mark.Tier.MARKED;
        final Mark mark = new Mark(segmentId, tier, List.copyOf(reasons));
        log.debug(
                "Marked segmentId={} tier={} reasons={}",
                segmentId,
                mark.tier(),
                mark.reasons().size());
        log.trace("Marked segmentId={} reasons={}", segmentId, mark.reasons());
        return Optional.of(mark);
    }

    private static Map<String, List<ArrivedGender>> arrivedFacts(final Book book, final List<Deferral> open) {
        final Map<String, GlossaryEntry> byTerm = byTerm(book.glossary());
        final Map<String, List<ArrivedGender>> facts = new HashMap<>();
        open.forEach(deferral -> ArrivedGender.of(deferral, byTerm, book.brief().narrator())
                .ifPresent(fact -> facts.computeIfAbsent(deferral.segmentId(), id -> new ArrayList<>())
                        .add(fact)));
        return facts;
    }

    /**
     * The glossary by term, the first entry winning a repeated term.
     *
     * @param glossary the non-null entries
     * @return never null
     */
    static Map<String, GlossaryEntry> byTerm(final List<GlossaryEntry> glossary) {
        return glossary.stream()
                .collect(Collectors.toMap(GlossaryEntry::term, Function.identity(), (first, second) -> first));
    }

    private static List<String> marked(final SegmentRecord record, final List<ArrivedGender> arrived) {
        final List<String> reasons = new ArrayList<>();
        if (record.status() == SegmentStatus.FLAGGED) {
            final List<String> blockers = checksOf(record.findings(), CheckLevel.BLOCK);
            reasons.add(blockers.isEmpty() ? "flagged" : "flagged: " + String.join(", ", blockers));
        }
        checksOf(record.findings(), CheckLevel.REPAIR).forEach(check -> reasons.add("proofread mark: " + check));
        arrived.forEach(fact -> reasons.add(fact.reason()));
        return reasons;
    }

    private static List<String> doubtful(
            final SegmentRecord record, final Segment segment, final MarkDetectors.MapNow map, final String language) {
        final List<String> reasons = new ArrayList<>();
        record.findings().stream()
                .filter(finding -> CheckLevels.of(finding) == CheckLevel.NOTE)
                .forEach(finding -> reasons.add(noteReason(finding)));
        final String target = DisplayText.of(Objects.requireNonNull(maskedTarget(record), "masked target"));
        reasons.addAll(MarkDetectors.find(map, DisplayText.of(segment.masked()), target, language));
        return reasons;
    }

    private static String noteReason(final QaFinding finding) {
        final String check = AuditFindings.isAudit(finding)
                ? "audit " + finding.raisedBy().substring(AuditFindings.PREFIX.length())
                : finding.raisedBy();
        return check + ": " + finding.note();
    }

    private static List<String> checksOf(final List<QaFinding> findings, final CheckLevel level) {
        return findings.stream()
                .filter(finding -> CheckLevels.of(finding) == level)
                .map(QaFinding::raisedBy)
                .distinct()
                .toList();
    }

    /**
     * Whether a stored segment holds a decided text the stage reads: not pending, not kept verbatim or in the source
     * language.
     *
     * @param record the non-null stored record
     * @param kept the auxiliary kinds the brief keeps in the source language
     * @return {@code true} if the stage may list or read it, {@code false} otherwise
     */
    static boolean isListed(final SegmentRecord record, final Set<SegmentKind> kept) {
        return record.status() != SegmentStatus.PENDING
                && maskedTarget(record) != null
                && record.path() != SegmentPath.VERBATIM
                && record.path() != SegmentPath.SOURCE_KEPT
                && !record.isKeptAsSource(kept);
    }

    /** The decided text a window shows: the person's edit, else the machine's; null when there is none. */
    static @Nullable String maskedTarget(final SegmentRecord record) {
        return record.maskedUserTarget() != null ? record.maskedUserTarget() : record.maskedMachineTarget();
    }

    /** The segments of the book's body units, in reading order: the story the stage reads. */
    static List<Segment> storySegments(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary() && unit.role() == UnitRole.BODY)
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }
}
