package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * The segments a paired arm (design D24) is graded on, written down before the arm runs. An id is
 * {@code <dataset>/<segment id>}: a mini book's block id ({@code mini-en/m01}, {@code mini-ru/ch01.md:3}) or a line of the
 * sequence book ({@code sequence/book.md:1}, the block's index as the Markdown importer names it; block 0 is the
 * chapter heading).
 *
 * @param arm the arm's number
 * @param about how the list was chosen
 * @param ids the segment ids in reading order, each once
 */
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
record ArmSegments(int arm, String about, List<String> ids) {

    /** Arm 2 (task 2.2): every segment of both mini books, then the first ten paragraphs of the sequence chapter 1. */
    static final String ARM_TWO = "/eval/arms/arm-2-segments.json";

    /** The dataset an id belongs to, before its first {@code /}. */
    static final char DATASET_END = '/';

    /** Copies the ids. */
    ArmSegments {
        Objects.requireNonNull(about, "about");
        ids = List.copyOf(ids);
    }

    /** The list a test resource holds. */
    static ArmSegments load(final String resource) {
        try (InputStream in = ArmSegments.class.getResourceAsStream(resource)) {
            final ArmSegments segments =
                    JsonMapper.builder().build().readValue(Objects.requireNonNull(in, resource), ArmSegments.class);
            log.debug(
                    "Arm segments resource={} arm={} ids={}",
                    resource,
                    segments.arm(),
                    segments.ids().size());
            return segments;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The ids of one dataset, in order. */
    List<String> ofDataset(final String dataset) {
        return ids.stream().filter(id -> dataset(id).equals(dataset)).toList();
    }

    /** The dataset an id names. */
    static String dataset(final String id) {
        return id.substring(0, id.indexOf(DATASET_END));
    }

    /**
     * The start of a Markdown book up to and including its {@code paragraphs}-th paragraph after the first heading: the
     * part of the sequence book an arm imports, so its segments keep the ids they have in the whole book.
     *
     * @param book the book's text
     * @param paragraphs how many paragraphs to keep, at least one
     * @return the lines up to the last line of that paragraph, joined with {@code \n} and ending with one
     */
    static String sequenceHead(final String book, final int paragraphs) {
        final List<String> lines = book.lines().toList();
        int seen = 0;
        int end = 0;
        boolean inParagraph = false;
        for (int i = 1; i < lines.size() && seen <= paragraphs; i++) {
            final boolean blank = lines.get(i).isBlank();
            if (!blank && !inParagraph) {
                seen++;
            }
            if (!blank && seen <= paragraphs) {
                end = i;
            }
            inParagraph = !blank;
        }
        log.debug("Sequence head paragraphs={} lines={}", paragraphs, end + 1);
        return lines.subList(0, end + 1).stream().collect(Collectors.joining("\n", "", "\n"));
    }
}
