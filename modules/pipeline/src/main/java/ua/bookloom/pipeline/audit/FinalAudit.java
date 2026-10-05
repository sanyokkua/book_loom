package ua.bookloom.pipeline.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaEvaluator;
import ua.bookloom.pipeline.qa.SoftCheckInput;

/**
 * Looks again, with the cheap deterministic checks, at every accepted segment a person has not reviewed: a paragraph
 * left in the source language, a word that mixes alphabets, a quote pair left open, a doubled word, a placeholder token
 * written into the text, the narrator's gender, a doubted word and a glossary name the target lost. It reuses the
 * run's own checks rather than copying them, so what the run would block the audit lists. Spacing is left out: the
 * typography pass owns it and it is no leak. The scan only reads; storing what it finds is {@link AuditRecorder}'s.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FinalAudit {

    private static final String TOKEN_LEAK = "token-leak";

    /**
     * The book as the audit reads it.
     *
     * @param brief the non-null Book Brief whose languages and policies the checks hold the text against
     * @param document the non-null opened book the stored records describe
     * @param glossary the non-null glossary entries of the project
     * @param words the non-null word validator
     */
    public record Book(BookBrief brief, Document document, List<GlossaryEntry> glossary, WordValidator words) {

        /** Rejects a missing part and copies the glossary. */
        public Book {
            Objects.requireNonNull(brief, "brief");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(glossary, "glossary");
            Objects.requireNonNull(words, "words");
            glossary = List.copyOf(glossary);
        }
    }

    /**
     * Audits every accepted, unreviewed segment.
     *
     * @param book the non-null book
     * @param records the non-null stored records, in document order
     * @return for each doubted segment its audit findings, in the order of {@code records}; never null, empty when
     *     every check is quiet
     */
    public static Map<String, List<QaFinding>> scan(final Book book, final List<SegmentRecord> records) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(records, "records");
        final Map<String, Segment> sources = sourcesOf(book.document());
        final Map<String, List<QaFinding>> doubted = new LinkedHashMap<>();
        final Set<SegmentKind> kept = book.brief().alsoTranslate().keptKinds();
        for (final SegmentRecord record : records) {
            final Segment source = sources.get(record.segmentId());
            if (source != null && isAudited(record, kept)) {
                final List<QaFinding> findings = findingsOf(book, source, record);
                if (!findings.isEmpty()) {
                    doubted.put(record.segmentId(), findings);
                }
            }
        }
        log.debug("Final audit looked at {} record(s) and doubts {}", records.size(), doubted.size());
        return doubted;
    }

    /**
     * Names the doubted segments the way the review list and the reports do.
     *
     * @param doubted the non-null result of {@link #scan}
     * @param document the non-null opened book, whose locators name the segments
     * @return the doubted segments in the order of {@code doubted}
     */
    public static List<SuspiciousSegment> named(final Map<String, List<QaFinding>> doubted, final Document document) {
        Objects.requireNonNull(doubted, "doubted");
        final Map<String, SegmentLocator> locators = SegmentLocators.of(document);
        final List<SuspiciousSegment> named = new ArrayList<>();
        doubted.forEach((segmentId, findings) -> {
            final SegmentLocator locator = locators.get(segmentId);
            named.add(new SuspiciousSegment(
                    segmentId, locator == null ? segmentId : locator.text(), AuditFindings.checksOf(findings)));
        });
        return named;
    }

    private static boolean isAudited(final SegmentRecord record, final Set<SegmentKind> kept) {
        return record.status() == SegmentStatus.ACCEPTED
                && !record.reviewed()
                && !record.isKeptAsSource(kept)
                && record.path() != SegmentPath.VERBATIM
                && record.path() != SegmentPath.SOURCE_KEPT
                && record.maskedMachineTarget() != null;
    }

    private static List<QaFinding> findingsOf(final Book book, final Segment source, final SegmentRecord record) {
        final String masked = record.maskedUserTarget() != null
                ? record.maskedUserTarget()
                : Objects.requireNonNull(record.maskedMachineTarget(), "masked target");
        final String sourceText = DisplayText.of(source.masked());
        final String target = DisplayText.of(masked);
        final List<QaFinding> found = new ArrayList<>();
        textChecks(book, source, sourceText, target).forEach(found::add);
        tokenLeak(record).ifPresent(found::add);
        NameMissingCheck.find(book.glossary(), sourceText, target).ifPresent(found::add);
        final List<QaFinding> audited = found.stream().map(AuditFindings::of).toList();
        if (!audited.isEmpty()) {
            log.debug("Final audit doubts segment {}: {}", record.segmentId(), AuditFindings.checksOf(audited));
        }
        return audited;
    }

    private static List<QaFinding> textChecks(
            final Book book, final Segment source, final String sourceText, final String target) {
        final BookBrief brief = book.brief();
        final SoftCheckInput input = new SoftCheckInput(
                sourceText,
                target,
                target,
                brief.sourceLanguage(),
                Objects.requireNonNull(brief.targetLanguage(), "target language"),
                brief.foreignPassages(),
                brief.names(),
                source.declaredLanguage(),
                book.glossary().stream().map(GlossaryEntry::term).toList(),
                List.of(),
                brief.narrator());
        return QaEvaluator.textChecks(input, book.words()).stream()
                .filter(result -> result.check() != CheckName.SPACING)
                .filter(result -> !isEnglishQuoteNote(result))
                .map(CheckResult::finding)
                .filter(Objects::nonNull)
                .toList();
    }

    // Balanced English curly quotes are a style note for review, not something to doubt in a finished book.
    private static boolean isEnglishQuoteNote(final CheckResult result) {
        return result.check() == CheckName.QUOTE_BALANCE && result.passed();
    }

    // The stored plain target never holds a placeholder: unmasking turns each one back into the book's markup.
    private static Optional<QaFinding> tokenLeak(final SegmentRecord record) {
        final String plain = record.effectiveTarget().orElse("");
        final List<String> leaked = Tokens.inOrder(plain);
        if (leaked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QaFinding(
                "markup",
                Severity.MEDIUM,
                "A placeholder token (" + leaked.getFirst() + ") stands in the target text.",
                TOKEN_LEAK));
    }

    private static Map<String, Segment> sourcesOf(final Document document) {
        final Map<String, Segment> byId = new LinkedHashMap<>();
        for (final Unit unit : document.units()) {
            unit.segments().forEach(segment -> byId.put(segment.id(), segment));
        }
        return byId;
    }
}
