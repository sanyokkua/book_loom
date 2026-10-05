package ua.bookloom.pipeline.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.batch.BatchEntries.Entry;

/**
 * Reads a batch reply per id, tolerant of how it is wrapped and strict about what each id says: every expected id must
 * come back exactly once with text, so a missing, repeated, extra or merged id is named and only that id falls back to
 * a single-segment draft. The parser decides nothing about the model's text beyond the id and the item checks of
 * {@link ItemValidator}; the run's gates see an accepted target afterwards.
 */
@Slf4j
public final class BatchReplyParser {

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+(?=\\s|$|[»”\"’])");

    private final BatchEntries entries;

    /**
     * Creates a parser.
     *
     * @param mapper the non-null application JSON mapper
     */
    public BatchReplyParser(final ObjectMapper mapper) {
        this.entries = new BatchEntries(Objects.requireNonNull(mapper, "mapper"));
    }

    /**
     * Reads a reply against the batch it answers.
     *
     * @param reply the non-null reply text, as the model sent it
     * @param items the non-null batch items in order; ids unique
     * @param sourceTag the source language tag, or null when unknown, for the length band
     * @param targetTag the non-null target language tag
     * @return one outcome per expected id then per extra id; not readable when the reply holds no entry at all
     */
    public BatchReply parse(
            final String reply, final List<BatchItem> items, @Nullable final String sourceTag, final String targetTag) {
        Objects.requireNonNull(items, "items");
        log.debug("Parsing batch reply items={} replyLength={}", items.size(), reply.length());
        final List<Entry> found = entries.read(reply);
        if (found.isEmpty()) {
            log.warn("Batch reply held no entry items={}", items.size());
            return BatchReply.unreadable(items.stream().map(BatchItem::id).toList());
        }
        final Map<String, List<String>> byId = new HashMap<>();
        final Map<String, Map<String, String>> termsById = new HashMap<>();
        found.forEach(entry -> {
            byId.computeIfAbsent(entry.id(), id -> new ArrayList<>()).add(entry.text());
            termsById.putIfAbsent(entry.id(), entry.terms());
        });
        final List<ItemOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            outcomes.add(outcome(items, i, byId, sourceTag, targetTag)
                    .withTerms(termsById.getOrDefault(items.get(i).id(), Map.of())));
        }
        final Set<String> expected =
                new LinkedHashSet<>(items.stream().map(BatchItem::id).toList());
        byId.forEach((id, texts) -> {
            if (!expected.contains(id)) {
                outcomes.add(new ItemOutcome(id, ItemStatus.EXTRA, texts.getFirst(), List.of()));
            }
        });
        final BatchReply result = new BatchReply(true, outcomes);
        log.debug("Parsed batch reply accepted={} failing={}", result.acceptedIds(), result.failingIds());
        return result;
    }

    private static ItemOutcome outcome(
            final List<BatchItem> items,
            final int index,
            final Map<String, List<String>> byId,
            @Nullable final String sourceTag,
            final String targetTag) {
        final BatchItem item = items.get(index);
        final List<String> written = byId.getOrDefault(item.id(), List.of());
        final List<String> texts =
                written.stream().filter(text -> !text.isBlank()).toList();
        if (texts.isEmpty()) {
            return ItemOutcome.bare(item.id(), ItemStatus.MISSING);
        }
        if (written.size() > 1) {
            return ItemOutcome.bare(item.id(), ItemStatus.DUPLICATE);
        }
        final String text = texts.getFirst();
        final BatchItem next = index + 1 < items.size() ? items.get(index + 1) : null;
        if (next != null && isMerged(item, next, text, byId, sourceTag, targetTag)) {
            log.warn("Batch item looks merged with the next id={} next={}", item.id(), next.id());
            return new ItemOutcome(item.id(), ItemStatus.MERGED_SUSPECT, text, List.of());
        }
        return new ItemOutcome(
                item.id(), ItemStatus.OK, text, ItemValidator.validate(item, text, sourceTag, targetTag));
    }

    /**
     * A text that answers for two items: the next id has no text of its own, and either the text holds this item's
     * tokens and the next item's together, or it is longer than the pair's band allows for this item alone.
     */
    private static boolean isMerged(
            final BatchItem item,
            final BatchItem next,
            final String text,
            final Map<String, List<String>> byId,
            @Nullable final String sourceTag,
            final String targetTag) {
        final boolean nextAnswered =
                byId.getOrDefault(next.id(), List.of()).stream().anyMatch(t -> !t.isBlank());
        if (nextAnswered) {
            return false;
        }
        final List<String> both = new ArrayList<>(Tokens.inOrder(item.masked()));
        both.addAll(Tokens.inOrder(next.masked()));
        final boolean holdsBoth = both.size() > Tokens.inOrder(item.masked()).size()
                && Tokens.inOrder(text).equals(both);
        final double limit = ItemValidator.mergeRatio(item.masked(), sourceTag, targetTag);
        final int own = ItemValidator.chars(item.masked());
        final boolean tooLong = own > 0 && ItemValidator.chars(text) > limit * own;
        final boolean extraSentences = sentenceEnds(text)
                >= sentenceEnds(DisplayText.of(item.masked()))
                        + Math.max(1, sentenceEnds(DisplayText.of(next.masked())));
        return holdsBoth || tooLong || extraSentences;
    }

    /** How many sentences a text ends: a run of full stops, marks or ellipsis before a space, a closing quote or the end. */
    private static int sentenceEnds(final String text) {
        final Matcher matcher = SENTENCE_END.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
