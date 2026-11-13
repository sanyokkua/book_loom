package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.eval.CaseRecord.Exchange;
import ua.bookloom.pipeline.eval.MiniScorer.Verdict;

/**
 * The {@code window-injection} grader (design D24): reads recorded translate-family calls and counts the map-block
 * entries none of whose forms occur in the call's window, and the block-8 entries ("Book so far", "Recently") whose
 * forms are absent from the book's text up to the window's end. It holds its own section names and its own letter
 * matching, so it never agrees with the prompt builder by sharing its mistake. A "Locked names" line names a token, not
 * a source name, so that block is not graded.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowInjectionGrader {

    private static final String USER_MARK = "[user] ";
    private static final List<String> MAP_HEADINGS =
            List.of("[Glossary", "[Suggested renderings", "[Established renderings", "[Characters");
    private static final List<String> BLOCK8_HEADINGS = List.of("[Book so far", "[Recently");
    private static final String PAIRS_HEADING = "[Previous pairs";
    private static final String NEXT_HEADING = "[Next item after the batch";
    private static final String SOURCE_PREFIX = "Source: ";
    // A numbered tag opens an item's own text ("[8] ..."), never a heading, so a next item's body stays in its section.
    private static final Pattern NUMBERED_TAG = Pattern.compile("^\\[\\d+\\]\\s");
    private static final Pattern TAG = Pattern.compile("</?s(?:\\s[^>]*)?>");
    private static final Pattern SOURCE_LINE = Pattern.compile("(?m)^Source: ");
    private static final Pattern ALSO = Pattern.compile("\\(also ([^)]*)\\)");
    private static final Pattern TRAILING_NOTE = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
    private static final Pattern COMMA = Pattern.compile(",");
    private static final Pattern ARROW = Pattern.compile("\\s[→—]\\s");

    private record Call(String items, List<String> windowLines, List<String> mapLines, List<String> block8Lines) {}

    static Verdict grade(final List<Exchange> calls, final List<String> bookTexts, final List<MiniGold.Entry> gold) {
        int mapChecked = 0;
        int block8Checked = 0;
        final List<String> mapOutside = new ArrayList<>();
        final List<String> block8Outside = new ArrayList<>();
        for (final Exchange exchange : calls) {
            final Call call = parse(exchange.prompt());
            if (call == null) {
                continue;
            }
            final String window = window(call);
            for (final String name : names(call.mapLines())) {
                mapChecked++;
                if (!occurs(forms(name, gold), window)) {
                    mapOutside.add(name);
                }
            }
            final String upToEnd = String.join("\n", bookTexts.subList(0, lastPosition(call, bookTexts) + 1));
            for (final String line : call.block8Lines()) {
                block8Checked++;
                block8Outside.addAll(unseen(line, upToEnd, gold));
            }
        }
        log.debug("window injection mapChecked={} block8Checked={}", mapChecked, block8Checked);
        final String detail = "mapChecked=" + mapChecked + " mapOutside=" + mapOutside.size() + " " + mapOutside
                + " block8Checked=" + block8Checked + " block8Outside=" + block8Outside.size() + " " + block8Outside;
        return new Verdict(mapOutside.isEmpty() && block8Outside.isEmpty(), detail);
    }

    private static Call parse(final String prompt) {
        final int at = prompt.indexOf(USER_MARK);
        if (at < 0) {
            return null;
        }
        final String user = prompt.substring(at + USER_MARK.length());
        final String items = between(user, "<Items>", "</Items>");
        final String text = items.isEmpty() ? between(user, "<Text>", "</Text>") : items;
        if (text.isEmpty()) {
            return null;
        }
        final List<String> windowLines = new ArrayList<>();
        // A proofread item shows its source after "Source: ", so the window's end is found by the source alone.
        windowLines.add(SOURCE_LINE.matcher(TAG.matcher(text).replaceAll("")).replaceAll(""));
        sections(user, List.of(PAIRS_HEADING)).stream()
                .filter(l -> l.startsWith(SOURCE_PREFIX))
                .map(l -> TAG.matcher(l.substring(SOURCE_PREFIX.length())).replaceAll(""))
                .forEach(windowLines::add);
        windowLines.addAll(sections(user, List.of(NEXT_HEADING)));
        return new Call(text, windowLines, sections(user, MAP_HEADINGS), sections(user, BLOCK8_HEADINGS));
    }

    private static String between(final String text, final String open, final String close) {
        final int from = text.indexOf(open);
        final int to = text.indexOf(close);
        return from < 0 || to < from
                ? ""
                : text.substring(from + open.length(), to).strip();
    }

    /** The non-blank body lines of every section whose heading starts with one of {@code headings}. */
    private static List<String> sections(final String user, final List<String> headings) {
        final List<String> lines = new ArrayList<>();
        boolean inside = false;
        for (final String line : user.split("\n", -1)) {
            if (line.startsWith("[") && !NUMBERED_TAG.matcher(line).find()) {
                inside = headings.stream().anyMatch(line::startsWith);
            } else if (line.isBlank()) {
                inside = false;
            } else if (inside) {
                lines.add(line.strip());
            }
        }
        return lines;
    }

    private static String window(final Call call) {
        return String.join("\n", call.windowLines());
    }

    private static List<String> names(final List<String> mapLines) {
        final List<String> names = new ArrayList<>();
        for (final String line : mapLines) {
            final String head =
                    ARROW.split(line, 2)[0].replaceFirst("^[-*•]\\s*", "").strip();
            final Matcher also = ALSO.matcher(head);
            final String main =
                    TRAILING_NOTE.matcher(also.replaceAll("")).replaceAll("").strip();
            if (!main.isEmpty() && !line.startsWith("Use each") && !line.startsWith("Write each")) {
                names.add(main);
            }
            also.reset();
            while (also.find()) {
                COMMA.splitAsStream(also.group(1)).map(String::strip).forEach(names::add);
            }
        }
        return names;
    }

    private static List<String> forms(final String name, final List<MiniGold.Entry> gold) {
        final String lower = name.toLowerCase(Locale.ROOT);
        final List<String> found = gold.stream()
                .filter(e -> e.lemma().toLowerCase(Locale.ROOT).equals(lower)
                        || e.forms().stream()
                                .anyMatch(f -> f.toLowerCase(Locale.ROOT).equals(lower)))
                .flatMap(e -> allForms(e).stream())
                .distinct()
                .toList();
        return found.isEmpty() ? List.of(name) : found;
    }

    /** The gold names a block-8 line mentions that the book has not yet said by the window's end. */
    private static List<String> unseen(final String line, final String upToEnd, final List<MiniGold.Entry> gold) {
        return gold.stream()
                .filter(e -> occurs(allForms(e), line) && !occurs(allForms(e), upToEnd))
                .map(MiniGold.Entry::lemma)
                .toList();
    }

    private static List<String> allForms(final MiniGold.Entry entry) {
        return Stream.concat(Stream.of(entry.lemma()), entry.forms().stream()).toList();
    }

    private static boolean occurs(final List<String> forms, final String text) {
        return forms.stream()
                .anyMatch(f -> Pattern.compile(
                                "(?<![\\p{L}])" + Pattern.quote(f) + "(?![\\p{L}])",
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(text)
                        .find());
    }

    /** The highest book position holding one of the window's source lines. */
    private static int lastPosition(final Call call, final List<String> bookTexts) {
        int last = 0;
        for (final String line : call.windowLines()) {
            for (final String part : line.lines().toList()) {
                for (int i = bookTexts.size() - 1; i > last && !part.isBlank(); i--) {
                    if (bookTexts.get(i).contains(part.strip())) {
                        last = i;
                    }
                }
            }
        }
        log.trace("window ends at position {} of {}", last, bookTexts.size());
        return last;
    }
}
