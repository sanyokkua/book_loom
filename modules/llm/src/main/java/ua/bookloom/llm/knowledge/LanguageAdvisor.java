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
import ua.bookloom.api.llm.LanguageState;
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
    private static final Pattern RUN_TOKEN = Pattern.compile("BOOKLOOM-RUN:(\\w+(?:-\\w+)*)>(\\w+(?:-\\w+)*)");
    private static final LanguageResult NOTHING = new LanguageResult(LanguageState.UNKNOWN, LanguageSource.NONE, null);

    private final @Nullable KnowledgeTables tables;
    private final BaseKeyResolver baseKeys;

    /**
     * Reads the bundled table once.
     *
     * @param files the knowledge files
     */
    @Inject
    public LanguageAdvisor(final ModelKnowledgeFiles files) {
        this(Objects.requireNonNull(files, "files").load().data(), BaseKeyResolver.IDENTITY_BASE_FAMILY);
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
     * @return true only when the model's own row's evidence names a run for that pair; a maker's list, a base model's
     *     row and a sibling model never count
     */
    public boolean testedPair(final ModelIdentity id, final String source, final String target) {
        Objects.requireNonNull(id, "id");
        final String from = LanguageGroups.groupOf(Objects.requireNonNull(source, "source"));
        final String to = LanguageGroups.groupOf(Objects.requireNonNull(target, "target"));
        final boolean tested =
                rowFor(ownKey(id)).map(row -> hasRun(row, from, to)).orElse(false);
        log.debug("tested pair model={} source={} target={} tested={}", id.family(), source, target, tested);
        return tested;
    }

    private LanguageResult fromRows(final ModelIdentity id, final String group) {
        final Optional<LanguageRow> own = rowFor(ownKey(id));
        if (own.isPresent()) {
            return new LanguageResult(
                    stateOf(own.get(), group), LanguageSource.ROW, own.get().key());
        }
        final Optional<LanguageRow> base = baseKeys.baseKeyOf(id).flatMap(this::rowFor);
        return base.map(row -> new LanguageResult(stateOf(row, group), LanguageSource.BASE, row.key()))
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

    private static boolean hasRun(final LanguageRow row, final String from, final String to) {
        final Matcher matcher = RUN_TOKEN.matcher(row.evidence());
        while (matcher.find()) {
            if (LanguageGroups.groupOf(matcher.group(1)).equals(from)
                    && LanguageGroups.groupOf(matcher.group(2)).equals(to)) {
                return true;
            }
        }
        return false;
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
