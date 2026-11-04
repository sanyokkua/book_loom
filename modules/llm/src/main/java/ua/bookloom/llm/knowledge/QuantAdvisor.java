package ua.bookloom.llm.knowledge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.SizeClass;

/**
 * Says how much a quant costs in quality, from the bundled table. The name gives the token and its flags; the
 * server's facts, when they carry a quant the table knows, win on which row applies.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class QuantAdvisor {

    private static final String QAT_VARIANT = "qat";
    private static final String IMATRIX_VARIANT = "imatrix";
    private static final String QAT_ROW = "Q4_0_QAT";
    private static final String PLAIN_Q4_0 = "Q4_0";
    private static final String SOURCE_NAME = "name";
    private static final String SOURCE_FACTS = "facts";
    private static final String SOURCE_NONE = "none";
    private static final String SOURCE_NO_ROW = "no row";

    private final ModelKnowledgeFiles files;

    /**
     * Judges a model's quant.
     *
     * @param id the parsed name; its quant label, flags and size are used
     * @param facts what the server said, or null when it has not been asked
     * @return the advice; token-only (null tier) when the size is unknown, or when no token is known at all
     */
    public QuantAdvice advise(final ModelIdentity id, final @Nullable ModelFacts facts) {
        Objects.requireNonNull(id, "id");
        final String label = id.quantLabel();
        final String factsQuant =
                facts == null || facts.quant() == null ? null : facts.quant().trim();
        final String token = label != null ? label : Objects.requireNonNullElse(factsQuant, "");
        final boolean dynamic = label != null && QuantLabel.isDynamic(label);
        final boolean qat = id.variants().contains(QAT_VARIANT);
        final boolean imatrix = id.variants().contains(IMATRIX_VARIANT);
        final String format =
                facts == null || facts.format() == null ? null : facts.format().toUpperCase(Locale.ROOT);
        final QuantAdvice tokenOnly =
                new QuantAdvice(token, null, format, dynamic, imatrix, qat, null, null, null, SOURCE_NONE);
        final var tables = files.load();
        if (token.isEmpty() || tables.isErr()) {
            log.warn("quant advice is token-only model={} token={} tablesLoaded={}", id.family(), token, tables.isOk());
            return tokenOnly;
        }
        final KnowledgeTables knowledge = Objects.requireNonNull(tables.data());
        final Optional<QuantRow> byName = label == null ? Optional.empty() : nameRow(knowledge, label);
        final Optional<QuantRow> byFacts = factsQuant == null ? Optional.empty() : knowledge.quantRow(factsQuant);
        logDisagreement(id, byName, byFacts);
        final String source = byFacts.isPresent() ? SOURCE_FACTS : SOURCE_NAME;
        final Optional<QuantRow> row = byFacts.or(() -> byName).map(r -> qatRow(knowledge, r, qat));
        final QuantAdvice advice =
                row.map(r -> judged(id, facts, tokenOnly, r, source)).orElseGet(() -> unmeasured(id, facts, tokenOnly));
        logAdvice(id, advice);
        return advice;
    }

    private static QuantAdvice unmeasured(
            final ModelIdentity id, final @Nullable ModelFacts facts, final QuantAdvice tokenOnly) {
        return withTier(tokenOnly, SizeClassifier.classify(id, facts), QuantTier.UNMEASURED, SOURCE_NO_ROW);
    }

    private static void logAdvice(final ModelIdentity id, final QuantAdvice advice) {
        log.debug(
                "quant advice model={} token={} base={} sizeClass={} tier={} source={}",
                id.family(),
                advice.token(),
                advice.baseType(),
                advice.sizeClass(),
                advice.tier(),
                advice.source());
    }

    private static QuantAdvice withTier(
            final QuantAdvice from,
            final @Nullable SizeClass size,
            final @Nullable QuantTier tier,
            final String source) {
        return new QuantAdvice(
                from.token(),
                from.baseType(),
                from.format(),
                from.dynamic(),
                from.imatrix(),
                from.qat(),
                from.bitClass(),
                size,
                tier,
                source);
    }

    private static Optional<QuantRow> nameRow(final KnowledgeTables tables, final String label) {
        return QuantLabel.candidates(label).stream()
                .map(tables::quantRow)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static QuantRow qatRow(final KnowledgeTables tables, final QuantRow row, final boolean qat) {
        return qat && PLAIN_Q4_0.equals(row.token()) ? tables.quantRow(QAT_ROW).orElse(row) : row;
    }

    private static QuantAdvice judged(
            final ModelIdentity id,
            final @Nullable ModelFacts facts,
            final QuantAdvice tokenOnly,
            final QuantRow row,
            final String source) {
        final SizeClass size = SizeClassifier.classify(id, facts);
        final String base = QuantLabel.baseType(row.token());
        final QuantTier tier = tierFor(row, size, tokenOnly.dynamic(), base);
        final QuantAdvice withRow = new QuantAdvice(
                tokenOnly.token(),
                base,
                tokenOnly.format(),
                tokenOnly.dynamic(),
                tokenOnly.imatrix(),
                tokenOnly.qat(),
                (int) Math.floor(row.bpw()) + "-bit",
                null,
                null,
                source);
        return withTier(withRow, size, tier, source);
    }

    private static @Nullable QuantTier tierFor(
            final QuantRow row, final @Nullable SizeClass size, final boolean dynamic, final String base) {
        if (size == null) {
            final long distinct = Stream.of(SizeClass.values())
                    .map(row::tier)
                    .flatMap(Optional::stream)
                    .distinct()
                    .count();
            final boolean complete = row.tiers().size() == SizeClass.values().length;
            return complete && distinct == 1 ? row.tier(SizeClass.SMALL).orElse(null) : null;
        }
        return row.tier(size)
                .map(tier -> TierLift.apply(tier, dynamic, row.bpw(), base, size))
                .orElse(QuantTier.UNMEASURED);
    }

    private void logDisagreement(
            final ModelIdentity id, final Optional<QuantRow> byName, final Optional<QuantRow> byFacts) {
        if (byName.isPresent()
                && byFacts.isPresent()
                && !byName.get().token().equals(byFacts.get().token())) {
            log.debug(
                    "quant advice model={} name row {} and facts row {} differ; facts win",
                    id.family(),
                    byName.get().token(),
                    byFacts.get().token());
        }
    }
}
