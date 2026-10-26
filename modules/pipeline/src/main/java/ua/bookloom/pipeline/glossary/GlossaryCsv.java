package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CsvProblem;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetOrigin;
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
    private static final List<String> COLUMNS = List.of(HEADER.split(","));
    private static final int TERM = 0;
    private static final int TARGET = 1;
    private static final int TYPE = 2;
    private static final int GENDER = 3;
    private static final int LOCKED = 4;
    private static final int[] POSITIONAL = {TERM, TARGET, TYPE, GENDER, LOCKED};
    private static final String FORMULA_STARTS = "=+-@\t\r";
    private static final char NEUTRALISER = '\'';

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
     * @param headerRefused whether the first line is a header that does not name the five columns once each, in which
     *     case no row is read
     * @param problems why the malformed lines could not be read, each cause once
     */
    public record Parsed(
            List<Row> rows, List<Integer> malformedLines, boolean headerRefused, Set<CsvProblem> problems) {}

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
            csv.append(CsvRecords.quoted(neutralised(entry.term())))
                    .append(',')
                    .append(CsvRecords.quoted(neutralised(entry.target() == null ? "" : entry.target())))
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
        final List<CsvRecord> records = CsvRecords.read(body).stream()
                .filter(record -> !record.isBlankLine())
                .toList();
        final boolean headed = !records.isEmpty() && isHeader(records.getFirst());
        final Optional<int[]> order = headed ? columnsOf(records.getFirst()) : Optional.of(POSITIONAL);
        log.debug("Glossary CSV header present={} usable={}", headed, order.isPresent());
        if (order.isEmpty()) {
            log.warn("Glossary CSV refused: its header does not name the five columns once each");
            return new Parsed(List.of(), List.of(), true, Set.of());
        }
        final List<Row> rows = new ArrayList<>();
        final List<Integer> malformed = new ArrayList<>();
        final Set<CsvProblem> problems = EnumSet.noneOf(CsvProblem.class);
        for (final CsvRecord record : records.stream().skip(headed ? 1 : 0).toList()) {
            final Set<CsvProblem> found = problemsOf(record, order.get());
            if (found.isEmpty()) {
                rows.add(rowOf(record, order.get()));
            } else {
                malformed.add(record.line());
                problems.addAll(found);
            }
        }
        log.debug("Glossary CSV parsed: {} rows, {} malformed, problems {}", rows.size(), malformed.size(), problems);
        return new Parsed(List.copyOf(rows), List.copyOf(malformed), false, Set.copyOf(problems));
    }

    // A header is any first line that names both the term and the target column, in whatever order; a data row never
    // holds those two words, so a file without a header still reads its first line as a row.
    private static boolean isHeader(final CsvRecord record) {
        final List<String> names =
                record.fields().stream().map(GlossaryCsv::columnName).toList();
        return names.contains(COLUMNS.get(TERM)) && names.contains(COLUMNS.get(TARGET));
    }

    /** Where each column sits in a row, in {@link #COLUMNS} order; empty when a column is missing, unknown or doubled. */
    private static Optional<int[]> columnsOf(final CsvRecord header) {
        final List<String> names =
                header.fields().stream().map(GlossaryCsv::columnName).toList();
        final int[] order = new int[FIELD_COUNT];
        for (int column = 0; column < FIELD_COUNT; column++) {
            order[column] = names.indexOf(COLUMNS.get(column));
        }
        final boolean exact = names.size() == FIELD_COUNT
                && Arrays.stream(order).noneMatch(index -> index < 0)
                && Set.copyOf(names).size() == FIELD_COUNT;
        log.trace("Glossary CSV header columns {} read as order {}", names, Arrays.toString(order));
        return exact ? Optional.of(order) : Optional.empty();
    }

    private static String columnName(final String field) {
        return field.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Keeps a spreadsheet from reading a cell as a formula: a value that starts with {@code =}, {@code +}, {@code -},
     * {@code @}, a tab or a carriage return is written after a single quote, which {@link #restored} takes off again.
     */
    static String neutralised(final String value) {
        return !value.isEmpty() && FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? NEUTRALISER + value : value;
    }

    /** Takes off the quote {@link #neutralised} put before a formula-like value; any other value is kept as it is. */
    static String restored(final String value) {
        final boolean wrapped =
                value.length() > 1 && value.charAt(0) == NEUTRALISER && FORMULA_STARTS.indexOf(value.charAt(1)) >= 0;
        return wrapped ? value.substring(1) : value;
    }

    // Every cause a row has, so one bad file is explained in one pass: a row typed "person" with the gender "she"
    // names both.
    private static Set<CsvProblem> problemsOf(final CsvRecord record, final int[] order) {
        final List<String> fields = record.fields();
        if (!record.terminated()) {
            return Set.of(CsvProblem.OPEN_QUOTE);
        }
        if (fields.size() != FIELD_COUNT) {
            return Set.of(CsvProblem.FIELD_COUNT);
        }
        final Set<CsvProblem> found = EnumSet.noneOf(CsvProblem.class);
        if (restored(fields.get(order[TERM]).strip()).isEmpty()) {
            found.add(CsvProblem.NO_TERM);
        }
        if (constant(TermType.class, fields.get(order[TYPE])).isEmpty()) {
            found.add(CsvProblem.UNKNOWN_TYPE);
        }
        if (constant(Gender.class, fields.get(order[GENDER])).isEmpty()) {
            found.add(CsvProblem.UNKNOWN_GENDER);
        }
        if (bool(fields.get(order[LOCKED])).isEmpty()) {
            found.add(CsvProblem.UNKNOWN_LOCKED);
        }
        return found;
    }

    // Reads a row problemsOf found nothing wrong with.
    private static Row rowOf(final CsvRecord record, final int[] order) {
        final List<String> fields = record.fields();
        final String target = restored(fields.get(order[TARGET]).strip());
        return new Row(
                record.line(),
                restored(fields.get(order[TERM]).strip()),
                target.isEmpty() ? null : target,
                constant(TermType.class, fields.get(order[TYPE])).orElseThrow(),
                constant(Gender.class, fields.get(order[GENDER])).orElseThrow(),
                bool(fields.get(order[LOCKED])).orElseThrow());
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

    // A row never turns a known value into an unknown one: a rescan's file lists a character as "term, unknown"
    // before anybody has told it who she is, and importing it must not undo what the person or a review settled.
    static GlossaryEntry replaced(final GlossaryEntry existing, final Row row) {
        final TermType type = isGeneric(row.type()) && !isGeneric(existing.type()) ? existing.type() : row.type();
        final Gender gender = row.gender() == Gender.UNKNOWN ? existing.gender() : row.gender();
        if (type != row.type() || gender != row.gender()) {
            log.debug(
                    "Glossary import keeps entry {} type={} gender={} over the row's {} / {}",
                    existing.id(),
                    type,
                    gender,
                    row.type(),
                    row.gender());
        }
        final GlossaryEntry result = new GlossaryEntry(
                existing.id(),
                existing.projectId(),
                existing.term(),
                row.target(),
                type,
                gender,
                row.locked(),
                TargetOrigin.PERSON,
                row.gender() == Gender.UNKNOWN ? existing.genderOrigin() : GenderOrigin.PERSON,
                existing.genderSeedTried(),
                existing.aliases(),
                row.gender() == Gender.UNKNOWN && existing.noGenderEvidence());
        logOrigin(existing, result);
        return result;
    }

    private static void logOrigin(final GlossaryEntry existing, final GlossaryEntry result) {
        log.debug(
                "Glossary import entry {} gender={} origin={} (held origin {})",
                existing.id(),
                result.gender(),
                result.genderOrigin(),
                existing.genderOrigin());
    }

    private static boolean isGeneric(final TermType type) {
        return type == TermType.TERM || type == TermType.OTHER;
    }
}
