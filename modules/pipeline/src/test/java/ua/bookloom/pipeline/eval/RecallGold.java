package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.util.hash.HashUtil;

/**
 * One line of an owner-local {@code gold.jsonl}: a segment a person (or a reading the person checked) has judged,
 * named by the hash of its source and exported text so the file holds no book text, with the defect classes found in
 * it (none for a clean segment) and the detectors that should have caught it.
 *
 * @param hash the segment's {@link #hash} — a new export of other text matches no line, so stale gold shows up
 * @param classes the defect classes, free words of the reading ({@code dropped-sentence}, {@code wrong-gender} …);
 *     empty for a segment read and found clean
 * @param expect the detector names that should fire, as the report spells them ({@code sentence-count},
 *     {@code audit:sentence-count}); empty when no deterministic detector is expected to see the defect
 */
record RecallGold(String hash, List<String> classes, List<String> expect) {

    /** The gold file a book directory holds. */
    static final String FILE = "gold.jsonl";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SEPARATOR = "\u001F";

    /** Copies the lists. */
    RecallGold {
        Objects.requireNonNull(hash, "hash");
        classes = List.copyOf(classes);
        expect = List.copyOf(expect);
    }

    /** Whether the reading found a defect in the segment. */
    boolean isDefective() {
        return !classes.isEmpty();
    }

    /**
     * The hash a gold line names a segment by: SHA-256 of the NFC form of the source's display text, the unit
     * separator U+001F and the exported text's display text, in lower-case hex.
     */
    static String hash(final String sourceDisplay, final String targetDisplay) {
        return HashUtil.sha256OfNfcText(sourceDisplay + SEPARATOR + targetDisplay);
    }

    /**
     * Reads a gold file; blank lines are skipped and a malformed or repeated line fails with its line number.
     *
     * @return the lines by hash in file order; empty when the file does not exist
     */
    static Map<String, RecallGold> read(final Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final Map<String, RecallGold> gold = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                final RecallGold line = parse(lines.get(i), i + 1);
                if (gold.putIfAbsent(line.hash(), line) != null) {
                    throw new IllegalArgumentException(FILE + " line " + (i + 1) + " repeats hash " + line.hash());
                }
            }
        }
        return gold;
    }

    private static RecallGold parse(final String line, final int number) {
        try {
            final JsonNode json = MAPPER.readTree(line);
            final String hash = json.path("hash").asText("");
            if (hash.isEmpty() || !json.path("classes").isArray()) {
                throw new IllegalArgumentException(FILE + " line " + number + " needs \"hash\" and \"classes\"");
            }
            return new RecallGold(hash, texts(json.path("classes")), texts(json.path("expect")));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(FILE + " line " + number + " is not JSON", e);
        }
    }

    private static List<String> texts(final JsonNode array) {
        final List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.asText()));
        return texts;
    }
}
