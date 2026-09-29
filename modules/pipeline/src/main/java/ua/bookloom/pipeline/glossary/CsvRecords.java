package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The RFC 4180 tokenizer behind the glossary file: it splits text into records of fields and remembers the line each
 * record starts on, because a quoted field may hold line breaks and the person is told which line to fix.
 */
final class CsvRecords {

    /**
     * One record of the file.
     *
     * @param line the 1-based line the record starts on
     * @param fields the record's fields, quotes removed
     * @param terminated {@code false} when a quoted field never closed before the text ended
     */
    record CsvRecord(int line, List<String> fields, boolean terminated) {

        boolean isBlankLine() {
            return fields.size() == 1 && fields.getFirst().isEmpty();
        }
    }

    private static final char QUOTE = '"';
    private static final char SEPARATOR = ',';

    private final String text;
    private int position;
    private int line = 1;

    private CsvRecords(final String text) {
        this.text = text;
    }

    static List<CsvRecord> read(final String text) {
        Objects.requireNonNull(text, "text");
        final CsvRecords reader = new CsvRecords(text);
        final List<CsvRecord> records = new ArrayList<>();
        while (reader.position < text.length()) {
            records.add(reader.nextRecord());
        }
        return records;
    }

    private CsvRecord nextRecord() {
        final int startLine = line;
        final List<String> fields = new ArrayList<>();
        boolean terminated = true;
        boolean moreFields = true;
        while (moreFields) {
            final StringBuilder field = new StringBuilder();
            terminated &= readField(field);
            fields.add(field.toString());
            moreFields = endOfField();
        }
        return new CsvRecord(startLine, fields, terminated);
    }

    private boolean readField(final StringBuilder field) {
        boolean terminated = true;
        if (peek() == QUOTE) {
            position++;
            terminated = readQuoted(field);
        }
        while (position < text.length() && !isDelimiter(text.charAt(position))) {
            field.append(text.charAt(position++));
        }
        return terminated;
    }

    private boolean readQuoted(final StringBuilder field) {
        while (position < text.length()) {
            final char current = text.charAt(position++);
            if (current == QUOTE && peek() == QUOTE) {
                field.append(QUOTE);
                position++;
            } else if (current == QUOTE) {
                return true;
            } else {
                countLineBreak(current);
                field.append(current);
            }
        }
        return false;
    }

    private void countLineBreak(final char current) {
        if (current == '\n' || (current == '\r' && peek() != '\n')) {
            line++;
        }
    }

    private boolean endOfField() {
        if (position >= text.length()) {
            return false;
        }
        final char current = text.charAt(position++);
        if (current == SEPARATOR) {
            return true;
        }
        if (current == '\r' && peek() == '\n') {
            position++;
        }
        line++;
        return false;
    }

    private char peek() {
        return position < text.length() ? text.charAt(position) : '\0';
    }

    private static boolean isDelimiter(final char character) {
        return character == SEPARATOR || character == '\r' || character == '\n';
    }
}
