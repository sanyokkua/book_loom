package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The detector-recall suite's measurement: per detector, how many of the segments the gold expects it on it caught
 * (recall) and how many of the read segments it fired on hold a defect (precision); per defect class, how many of its
 * segments any detector caught (recall) and how many of the read segments its expected detectors fired on are of that
 * class (precision). Only segments a gold line names count — an unread segment is neither clean nor defective. A rate
 * with an empty population is {@code null}, never a made-up 100 %. Detector and class names and counts are all it holds.
 *
 * @param books what the suite found in each book
 */
record RecallReport(List<RecallBookRun> books) {

    static final String SUITE = "recall";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Copies the books. */
    RecallReport {
        books = List.copyOf(books);
    }

    /**
     * One detector's numbers.
     *
     * @param name the detector, as its finding names it
     * @param fired read segments it fired on
     * @param truePositive of those, segments the gold calls defective
     * @param expected segments the gold expects it on
     * @param caught of those, segments it fired on
     */
    record Detector(String name, int fired, int truePositive, int expected, int caught) {

        @Nullable
        Double precision() {
            return rate(truePositive, fired);
        }

        @Nullable
        Double recall() {
            return rate(caught, expected);
        }
    }

    /**
     * One defect class's numbers.
     *
     * @param name the class, as the gold names it
     * @param segments read segments of the class
     * @param caught of those, segments any detector fired on
     * @param flagged read segments that one of the class's expected detectors fired on
     * @param truePositive of those, segments of the class
     */
    record DefectClass(String name, int segments, int caught, int flagged, int truePositive) {

        @Nullable
        Double recall() {
            return rate(caught, segments);
        }

        @Nullable
        Double precision() {
            return rate(truePositive, flagged);
        }
    }

    /** A segment the gold names, with its line. */
    private record Read(RecallSegment segment, RecallGold gold) {

        boolean fired(final String detector) {
            return segment.fired().contains(detector);
        }
    }

    List<Detector> detectors() {
        final List<Read> read = read();
        final TreeSet<String> names = new TreeSet<>();
        read.forEach(r -> {
            names.addAll(r.segment().fired());
            names.addAll(r.gold().expect());
        });
        return names.stream()
                .map(name -> new Detector(
                        name,
                        count(read, r -> r.fired(name)),
                        count(read, r -> r.fired(name) && r.gold().isDefective()),
                        count(read, r -> r.gold().expect().contains(name)),
                        count(read, r -> r.gold().expect().contains(name) && r.fired(name))))
                .toList();
    }

    List<DefectClass> classes() {
        final List<Read> read = read();
        final TreeSet<String> names = new TreeSet<>();
        read.forEach(r -> names.addAll(r.gold().classes()));
        return names.stream().map(name -> defectClass(read, name)).toList();
    }

    private static DefectClass defectClass(final List<Read> read, final String name) {
        final Predicate<Read> ofClass = r -> r.gold().classes().contains(name);
        final TreeSet<String> detectors = new TreeSet<>();
        read.stream().filter(ofClass).forEach(r -> detectors.addAll(r.gold().expect()));
        final Predicate<Read> flagged = r -> detectors.stream().anyMatch(r::fired);
        return new DefectClass(
                name,
                count(read, ofClass),
                count(read, ofClass.and(r -> !r.segment().fired().isEmpty())),
                count(read, flagged),
                count(read, flagged.and(ofClass)));
    }

    int aligned() {
        return books.stream().mapToInt(book -> book.segments().size()).sum();
    }

    int readCount() {
        return read().size();
    }

    int defective() {
        return count(read(), r -> r.gold().isDefective());
    }

    int unaligned() {
        return books.stream().mapToInt(RecallBookRun::unaligned).sum();
    }

    long staleGold() {
        return books.stream().mapToLong(RecallBookRun::staleGold).sum();
    }

    /** Defective read segments any detector fired on, over all defective read segments. */
    @Nullable
    Double recall() {
        return rate(
                count(
                        read(),
                        r -> r.gold().isDefective() && !r.segment().fired().isEmpty()),
                defective());
    }

    /** Clean read segments any detector fired on, over all clean read segments. */
    @Nullable
    Double falseAlarm() {
        final List<Read> read = read();
        return rate(
                count(read, r -> !r.gold().isDefective() && !r.segment().fired().isEmpty()),
                count(read, r -> !r.gold().isDefective()));
    }

    private List<Read> read() {
        return books.stream()
                .flatMap(book -> book.segments().stream()
                        .filter(segment -> book.gold().containsKey(segment.hash()))
                        .map(segment -> new Read(
                                segment, Objects.requireNonNull(book.gold().get(segment.hash()), "gold"))))
                .toList();
    }

    /** The table printed to the log and written beside the JSON. */
    String table() {
        return (summaryLine() + detectorLines() + classLines()).stripTrailing();
    }

    private String summaryLine() {
        return String.format(
                Locale.ROOT,
                "promptEval recall books=%d aligned=%d read=%d defective=%d unaligned=%d staleGold=%d recall=%s"
                        + " falseAlarm=%s%n",
                books.size(),
                aligned(),
                readCount(),
                defective(),
                unaligned(),
                staleGold(),
                percent(recall()),
                percent(falseAlarm()));
    }

    private String detectorLines() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "%-32s %6s %6s %9s %8s %6s%n",
                "detector",
                "fired",
                "tp",
                "precision",
                "expected",
                "recall"));
        detectors()
                .forEach(d -> out.append(String.format(
                        Locale.ROOT,
                        "%-32s %6d %6d %9s %8d %6s%n",
                        d.name(),
                        d.fired(),
                        d.truePositive(),
                        percent(d.precision()),
                        d.expected(),
                        percent(d.recall()))));
        return out.toString();
    }

    private String classLines() {
        final StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "%-32s %6s %6s %6s %7s %9s%n",
                "class",
                "segs",
                "caught",
                "recall",
                "flagged",
                "precision"));
        classes()
                .forEach(c -> out.append(String.format(
                        Locale.ROOT,
                        "%-32s %6d %6d %6s %7d %9s%n",
                        c.name(),
                        c.segments(),
                        c.caught(),
                        percent(c.recall()),
                        c.flagged(),
                        percent(c.precision()))));
        return out.toString();
    }

    /** The report as one JSON object; {@code suite} tells the matrix table which suite it is. */
    String json() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("suite", SUITE);
        fields.put("model", "none");
        fields.put("books", books.stream().map(this::bookFields).toList());
        fields.put("aligned", aligned());
        fields.put("read", readCount());
        fields.put("defective", defective());
        fields.put("unaligned", unaligned());
        fields.put("staleGold", staleGold());
        fields.put("recall", recall());
        fields.put("falseAlarm", falseAlarm());
        fields.put(
                "detectors",
                detectors().stream().map(RecallReport::detectorFields).toList());
        fields.put("classes", classes().stream().map(RecallReport::classFields).toList());
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> bookFields(final RecallBookRun book) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", book.name());
        fields.put("aligned", book.segments().size());
        fields.put("unaligned", book.unaligned());
        fields.put("gold", book.gold().size());
        fields.put("staleGold", book.staleGold());
        return fields;
    }

    private static Map<String, Object> detectorFields(final Detector detector) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", detector.name());
        fields.put("fired", detector.fired());
        fields.put("truePositive", detector.truePositive());
        fields.put("precision", detector.precision());
        fields.put("expected", detector.expected());
        fields.put("caught", detector.caught());
        fields.put("recall", detector.recall());
        return fields;
    }

    private static Map<String, Object> classFields(final DefectClass defectClass) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", defectClass.name());
        fields.put("segments", defectClass.segments());
        fields.put("caught", defectClass.caught());
        fields.put("recall", defectClass.recall());
        fields.put("flagged", defectClass.flagged());
        fields.put("truePositive", defectClass.truePositive());
        fields.put("precision", defectClass.precision());
        return fields;
    }

    private static int count(final List<Read> read, final Predicate<Read> hit) {
        return (int) read.stream().filter(hit).count();
    }

    private static @Nullable Double rate(final int hits, final int population) {
        return population == 0 ? null : (double) hits / population;
    }

    private static String percent(@Nullable final Double rate) {
        return rate == null ? "-" : String.format(Locale.ROOT, "%.0f%%", 100 * rate);
    }
}
