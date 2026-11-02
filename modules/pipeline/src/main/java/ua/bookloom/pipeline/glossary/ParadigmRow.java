package ua.bookloom.pipeline.glossary;

import java.util.Optional;

/**
 * A line of a name paradigm, {@code ending|gender: endings} or {@code ending|gender>voc: endings}. Parsing by hand
 * stands in for the regular expression {@code (\S+)\|(m|f|any)(>voc)?:\s*(.+)}, which retries every bar of a long
 * word.
 *
 * @param ending the part before the bar
 * @param gender {@code m}, {@code f} or {@code any}
 * @param vocative whether the row is a vocative row
 * @param endings what follows the colon, not blank
 */
record ParadigmRow(String ending, String gender, boolean vocative, String endings) {

    private static final String[] GENDERS = {"m", "f", "any"};
    private static final String VOCATIVE = ">voc";
    private static final String LINE_BREAKS = "\n\r\u0085  ";

    /**
     * Reads a row.
     *
     * @param line the stripped line
     * @return the row, or empty when the line has another shape
     */
    static Optional<ParadigmRow> parse(final String line) {
        if (line.chars().anyMatch(c -> LINE_BREAKS.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        int tokenEnd = 0;
        while (tokenEnd < line.length() && !Character.isWhitespace(line.charAt(tokenEnd))) {
            tokenEnd++;
        }
        for (int bar = line.lastIndexOf('|', tokenEnd - 1); bar > 0; bar = line.lastIndexOf('|', bar - 1)) {
            final Optional<ParadigmRow> row = afterBar(line, bar);
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    private static Optional<ParadigmRow> afterBar(final String line, final int bar) {
        for (final String gender : GENDERS) {
            if (!line.startsWith(gender, bar + 1)) {
                continue;
            }
            int at = bar + 1 + gender.length();
            final boolean vocative = line.startsWith(VOCATIVE, at);
            at += vocative ? VOCATIVE.length() : 0;
            if (at < line.length() && line.charAt(at) == ':') {
                final String endings = line.substring(at + 1).stripLeading();
                return endings.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new ParadigmRow(line.substring(0, bar), gender, vocative, endings));
            }
        }
        return Optional.empty();
    }
}
