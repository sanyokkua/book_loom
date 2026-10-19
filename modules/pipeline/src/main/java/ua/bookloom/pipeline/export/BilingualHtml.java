package ua.bookloom.pipeline.export;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.DisplayText;

/**
 * The bilingual copy: one table row per segment, its source beside the text written for it. It is self-contained —
 * inline style, no script, no link and no reference to any file or address — so it opens offline and leaks nothing.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BilingualHtml {

    private static final String HEAD_BEFORE_TITLE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <title>""";
    private static final String HEAD_AFTER_TITLE = """
            </title>
            <style>
            body { font-family: serif; margin: 2em; }
            table { border-collapse: collapse; width: 100%; }
            td { border: 1px solid #999999; padding: 0.4em; vertical-align: top; width: 50%; }
            </style>
            </head>
            <body>
            <table>
            """;
    private static final String TAIL = "</table>\n</body>\n</html>\n";

    /**
     * Builds the copy of a written book.
     *
     * @param title the non-null name the page is titled with, the written book's file name
     * @param targets the non-null book as written, with each written target's masked form
     * @return the page's text
     */
    static String of(final String title, final EffectiveTargets targets) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(targets, "targets");
        final StringBuilder html =
                new StringBuilder(HEAD_BEFORE_TITLE).append(escaped(title)).append(HEAD_AFTER_TITLE);
        targets.document().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .forEach(segment -> html.append("<tr><td>")
                        .append(escaped(DisplayText.of(segment.masked())))
                        .append("</td><td>")
                        .append(escaped(written(segment, targets)))
                        .append("</td></tr>\n"));
        log.debug("Bilingual copy built title={} length={}", title, html.length());
        return html.append(TAIL).toString();
    }

    private static String written(final Segment segment, final EffectiveTargets targets) {
        final String masked = targets.maskedTargets().get(segment.id());
        if (masked != null) {
            return DisplayText.of(masked);
        }
        final String target = segment.targetInner();
        return target != null ? target : DisplayText.of(segment.masked());
    }

    private static String escaped(final String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
