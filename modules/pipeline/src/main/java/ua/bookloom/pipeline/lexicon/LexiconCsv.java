package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.glossary.CsvRecords;
import ua.bookloom.pipeline.glossary.CsvRecords.CsvRecord;

/**
 * The recurring terms as a plain file (15i.N7) — RFC 4180 CSV in UTF-8 with the header {@value #HEADER}, read by the
 * glossary file's own tokenizer — so a series keeps its terms across books and one malformed line costs only that
 * line.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LexiconCsv {

    static final String HEADER = "term,target";

    private static final String RECORD_END = "\r\n";
    private static final int FIELD_COUNT = 2;
    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * One well-formed row.
     *
     * @param line the 1-based line the row starts on, counting the header when the file has one
     * @param term the source term; never blank
     * @param target the rendering, or null when the field is empty
     */
    record Row(int line, String term, @Nullable String target) {}

    /**
     * What reading a file found.
     *
     * @param rows the well-formed rows in file order
     * @param malformedLines the lines of the rows that lack two fields, have no term or hold a quote that never closes
     */
    record Parsed(List<Row> rows, List<Integer> malformedLines) {}

    /** The terms sorted by their comparison key, each with its established rendering or none. */
    static String format(final List<LexiconEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        final StringBuilder csv = new StringBuilder(HEADER).append(RECORD_END);
        entries.stream()
                .sorted(Comparator.comparing((LexiconEntry entry) -> LexiconEntry.keyOf(entry.term())))
                .forEach(entry -> csv.append(CsvRecords.quoted(entry.term()))
                        .append(',')
                        .append(CsvRecords.quoted(entry.established().orElse("")))
                        .append(RECORD_END));
        log.debug("Lexicon CSV formatted: {} entries", entries.size());
        return csv.toString();
    }

    /** Reads CSV text, skipping a header and any blank line; a file with no header numbers its first record line 1. */
    static Parsed parse(final String text) {
        Objects.requireNonNull(text, "text");
        final String body = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        final List<CsvRecord> records = CsvRecords.read(body).stream()
                .filter(record -> !record.isBlankLine())
                .toList();
        final boolean headed = !records.isEmpty() && isHeader(records.getFirst());
        final List<Row> rows = new ArrayList<>();
        final List<Integer> malformed = new ArrayList<>();
        for (final CsvRecord record : records.stream().skip(headed ? 1 : 0).toList()) {
            if (isUsable(record)) {
                final String target = record.fields().get(1);
                rows.add(new Row(record.line(), record.fields().getFirst(), target.isEmpty() ? null : target));
            } else {
                malformed.add(record.line());
            }
        }
        log.debug("Lexicon CSV parsed: {} rows, {} malformed", rows.size(), malformed.size());
        return new Parsed(List.copyOf(rows), List.copyOf(malformed));
    }

    private static boolean isHeader(final CsvRecord record) {
        final List<String> expected = List.of(HEADER.split(","));
        final List<String> fields = record.fields();
        return fields.size() == FIELD_COUNT
                && fields.get(0).strip().equalsIgnoreCase(expected.get(0))
                && fields.get(1).strip().equalsIgnoreCase(expected.get(1));
    }

    private static boolean isUsable(final CsvRecord record) {
        return record.terminated()
                && record.fields().size() == FIELD_COUNT
                && !record.fields().getFirst().isBlank();
    }
}
