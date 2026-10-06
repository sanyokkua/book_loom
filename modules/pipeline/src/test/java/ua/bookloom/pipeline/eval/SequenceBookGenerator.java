package ua.bookloom.pipeline.eval;

import static ua.bookloom.pipeline.eval.SequenceBookLibrary.NAME_SPEECH;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the synthetic English book the sequence eval translates: eight chapters of forty paragraphs, three of them
 * narrated in the first person by a male spirit, built from {@link SequenceBookLibrary}'s sentences and slots. A fixed
 * seed makes the output identical on every run, so the committed {@code book.md} can be checked against it. The shape
 * follows a real 3,783-segment run: speech in a third of the paragraphs in six quote styles (a dozen mixing straight and
 * curly marks), long paragraphs, short lines, and the recurring titles and names at the density that made them drift.
 * Run {@link #main} with a path to rewrite the committed file after changing a pool.
 */
final class SequenceBookGenerator {

    static final long SEED = 20261006L;
    static final int CHAPTERS = 8;

    private static final Set<Integer> FIRST_PERSON = Set.of(1, 4, 6);
    private static final Set<Integer> VERSE_CHAPTERS = Set.of(0, 2, 5);
    private static final int LONG_PER_CHAPTER = 5;
    private static final int SHORT_PER_CHAPTER = 7;
    private static final int DIALOGUE_PER_CHAPTER = 14;
    private static final int BODY_PER_CHAPTER = 38;
    private static final int MEDIUM_MIN = 46;
    private static final int MEDIUM_SPAN = 24;
    private static final int LONG_MIN = 84;
    private static final int LONG_SPAN = 24;
    private static final int FIRST_PERSON_SCENE_PERCENT = 65;
    private static final int RETRIES = 30;
    private static final int MAX_FILL_PASSES = 4;
    private static final int INLINE_SPEECH_PERCENT = 30;
    private static final int NAME_SPEECH_PERCENT = 25;
    private static final int NARRATOR_TAG_PERCENT = 50;
    private static final int TERM_SENTENCE_ONE_IN = 3;
    private static final int TAG_VARIANTS = 4;
    private static final int MIXED_VARIANTS = 3;
    private static final Pattern SLOT = Pattern.compile("\\{([A-Z0-9]+)}");
    private static final List<String> SAY = List.of("said", "replied", "answered", "whispered", "muttered");
    private static final List<String> ASK = List.of("asked", "demanded");
    private static final List<SequenceTopic> CYCLE = List.of(
            SequenceTopic.MR,
            SequenceTopic.BOY,
            SequenceTopic.MASTER,
            SequenceTopic.MRS,
            SequenceTopic.IMP,
            SequenceTopic.MS,
            SequenceTopic.MAGICIAN,
            SequenceTopic.MR,
            SequenceTopic.MASTER,
            SequenceTopic.BOY,
            SequenceTopic.MRS,
            SequenceTopic.MS,
            SequenceTopic.IMP,
            SequenceTopic.MAGICIAN,
            SequenceTopic.SIR,
            SequenceTopic.PENTACLE,
            SequenceTopic.CIRCLE);
    private static final Map<String, String> ANCHORS = Map.of(
            "0:DIALOGUE",
            "\"Do not trouble yourself, magician,\" said the imp. \"I only came to look at the master of the house.\"",
            "1:MEDIUM",
            "I have been summoned by better magicians than this one, and by worse. I laughed. A boy who gives orders"
                    + " to an imp while standing in the dark is either very brave or very foolish.",
            "4:MEDIUM",
            "I am Bartimaeus, and I do not enjoy being kept waiting in a circle. The boy stared at me as though I"
                    + " might bite, and I had no such plan, not yet.",
            "2:MEDIUM",
            "The morning brought no rain and no peace. Mr Underwood sat at the long table with a letter in his hand.");

    private enum Kind {
        MEDIUM,
        LONG,
        SHORT,
        DIALOGUE,
        VERSE,
        FOOTNOTE
    }

    private enum Style {
        STRAIGHT(36),
        CURLY(36),
        NESTED(12),
        EMDASH(16),
        MIXED(12);

        private final int count;

        Style(final int count) {
            this.count = count;
        }
    }

    private final Random random;
    private final Set<String> used = new HashSet<>();
    private final Map<String, String> anchors = new HashMap<>(ANCHORS);
    private final Deque<Style> styles = new ArrayDeque<>();
    private final List<String> shorts;
    private int topicCursor;
    private int shortCursor;
    private int verseCursor;

    private SequenceBookGenerator(final long seed) {
        this.random = new Random(seed);
        final List<Style> deck = new ArrayList<>();
        for (final Style style : Style.values()) {
            deck.addAll(Collections.nCopies(style.count, style));
        }
        Collections.shuffle(deck, random);
        styles.addAll(deck);
        shorts = new ArrayList<>(SequenceBookLibrary.SHORT);
        Collections.shuffle(shorts, random);
    }

    /**
     * Generates the book.
     *
     * @return the Markdown text, identical on every call
     */
    static String generate() {
        return new SequenceBookGenerator(SEED).book();
    }

    /**
     * Writes the generated book to the given path.
     *
     * @param args one argument, the file to write
     * @throws IOException if the file cannot be written
     */
    public static void main(final String[] args) throws IOException {
        Files.writeString(Path.of(args[0]), generate(), StandardCharsets.UTF_8);
    }

    private String book() {
        final StringBuilder out = new StringBuilder();
        for (int chapter = 0; chapter < CHAPTERS; chapter++) {
            out.append("# ").append(SequenceBookLibrary.TITLES.get(chapter)).append("\n\n");
            chapter(chapter).forEach(paragraph -> out.append(paragraph).append("\n\n"));
        }
        return out.toString().stripTrailing() + "\n";
    }

    private List<String> chapter(final int chapter) {
        final boolean first = FIRST_PERSON.contains(chapter);
        final List<Kind> kinds = kindsOf(chapter);
        final List<String> paragraphs = new ArrayList<>();
        for (final Kind kind : kinds) {
            paragraphs.add(kind == Kind.FOOTNOTE ? footnote(chapter) : paragraph(chapter, kind, first));
        }
        final int target = kinds.indexOf(Kind.LONG);
        paragraphs.set(target, paragraphs.get(target) + marker(chapter + 1));
        return paragraphs;
    }

    private List<Kind> kindsOf(final int chapter) {
        final List<Kind> body = new ArrayList<>();
        body.addAll(Collections.nCopies(LONG_PER_CHAPTER, Kind.LONG));
        body.addAll(Collections.nCopies(SHORT_PER_CHAPTER, Kind.SHORT));
        body.addAll(Collections.nCopies(DIALOGUE_PER_CHAPTER, Kind.DIALOGUE));
        body.add(VERSE_CHAPTERS.contains(chapter) ? Kind.VERSE : Kind.MEDIUM);
        body.addAll(Collections.nCopies(BODY_PER_CHAPTER - body.size(), Kind.MEDIUM));
        Collections.shuffle(body, random);
        final List<Kind> kinds = new ArrayList<>();
        kinds.add(Kind.MEDIUM);
        kinds.addAll(body);
        kinds.add(Kind.FOOTNOTE);
        return kinds;
    }

    private String paragraph(final int chapter, final Kind kind, final boolean first) {
        final String anchor = anchors.remove(chapter + ":" + kind);
        if (anchor != null) {
            used.add(anchor);
            return anchor;
        }
        return switch (kind) {
            case SHORT -> shorts.get(shortCursor++ % shorts.size());
            case VERSE -> SequenceBookLibrary.VERSE.get(verseCursor++ % SequenceBookLibrary.VERSE.size());
            case MEDIUM -> unique(() -> medium(first));
            case LONG -> unique(() -> longParagraph(first));
            case DIALOGUE -> unique(() -> dialogue(first));
            case FOOTNOTE -> throw new IllegalStateException("a footnote is written by its chapter");
        };
    }

    private String unique(final Supplier<String> make) {
        String text = make.get();
        for (int attempt = 0; attempt < RETRIES && !used.add(text); attempt++) {
            text = make.get();
        }
        return text;
    }

    private static String footnote(final int chapter) {
        return "[" + (chapter + 1) + "] " + SequenceBookLibrary.FOOTNOTE.get(chapter);
    }

    private static String marker(final int number) {
        return number % 2 == 0 ? "[" + number + "](#n" + number + ")" : "[" + number + "]";
    }

    private String medium(final boolean first) {
        final StringBuilder text = new StringBuilder(scene(first));
        add(text, this::termNarration);
        final int target = MEDIUM_MIN + random.nextInt(MEDIUM_SPAN);
        while (words(text) < target) {
            add(text, () -> random.nextInt(TERM_SENTENCE_ONE_IN) == 0 ? termNarration() : scene(first));
        }
        return text.toString();
    }

    private String longParagraph(final boolean first) {
        final StringBuilder text = new StringBuilder(scene(first));
        final boolean quoted = random.nextInt(100) < INLINE_SPEECH_PERCENT;
        final int target = LONG_MIN + random.nextInt(LONG_SPAN);
        for (int turn = 1; words(text) < target; turn++) {
            final boolean breather = turn % TERM_SENTENCE_ONE_IN == 0;
            add(text, () -> !breather ? termNarration() : quoted ? inlineSpeech(first) : scene(first));
        }
        return text.toString();
    }

    // A sentence the paragraph already holds is drawn again, so no paragraph repeats itself.
    private static void add(final StringBuilder text, final Supplier<String> sentence) {
        String next = sentence.get();
        for (int attempt = 0; attempt < RETRIES && text.indexOf(next) >= 0; attempt++) {
            next = sentence.get();
        }
        text.append(' ').append(next);
    }

    private String inlineSpeech(final boolean first) {
        final String speech = termSpeech();
        final boolean curly = random.nextBoolean();
        return (curly ? "“" : "\"") + lead(speech) + (curly ? "”" : "\"") + " " + tag(speech, first) + ".";
    }

    private String dialogue(final boolean first) {
        final Style style = styles.pop();
        final String one = termSpeech();
        final String two = random.nextBoolean() ? termSpeech() : genericSpeech();
        return switch (style) {
            case STRAIGHT -> framed("\"", "\"", one, two, first);
            case CURLY -> framed("“", "”", one, two, first);
            case NESTED -> nested(random.nextBoolean(), one);
            case EMDASH -> "— " + lead(one) + " — " + tag(one, first) + ". — " + two;
            case MIXED -> mixed(one, two, first);
        };
    }

    private String framed(
            final String open, final String close, final String one, final String two, final boolean first) {
        final String tag = tag(one, first);
        return switch (random.nextInt(TAG_VARIANTS)) {
            case 0 -> open + lead(one) + close + " " + tag + ".";
            case 1 -> capital(tag) + ", " + open + one + close;
            case 2 -> open + lead(one) + close + " " + tag + ". " + open + two + close;
            default -> open + one + close + " " + open + two + close;
        };
    }

    private String nested(final boolean curly, final String speech) {
        final String outerOpen = curly ? "“" : "\"";
        final String outerClose = curly ? "”" : "\"";
        final String innerOpen = curly ? "‘" : "'";
        final String innerClose = curly ? "’" : "'";
        return outerOpen + pick(SequenceBookLibrary.NEST_PRE) + " " + innerOpen + speech + innerClose + " "
                + pick(SequenceBookLibrary.NEST_POST) + outerClose;
    }

    private String mixed(final String one, final String two, final boolean first) {
        return switch (random.nextInt(MIXED_VARIANTS)) {
            case 0 -> "“" + lead(one) + "\" " + tag(one, first) + ".";
            case 1 -> "\"" + lead(one) + "” " + tag(one, first) + ". “" + two + "\"";
            default -> "“" + one + " " + two + "\"";
        };
    }

    private String tag(final String speech, final boolean first) {
        final String speaker =
                first && random.nextInt(100) < NARRATOR_TAG_PERCENT ? "I" : fill(pick(SequenceBookLibrary.SPEAKER));
        return speaker + " " + pick(speech.endsWith("?") ? ASK : SAY);
    }

    private String scene(final boolean first) {
        final boolean narrator = first && random.nextInt(100) < FIRST_PERSON_SCENE_PERCENT;
        return fill(pick(narrator ? SequenceBookLibrary.SCENE1 : SequenceBookLibrary.SCENE3));
    }

    private SequenceTopic nextTopic() {
        return CYCLE.get(topicCursor++ % CYCLE.size());
    }

    private String termNarration() {
        return fill(pick(nextTopic().narration()));
    }

    private String termSpeech() {
        return fill(pick(nextTopic().speech()));
    }

    private String genericSpeech() {
        final boolean named = random.nextInt(100) < NAME_SPEECH_PERCENT;
        return fill(pick(named ? NAME_SPEECH : SequenceBookLibrary.SPEECH));
    }

    private String fill(final String template) {
        String text = template;
        for (int pass = 0; pass < MAX_FILL_PASSES && text.indexOf('{') >= 0; pass++) {
            text = SLOT.matcher(text).replaceAll(slot -> Matcher.quoteReplacement(pick(pool(slot.group(1)))));
        }
        return text;
    }

    private static List<String> pool(final String slot) {
        return switch (slot) {
            case "OBJ" -> SequenceBookLibrary.OBJ;
            case "ADJ" -> SequenceBookLibrary.ADJ;
            case "PLACE" -> SequenceBookLibrary.PLACE;
            case "TIME" -> SequenceBookLibrary.TIME;
            case "NUMPHRASE" -> SequenceBookLibrary.NUMPHRASE;
            case "NAME" -> SequenceBookLibrary.NAME;
            case "MR" -> SequenceBookLibrary.MR;
            case "MRS" -> SequenceBookLibrary.MRS;
            case "MS" -> SequenceBookLibrary.MS;
            case "LATIN" -> SequenceBookLibrary.LATIN;
            case "EM" -> SequenceBookLibrary.EM;
            default -> throw new IllegalStateException("no pool for slot " + slot);
        };
    }

    private String pick(final List<String> pool) {
        return pool.get(random.nextInt(pool.size()));
    }

    private static String lead(final String speech) {
        return speech.endsWith(".") ? speech.substring(0, speech.length() - 1) + "," : speech;
    }

    private static String capital(final String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int words(final CharSequence text) {
        return text.toString().split("\\s+").length;
    }
}
