package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.CsvRecords.CsvRecord;

/**
 * The glossary as a plain file — RFC 4180 CSV in UTF-8 with the header {@value #HEADER} — so a series keeps its names
 * across books and one malformed line costs only that line.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlossaryCsv {

    /** The header line, written first and skipped on import when a file starts with it. */
    public static final String HEADER = "term,target,type,gender,locked";

    private static final String RECORD_END = "\r\n";
    private static final int FIELD_COUNT = 5;
    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * One well-formed row of a file.
     *
     * @param line the 1-based line the row starts on, counting the header when the file has one
     * @param term the source term; never blank
     * @param target the target, or null when the field is empty
     * @param type the term's type
     * @param gender the term's gender
     * @param locked whether the row asks for the term to be locked
     */
    public record Row(int line, String term, @Nullable String target, TermType type, Gender gender, boolean locked) {}

    /**
     * What reading a file found.
     *
     * @param rows the well-formed rows in file order
     * @param malformedLines the lines of the rows that lack five fields, hold an unknown type, gender or locked value,
     *     have no term, or hold a quote that never closes
     */
    public record Parsed(List<Row> rows, List<Integer> malformedLines) {}

    /**
     * Writes entries as CSV text, each record ended by CRLF as RFC 4180 has it.
     *
     * @param entries the entries to write, in order; never null
     * @return the header followed by one record per entry
     */
    public static String format(final List<GlossaryEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        final StringBuilder csv = new StringBuilder(HEADER).append(RECORD_END);
        for (final GlossaryEntry entry : entries) {
            csv.append(quoted(entry.term()))
                    .append(',')
                    .append(quoted(entry.target() == null ? "" : entry.target()))
                    .append(',')
                    .append(entry.type().name().toLowerCase(Locale.ROOT))
                    .append(',')
                    .append(entry.gender().name().toLowerCase(Locale.ROOT))
                    .append(',')
                    .append(entry.locked())
                    .append(RECORD_END);
        }
        log.debug("Glossary CSV formatted: {} entries", entries.size());
        return csv.toString();
    }

    /**
     * Reads CSV text, skipping the header when the file has one and any blank line, and setting aside every row it
     * cannot use; a file with no header numbers its first record line 1.
     *
     * @param text the file's text; never null, a leading byte-order mark is ignored
     * @return the usable rows and the lines of the rest
     */
    public static Parsed parse(final String text) {
        Objects.requireNonNull(text, "text");
        final String body = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        final List<Row> rows = new ArrayList<>();
        final List<Integer> malformed = new ArrayList<>();
        final List<CsvRecord> records = CsvRecords.read(body).stream()
                .filter(record -> !record.isBlankLine())
                .toList();
        final boolean headed = !records.isEmpty() && isHeader(records.getFirst());
        for (final CsvRecord record : records.stream().skip(headed ? 1 : 0).toList()) {
            final Optional<Row> row = rowOf(record);
            row.ifPresentOrElse(rows::add, () -> malformed.add(record.line()));
        }
        log.debug("Glossary CSV parsed: {} rows, {} malformed", rows.size(), malformed.size());
        return new Parsed(List.copyOf(rows), List.copyOf(malformed));
    }

    private static boolean isHeader(final CsvRecord record) {
        final List<String> expected = List.of(HEADER.split(","));
        final List<String> fields = record.fields();
        return fields.size() == expected.size()
                && IntStream.range(0, FIELD_COUNT)
                        .allMatch(index -> fields.get(index).strip().equalsIgnoreCase(expected.get(index)));
    }

    private static Optional<Row> rowOf(final CsvRecord record) {
        final List<String> fields = record.fields();
        if (!record.terminated()
                || fields.size() != FIELD_COUNT
                || fields.getFirst().isBlank()) {
            return Optional.empty();
        }
        final Optional<TermType> type = constant(TermType.class, fields.get(2));
        final Optional<Gender> gender = constant(Gender.class, fields.get(3));
        final Optional<Boolean> locked = bool(fields.get(4));
        if (type.isEmpty() || gender.isEmpty() || locked.isEmpty()) {
            return Optional.empty();
        }
        final String target = fields.get(1);
        return Optional.of(new Row(
                record.line(),
                fields.getFirst(),
                target.isEmpty() ? null : target,
                type.get(),
                gender.get(),
                locked.get()));
    }

    private static <E extends Enum<E>> Optional<E> constant(final Class<E> type, final String name) {
        final String wanted = name.strip();
        for (final E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(wanted)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> bool(final String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "true" -> Optional.of(Boolean.TRUE);
            case "false" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    private static String quoted(final String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\r') < 0 && field.indexOf('\n') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }
}
