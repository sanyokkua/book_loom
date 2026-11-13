package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.eval.CalItems.Item;
import ua.bookloom.pipeline.eval.JudgeReply.Scored;
import ua.bookloom.pipeline.eval.JudgeReply.Scores;
import ua.bookloom.pipeline.eval.JudgeReply.Winner;

/**
 * Arm 1 of design D24: the local judge grades the calibration items, and the report carries each item's judgement in
 * the shape {@code scripts/eval-score.py calibrate} compares with the graders' labels. A single item's {@code score} is
 * the mean of the judge's four scores, the one figure set beside the graders' overall 0–5 score. An item whose reply
 * could not be read is left out of {@code cases} and named in {@code unparsed}: an unread reply counted as "no meaning
 * error" would flatter the judge's true-negative rate.
 */
@Slf4j
final class JudgeCalibration {

    static final String SUITE = "cal";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double SCORE_COUNT = 4.0;

    private final JudgeRunner judge;

    JudgeCalibration(final JudgeRunner judge) {
        this.judge = Objects.requireNonNull(judge, "judge");
    }

    /**
     * One item's judgement as the report writes it.
     *
     * @param item the judged item
     * @param judgement the {@code judge} object, or null when the reply could not be read
     */
    record Judged(Item item, @Nullable ObjectNode judgement) {

        /** Rejects a missing item. */
        Judged {
            Objects.requireNonNull(item, "item");
        }
    }

    /**
     * The judgements of a calibration run in item order.
     *
     * @param judged every item asked, read or not
     */
    record Report(List<Judged> judged) {

        /** Copies the list. */
        Report {
            judged = List.copyOf(judged);
        }

        /** The report body: the run's settings, the read cases and the unread ids. */
        String json(final String datasetId, final Set<String> splits) {
            Objects.requireNonNull(datasetId, "datasetId");
            Objects.requireNonNull(splits, "splits");
            final ObjectNode root = MAPPER.createObjectNode();
            root.put("suite", SUITE);
            root.put("dataset", datasetId);
            splits.stream().sorted().forEach(root.putArray("splits")::add);
            root.put("items", judged.size());
            final ArrayNode cases = root.putArray("cases");
            final ArrayNode unparsed = root.putArray("unparsed");
            judged.forEach(j -> {
                if (j.judgement() == null) {
                    unparsed.add(j.item().id());
                } else {
                    cases.add(caseRecord(j.item(), j.judgement()));
                }
            });
            return root.toString();
        }

        /** One line per item, then the count of unread replies. */
        String table() {
            final List<String> lines = new ArrayList<>();
            judged.forEach(j -> lines.add(String.format(
                    Locale.ROOT,
                    "%-8s %-6s %-8s %-5s %s",
                    j.item().id(),
                    j.item().kind().wire(),
                    j.item().split(),
                    j.item().pair(),
                    j.judgement() == null ? "unparsed" : j.judgement().toString())));
            lines.add("judged " + judged.size() + ", unparsed "
                    + judged.stream().filter(j -> j.judgement() == null).count());
            return String.join("\n", lines);
        }

        private static ObjectNode caseRecord(final Item item, final ObjectNode judgement) {
            final ObjectNode record = MAPPER.createObjectNode();
            record.put("caseId", item.id());
            record.put("repeat", 0);
            record.put("seed", JudgeRunner.SEED);
            record.put("kind", item.kind().wire());
            record.put("split", item.split());
            record.put("pair", item.pair());
            record.putArray("graders");
            record.set("judge", judgement);
            return record;
        }
    }

    /** Asks the judge about every item in order; a pair is asked in both orders. */
    Report run(final List<Item> items) {
        Objects.requireNonNull(items, "items");
        log.info("Judge calibration starting items={}", items.size());
        final List<Judged> judged = items.stream().map(this::judged).toList();
        final List<String> unread = judged.stream()
                .filter(j -> j.judgement() == null)
                .map(j -> j.item().id())
                .toList();
        log.info("Judge calibration finished items={} unparsed={}", items.size(), unread);
        return new Report(judged);
    }

    private Judged judged(final Item item) {
        log.debug(
                "Judge calibration item id={} kind={} split={} pair={}",
                item.id(),
                item.kind(),
                item.split(),
                item.pair());
        return new Judged(
                item,
                switch (item.kind()) {
                    case SINGLE -> single(item);
                    case PAIR -> pair(item);
                });
    }

    private @Nullable ObjectNode single(final Item item) {
        final JudgeReply reply =
                judge.judgeOne(item.judgeItem(), item.translations().get(0));
        if (!(reply instanceof Scored scored)) {
            log.warn("Judge calibration reply unreadable id={} reason={}", item.id(), reply.describe());
            return null;
        }
        final Scores s = scored.scores();
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("meaningError", scored.flags().meaningError());
        node.put("score", (s.meaning() + s.grammar() + s.terminology() + s.fluency()) / SCORE_COUNT);
        node.putObject("scores")
                .put("meaning", s.meaning())
                .put("grammar", s.grammar())
                .put("terminology", s.terminology())
                .put("fluency", s.fluency());
        node.put("mqm", scored.mqmPenalty());
        node.put("verdict", scored.verdict().wire());
        return node;
    }

    private @Nullable ObjectNode pair(final Item item) {
        final JudgeRunner.PairOrders orders = judge.judgePairOrders(
                item.judgeItem(),
                item.translations().get(0),
                item.translations().get(1));
        final Winner forward = orders.forward();
        final Winner swapped = orders.swapped();
        if (forward == null || swapped == null) {
            log.warn("Judge calibration pair unreadable id={} forward={} swapped={}", item.id(), forward, swapped);
            return null;
        }
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("winner", forward.wire());
        node.put("winnerSwapped", swapped.wire());
        return node;
    }
}
