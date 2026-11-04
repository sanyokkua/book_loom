package ua.bookloom.llm.knowledge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageDeclaration;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;

/**
 * Says whether a model's maker declares a language, from the bundled table. A warning must rest on a real gap, so
 * only a complete list can say "not declared"; the server's own language list may add a language and never takes one
 * away.
 */
@Slf4j
@Singleton
public final class LanguageAdvisor {

    private static final String ENGLISH = "en";
    private static final Pattern RUN_TOKEN = Pattern.compile("BOOKLOOM-RUN:([\\w-]+)>([\\w-]+)(?:@(\\w+))?");
    private static final Pattern QUANT_TOKEN = Pattern.compile("BOOKLOOM-QUANT:([\\w.-]+?)(?:@(\\w+))?(?=[;\\s]|$)");
    private static final List<String> FIRST_CODES = List.of("uk", "ru");
    private static final LanguageResult NOTHING = new LanguageResult(LanguageState.UNKNOWN, LanguageSource.NONE, null);

    private final @Nullable KnowledgeTables tables;
    private final BaseKeyResolver baseKeys;

    /**
     * Reads the bundled table once, taking a fine-tune's base from the name grammar alone.
     *
     * @param files the knowledge files
     */
    public LanguageAdvisor(final ModelKnowledgeFiles files) {
        this(files, BaseKeyResolver.IDENTITY_BASE_FAMILY);
    }

    /**
     * Reads the bundled table once.
     *
     * @param files the knowledge files
     * @param baseKeys names the model a fine-tune is built on
     */
    @Inject
    public LanguageAdvisor(final ModelKnowledgeFiles files, final BaseKeyResolver baseKeys) {
        this(Objects.requireNonNull(files, "files").load().data(), baseKeys);
    }

    LanguageAdvisor(final @Nullable KnowledgeTables tables, final BaseKeyResolver baseKeys) {
        this.tables = tables;
        this.baseKeys = Objects.requireNonNull(baseKeys, "baseKeys");
        if (tables == null) {
            log.warn("language table did not load; every language reads as unknown");
        }
    }

    /**
     * Says whether the maker declares a language.
     *
     * @param id the parsed model name
     * @param facts what the server said, or null when it has not been asked
     * @param bcp47Tag any language tag; compared by its primary subtag
     * @return the verdict and where it came from; never throws, unknown when the table is missing
     */
    public LanguageResult state(final ModelIdentity id, final @Nullable ModelFacts facts, final String bcp47Tag) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bcp47Tag, "bcp47Tag");
        final String group = LanguageGroups.groupOf(bcp47Tag);
        final LanguageResult result = fromFacts(fromRows(id, group), facts, group);
        log.debug(
                "language state model={} tag={} state={} source={}",
                id.family(),
                bcp47Tag,
                result.state(),
                result.source().label());
        return result;
    }

    /**
     * Whether BookLoom's own run proved this model on a pair.
     *
     * @param id the parsed model name
     * @param source the source language tag
     * @param target the target language tag
     * @return true only when the model's own row's evidence names a run for that pair (and, when the token names a
     *     size such as {@code @e4b}, for this size); a maker's list, a base model's row and a sibling model never
     *     count
     */
    public boolean testedPair(final ModelIdentity id, final String source, final String target) {
        Objects.requireNonNull(id, "id");
        final String from = LanguageGroups.groupOf(Objects.requireNonNull(source, "source"));
        final String to = LanguageGroups.groupOf(Objects.requireNonNull(target, "target"));
        final boolean tested =
                rowFor(ownKey(id)).map(row -> hasRun(row, id, from, to)).orElse(false);
        log.debug("tested pair model={} source={} target={} tested={}", id.family(), source, target, tested);
        return tested;
    }

    /**
     * The declaration the person reads for a model, from its own row or else its base model's.
     *
     * @param id the parsed model name
     * @return the maker's list kind, codes and where it was read (the server's own list is not part of it); empty when no row speaks for the model
     */
    public Optional<LanguageDeclaration> declaration(final ModelIdentity id) {
        Objects.requireNonNull(id, "id");
        final Optional<LanguageDeclaration> found =
                rowOf(id).map(located -> located.row().declaration());
        log.debug(
                "language declaration model={} kind={}",
                id.family(),
                found.map(d -> d.listKind().name()).orElse("none"));
        return found;
    }

    private record Located(LanguageRow row, LanguageSource source) {}

    // A row that says NONE says the maker published nothing, so a base model's list, when it has one, says more.
    private Optional<Located> rowOf(final ModelIdentity id) {
        final Optional<LanguageRow> own = rowFor(ownKey(id));
        if (own.isPresent() && own.get().listKind() != ListKind.NONE) {
            return own.map(row -> new Located(row, LanguageSource.ROW));
        }
        final Optional<LanguageRow> base = baseKeys.baseKeyOf(id).flatMap(this::rowFor);
        if (own.isPresent() && base.map(row -> row.listKind() == ListKind.NONE).orElse(true)) {
            return own.map(row -> new Located(row, LanguageSource.ROW));
        }
        return base.map(row -> new Located(row, LanguageSource.BASE));
    }

    private LanguageResult fromRows(final ModelIdentity id, final String group) {
        return rowOf(id)
                .map(found -> new LanguageResult(
                        stateOf(found.row(), group), found.source(), found.row().key()))
                .orElse(NOTHING);
    }

    private static LanguageResult fromFacts(
            final LanguageResult fromRows, final @Nullable ModelFacts facts, final String group) {
        if (fromRows.state() == LanguageState.DECLARED || facts == null) {
            return fromRows;
        }
        final Set<String> listed =
                facts.languages().stream().map(LanguageGroups::groupOf).collect(Collectors.toSet());
        final boolean englishOnly = listed.equals(Set.of(ENGLISH));
        return !englishOnly && listed.contains(group)
                ? new LanguageResult(LanguageState.DECLARED, LanguageSource.FACTS, fromRows.rowKey())
                : fromRows;
    }

    private static LanguageState stateOf(final LanguageRow row, final String group) {
        final boolean listed = row.codes().stream().map(LanguageGroups::groupOf).anyMatch(group::equals);
        return switch (row.listKind()) {
            case COMPLETE -> listed ? LanguageState.DECLARED : LanguageState.NOT_DECLARED;
            case POSITIVE_ONLY -> listed ? LanguageState.DECLARED : LanguageState.UNKNOWN;
            case NONE -> LanguageState.UNKNOWN;
        };
    }

    private static boolean hasRun(final LanguageRow row, final ModelIdentity id, final String from, final String to) {
        final Matcher matcher = RUN_TOKEN.matcher(row.evidence());
        while (matcher.find()) {
            if (LanguageGroups.groupOf(matcher.group(1)).equals(from)
                    && LanguageGroups.groupOf(matcher.group(2)).equals(to)
                    && sizeMatches(matcher.group(3), id)) {
                return true;
            }
        }
        return false;
    }

    // A token without a size covers the whole row; "e4b" covers the effective size 4, "12b" the total size 12.
    private static boolean sizeMatches(final @Nullable String size, final ModelIdentity id) {
        if (size == null) {
            return true;
        }
        return size.equalsIgnoreCase(billions("e", id.effB())) || size.equalsIgnoreCase(billions("", id.sizeB()));
    }

    private static String billions(final String prefix, final @Nullable Double size) {
        if (size == null) {
            return "";
        }
        final boolean whole = size == Math.rint(size);
        return prefix + (whole ? String.valueOf(size.longValue()) : String.valueOf(size)) + "b";
    }

    /**
     * The date of the bundled tables.
     *
     * @return the {@code data-version} of the knowledge files, or an empty text when they did not load
     */
    public String dataVersion() {
        return tables == null ? "" : tables.dataVersion();
    }

    /**
     * Whether a BookLoom run of this model at this quant is on record.
     *
     * @param id the parsed model name
     * @param quantToken the quant as judged, such as {@code nvfp4}
     * @return true only when the model's own row's evidence holds a {@code BOOKLOOM-QUANT:<token>} for this quant
     *     (and this size when the token names one)
     */
    public boolean quantMeasured(final ModelIdentity id, final String quantToken) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(quantToken, "quantToken");
        final boolean measured =
                rowFor(ownKey(id)).map(row -> hasQuantRun(row, id, quantToken)).orElse(false);
        log.debug("quant measured model={} quant={} measured={}", id.family(), quantToken, measured);
        return measured;
    }

    private static boolean hasQuantRun(final LanguageRow row, final ModelIdentity id, final String quantToken) {
        final Matcher matcher = QUANT_TOKEN.matcher(row.evidence());
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(quantToken) && sizeMatches(matcher.group(2), id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the maker's list says about a language pair, for the Models tab and Start.
     *
     * @param id the parsed model name
     * @param facts what the server said, or null when it has not been asked
     * @param source the source tag, or null for none; that side is not judged
     * @param target the target tag, or null for none; that side is not judged
     * @param familyLabel the model's family name for a sentence, such as {@code Llama 3.1}
     * @param baseLabel the display name of the model a fine-tune is built on, or null
     * @return the advice; a base model's list never makes a warning, only an information line
     */
    public LanguageAdvice advise(
            final ModelIdentity id,
            final @Nullable ModelFacts facts,
            final @Nullable String source,
            final @Nullable String target,
            final String familyLabel,
            final @Nullable String baseLabel) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(familyLabel, "familyLabel");
        final Optional<Located> located = rowOf(id);
        final Optional<LanguageResult> from = source == null ? Optional.empty() : Optional.of(state(id, facts, source));
        final Optional<LanguageResult> to = target == null ? Optional.empty() : Optional.of(state(id, facts, target));
        final boolean fromBase =
                located.map(row -> row.source() == LanguageSource.BASE).orElse(false);
        final LanguageAdvice advice = new LanguageAdvice(
                from.map(LanguageAdvisor::visibleState).orElse(LanguageState.DECLARED),
                to.map(LanguageAdvisor::visibleState).orElse(LanguageState.DECLARED),
                isBaseOnly(from, to),
                source != null && target != null && testedPair(id, source, target),
                familyLabel,
                fromBase ? baseLabel : null,
                fromBase && baseLabel != null
                        ? located.map(row -> ordered(row.row().codes())).orElse(List.of())
                        : List.of());
        log.debug(
                "language advice model={} source={} target={} states={}/{} baseOnly={} tested={}",
                id.family(),
                source,
                target,
                advice.sourceState(),
                advice.targetState(),
                advice.baseOnly(),
                advice.pairTested());
        return advice;
    }

    private static boolean isBaseOnly(final Optional<LanguageResult> from, final Optional<LanguageResult> to) {
        return Stream.of(from, to)
                .flatMap(Optional::stream)
                .anyMatch(result -> result.source() == LanguageSource.BASE && result.state() == LanguageState.DECLARED);
    }

    // A fine-tune's own gap is not the maker's word: a base model's list can add a language, never warn about one.
    private static LanguageState visibleState(final LanguageResult result) {
        return result.source() == LanguageSource.BASE && result.state() == LanguageState.NOT_DECLARED
                ? LanguageState.UNKNOWN
                : result.state();
    }

    private static List<String> ordered(final Set<String> codes) {
        return Stream.concat(
                        FIRST_CODES.stream().filter(codes::contains),
                        codes.stream()
                                .filter(code -> !FIRST_CODES.contains(code))
                                .sorted())
                .toList();
    }

    private static Optional<String> ownKey(final ModelIdentity id) {
        if (id.family() == null) {
            return Optional.empty();
        }
        return Optional.of(Stream.of(id.family(), id.version(), id.line())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("-")));
    }

    private Optional<LanguageRow> rowFor(final Optional<String> key) {
        return key.flatMap(this::rowFor);
    }

    private Optional<LanguageRow> rowFor(final String key) {
        if (tables == null) {
            return Optional.empty();
        }
        final List<String> tokens = NameKey.tokens(key);
        final Optional<LanguageRow> exact = tables.languageRows().stream()
                .filter(row -> row.matchKind() == MatchKind.EXACT && sameTokens(NameKey.tokens(row.pattern()), tokens))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return tables.languageRows().stream()
                .filter(row -> row.matchKind() == MatchKind.PREFIX && startsWith(tokens, NameKey.tokens(row.pattern())))
                .max(java.util.Comparator.comparingInt(
                        row -> NameKey.tokens(row.pattern()).size()));
    }

    /** The parser puts the version before a line and after a hyphenated family, so an exact row ignores the order. */
    private static boolean sameTokens(final List<String> a, final List<String> b) {
        return a.size() == b.size()
                && a.stream().sorted().toList().equals(b.stream().sorted().toList());
    }

    private static boolean startsWith(final List<String> tokens, final List<String> prefix) {
        return prefix.size() <= tokens.size()
                && tokens.subList(0, prefix.size()).equals(prefix);
    }
}
