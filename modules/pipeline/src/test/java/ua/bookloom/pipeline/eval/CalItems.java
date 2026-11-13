package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * The judge calibration set (design D24, arm 1): invented single translations and pairs of one source, each in its
 * split. The file says nothing about the intended defect or the better side, so a grader grades blind; the labels live
 * apart in {@code labels.json}, written by the graders.
 *
 * @param id the dataset id, such as {@code cal-1}
 * @param items every item in file order
 */
@Slf4j
record CalItems(String id, List<Item> items) {

    static final String CAL_1 = "cal-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern WORD_GAP = Pattern.compile("\\s+");
    private static final String TARGET_LANGUAGE = "uk";

    /** Copies the items. */
    CalItems {
        Objects.requireNonNull(id, "id");
        items = List.copyOf(items);
    }

    /** What an item asks the grader to judge, with its word in the file. */
    enum Kind {
        /** One translation: a meaning error yes or no and a score 0–5. */
        SINGLE("single"),
        /** Two translations of one source: A, B or tie. */
        PAIR("pair");

        private final String wire;

        Kind(final String wire) {
            this.wire = wire;
        }

        String wire() {
            return wire;
        }

        static Kind of(final String word) {
            return switch (word) {
                case "single" -> SINGLE;
                case "pair" -> PAIR;
                default -> throw new IllegalArgumentException("unknown item kind " + word);
            };
        }
    }

    /**
     * One calibration item.
     *
     * @param id the item id, such as {@code cal-s01}
     * @param kind single or pair
     * @param split {@code few-shot}, {@code dev} or {@code test}
     * @param pair the language pair, {@code en-uk} or {@code ru-uk}
     * @param source the source text
     * @param translations one translation for a single item, A then B for a pair
     * @param neighbours the neighbouring source paragraphs, context only; empty when none
     * @param goldTerms the required renderings the source uses; empty when none
     */
    record Item(
            String id,
            Kind kind,
            String split,
            String pair,
            String source,
            List<String> translations,
            List<String> neighbours,
            List<JudgeItem.GoldTerm> goldTerms) {

        /** Rejects a missing part and copies the lists. */
        Item {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(split, "split");
            Objects.requireNonNull(pair, "pair");
            Objects.requireNonNull(source, "source");
            translations = List.copyOf(translations);
            neighbours = List.copyOf(neighbours);
            goldTerms = List.copyOf(goldTerms);
        }

        /** The source's word count, split on white space. */
        int words() {
            return WORD_GAP.split(source.strip()).length;
        }

        /** What the judge is shown besides the translations. */
        JudgeItem judgeItem() {
            final String sourceLanguage = pair.substring(0, pair.indexOf('-'));
            return new JudgeItem(id, sourceLanguage, TARGET_LANGUAGE, source, goldTerms, neighbours);
        }
    }

    /**
     * The graders' labels: per grader id, the label of each item it graded.
     *
     * @param graders grader id to item id to the label as written
     * @param unlabelled the ids a grader could not label, listed so they are counted as left out
     */
    record Labels(Map<String, Map<String, JsonNode>> graders, Set<String> unlabelled) {

        /** Copies the maps. */
        Labels {
            graders = Map.copyOf(graders);
            unlabelled = Set.copyOf(unlabelled);
        }

        /** Whether every grader labelled {@code itemId} (and there are two of them). */
        boolean hasBothGraders(final String itemId) {
            return graders.size() == 2 && graders.values().stream().allMatch(g -> g.containsKey(itemId));
        }
    }

    /** The bundled set {@code /eval/datasets/<id>/items.json}. */
    static CalItems loadBundled(final String datasetId) {
        Objects.requireNonNull(datasetId, "datasetId");
        final JsonNode root =
                read(datasetId, "items.json").orElseThrow(() -> new IllegalStateException("no items: " + datasetId));
        final List<Item> items = stream(root.get("items")).map(CalItems::item).toList();
        log.debug("Calibration items loaded dataset={} items={}", datasetId, items.size());
        return new CalItems(datasetId, items);
    }

    /** The bundled labels of the set, or empty while the graders have not written them. */
    static Optional<Labels> loadBundledLabels(final String datasetId) {
        Objects.requireNonNull(datasetId, "datasetId");
        return read(datasetId, "labels.json").map(root -> {
            final Map<String, Map<String, JsonNode>> graders = root.get("graders").properties().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> labelsOf(e.getValue())));
            final Set<String> unlabelled = root.has("unlabelled")
                    ? stream(root.get("unlabelled")).map(JsonNode::asText).collect(Collectors.toSet())
                    : Set.of();
            log.debug("Calibration labels loaded dataset={} graders={}", datasetId, graders.keySet());
            return new Labels(graders, unlabelled);
        });
    }

    /** The items whose split is one of {@code splits}, in file order. */
    List<Item> inSplits(final Set<String> splits) {
        Objects.requireNonNull(splits, "splits");
        return items.stream().filter(i -> splits.contains(i.split())).toList();
    }

    private static Map<String, JsonNode> labelsOf(final JsonNode grader) {
        return grader.properties().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Item item(final JsonNode node) {
        final Kind kind = Kind.of(node.get("kind").asText());
        final List<String> translations = kind == Kind.PAIR
                ? List.of(node.get("a").asText(), node.get("b").asText())
                : List.of(node.get("translation").asText());
        return new Item(
                node.get("id").asText(),
                kind,
                node.get("split").asText(),
                node.get("pair").asText(),
                node.get("source").asText(),
                translations,
                stream(node.path("neighbours")).map(JsonNode::asText).toList(),
                stream(node.path("goldTerms"))
                        .map(t -> new JudgeItem.GoldTerm(
                                t.get("source").asText(), t.get("target").asText()))
                        .toList());
    }

    private static Stream<JsonNode> stream(final JsonNode array) {
        return array.valueStream();
    }

    private static Optional<JsonNode> read(final String datasetId, final String file) {
        final String resource = "/eval/datasets/" + datasetId + "/" + file;
        try (InputStream in = CalItems.class.getResourceAsStream(resource)) {
            return in == null ? Optional.empty() : Optional.of(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
