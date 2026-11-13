package ua.bookloom.llm.profile;

import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The word tokens of a name once the quant, the shard counter, the companion prefix and the {@code Org_Model} vendor
 * are taken out.
 *
 * @param tokens the lower-case words, vendor word and tuner prefix removed
 * @param org the vendor of an {@code Org_Model} name, or null
 * @param finetuner the tuner prefix ("huihui"), or null
 * @param shard "part/total" of a split file, or null
 */
record NameWords(
        List<String> tokens,
        @Nullable String org,
        @Nullable String finetuner,
        @Nullable String shard) {

    static NameWords of(final String rest, final @Nullable String sidecar) {
        String low = rest;
        final Matcher shard = NameVocabulary.SHARD.matcher(low);
        final String shardText =
                shard.find() ? Integer.parseInt(shard.group(1)) + "/" + Integer.parseInt(shard.group(2)) : null;
        low = shard.reset().replaceFirst("");
        if (sidecar != null && low.matches(sidecar + "[-_.].*")) {
            low = low.substring(sidecar.length() + 1);
        }
        String org = null;
        final Matcher vendor = NameVocabulary.ORG_MODEL.matcher(low);
        if (vendor.matches() && !vendor.group(2).contains("_")) {
            org = vendor.group(1);
            low = vendor.group(2);
        }
        low = low.replace('_', '-').replaceAll("\\.+(?=-|$)", "").replaceAll("\\.(?=[a-z])", "-");
        List<String> tokens = withoutPublishers(
                Stream.of(low.split("-+")).filter(t -> !t.isEmpty()).toList(), sidecar != null);
        String finetuner = null;
        if (tokens.size() > 1 && NameVocabulary.FINETUNER_PREFIX.contains(tokens.getFirst())) {
            finetuner = tokens.getFirst();
            tokens = tokens.subList(1, tokens.size());
        }
        return new NameWords(tokens, org, finetuner, shardText);
    }

    private static List<String> withoutPublishers(final List<String> words, final boolean sidecar) {
        List<String> tokens = words;
        if (sidecar) {
            tokens = tokens.stream()
                    .filter(t -> !NameVocabulary.QUANTIZERS.contains(t))
                    .toList();
        }
        if (tokens.size() > 1 && NameVocabulary.VENDOR_TOKENS.contains(tokens.getFirst())) {
            tokens = tokens.subList(1, tokens.size());
        }
        return tokens;
    }
}
