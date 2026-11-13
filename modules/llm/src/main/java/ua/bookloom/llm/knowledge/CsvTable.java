package ua.bookloom.llm.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A knowledge file read as RFC 4180 text: a {@code # data-version:} first line, comment lines, one header, then rows.
 * A row is one physical line, so a field never holds a line break.
 *
 * @param dataVersion the version the file declares
 * @param rows the data rows; {@code Row.number} is the 1-based line in the file
 */
record CsvTable(String dataVersion, List<Row> rows) {

    private static final Pattern VERSION_LINE = Pattern.compile("#\\s*data-version:\\s*(\\S+)\\s*");

    /** One data line. */
    record Row(int number, List<String> fields) {}

    /**
     * Parses a file.
     *
     * @param file the file name, for messages
     * @param text the whole file
     * @param header the exact header line expected
     * @return the table
     * @throws KnowledgeFormatException on a missing or duplicate version line, a wrong header, a wrong field count or
     *     an unterminated quote
     */
    static CsvTable parse(String file, String text, String header) {
        final String[] lines = text.split("\n", -1);
        final int columns = header.split(",").length;
        @Nullable String version = null;
        boolean headerSeen = false;
        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final int number = i + 1;
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("#")) {
                version = readVersionLine(file, number, line, version, headerSeen);
            } else if (!headerSeen) {
                requireVersionAndHeader(file, number, line, header, version);
                headerSeen = true;
            } else {
                rows.add(new Row(number, splitRow(file, number, line, columns)));
            }
        }
        if (version == null) {
            throw new KnowledgeFormatException(file, 1, "missing data-version line");
        }
        if (!headerSeen) {
            throw new KnowledgeFormatException(file, lines.length, "missing header");
        }
        return new CsvTable(version, List.copyOf(rows));
    }

    private static @Nullable String readVersionLine(
            String file, int number, String line, @Nullable String version, boolean headerSeen) {
        final Matcher matcher = VERSION_LINE.matcher(line);
        if (!matcher.matches()) {
            return version;
        }
        if (version != null) {
            throw new KnowledgeFormatException(file, number, "duplicate data-version line");
        }
        if (headerSeen) {
            throw new KnowledgeFormatException(file, number, "data-version line after the header");
        }
        return matcher.group(1);
    }

    private static void requireVersionAndHeader(
            String file, int number, String line, String header, @Nullable String version) {
        if (version == null) {
            throw new KnowledgeFormatException(file, number, "missing data-version line before the header");
        }
        if (!line.equals(header)) {
            throw new KnowledgeFormatException(file, number, "wrong header, expected " + header);
        }
    }

    private static List<String> splitRow(String file, int number, String line, int columns) {
        final List<String> fields = new ArrayList<>();
        final StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        while (i < line.length()) {
            final char c = line.charAt(i);
            if (quoted && c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                field.append('"');
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
            i++;
        }
        if (quoted) {
            throw new KnowledgeFormatException(file, number, "unterminated quote");
        }
        fields.add(field.toString());
        if (fields.size() != columns) {
            throw new KnowledgeFormatException(
                    file, number, "expected " + columns + " columns but found " + fields.size());
        }
        return List.copyOf(fields);
    }
}
