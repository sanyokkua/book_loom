package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What a book written now would contain, in the lines the Export screen shows.
 *
 * <p>Segments kept as source by choice are stated apart from untranslated ones: the desk's {@code pending} never
 * includes them, and lumping them in would make a finished book read as unfinished. A flagged segment with no machine
 * target is written in the source language, so it joins the untranslated line instead of the flagged one. Segments
 * kept as they are because they had nothing to translate — a chapter number, a scene break — have a line of their own:
 * nobody chose to keep them, so they are never stated as kept by choice.
 */
@Slf4j
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PartialExportStatement {

    /**
     * Words the counts.
     *
     * @param counts the review desk's counts for the project
     * @param messages the catalogue the lines come from
     * @return one line per non-zero figure, source-language first, then flagged, then kept by choice, then kept as is;
     *     empty when nothing needs stating
     */
    static List<String> lines(final ReviewCounts counts, final Messages messages) {
        final int inSource = counts.pending() + counts.flaggedWithoutTarget();
        final int flaggedWithMachine = counts.flagged() - counts.flaggedWithoutTarget();
        log.debug(
                "partial export: {} in the source language, {} flagged with a machine translation, {} kept as source,"
                        + " {} kept as is",
                inSource,
                flaggedWithMachine,
                counts.sourceKept(),
                counts.keptVerbatim());
        final List<String> lines = new ArrayList<>();
        add(lines, messages, MessageKey.EXPORT_PARTIAL_SOURCE, inSource);
        add(lines, messages, MessageKey.EXPORT_PARTIAL_FLAGGED, flaggedWithMachine);
        add(lines, messages, MessageKey.EXPORT_PARTIAL_KEPT, counts.sourceKept());
        add(lines, messages, MessageKey.EXPORT_PARTIAL_VERBATIM, counts.keptVerbatim());
        return lines;
    }

    private static void add(final List<String> lines, final Messages messages, final MessageKey key, final int count) {
        if (count > 0) {
            lines.add(messages.get(key, count));
        }
    }
}
