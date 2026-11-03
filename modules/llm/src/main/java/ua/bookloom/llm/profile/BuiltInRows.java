package ua.bookloom.llm.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;

/**
 * The built-in rows: what BookLoom knows about how each family of models is templated and switched, written as data
 * with the rule in words beside it. A row is picked by the model's parsed name alone. The architecture the server
 * reports is deliberately not read: {@code llama} and {@code deepseek2} are shared by unrelated models, and a new
 * family built on a known architecture (Nemotron 3.5 Bolt reports a Nemotron or Gemma one) must get the default
 * profile until it has a row of its own.
 */
@Slf4j
@Singleton
public final class BuiltInRows implements ProfileRows {

    private final List<BuiltInRow> rows;

    /** The rows of every family group. */
    @Inject
    public BuiltInRows() {
        final List<BuiltInRow> all = new ArrayList<>();
        all.addAll(GemmaRows.all());
        all.addAll(MistralRows.all());
        all.addAll(QwenRows.all());
        all.addAll(OtherRows.all());
        all.addAll(ForkRows.all());
        all.addAll(LlamaRows.all());
        this.rows = List.copyOf(all);
    }

    /**
     * Lists the rows.
     *
     * @return every built-in row; never null
     */
    public List<BuiltInRow> rows() {
        return rows;
    }

    @Override
    public Optional<Picked> pick(final ModelIdentity identity, final ModelFacts facts) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(facts, "facts");
        final List<BuiltInRow> matching = rows.stream()
                .filter(r -> r.pattern().matches(identity))
                .sorted(Comparator.comparingInt((BuiltInRow r) -> r.pattern().specificity())
                        .reversed())
                .toList();
        if (matching.isEmpty()) {
            log.debug(
                    "No built-in row family={} version={} size={} architecture={}: the default profile",
                    identity.family(),
                    identity.version(),
                    identity.sizeB(),
                    facts.architecture());
            return Optional.empty();
        }
        return Optional.of(best(identity, matching));
    }

    private static Picked best(final ModelIdentity identity, final List<BuiltInRow> matching) {
        final BuiltInRow best = matching.getFirst();
        if (matching.size() > 1) {
            log.warn(
                    "{} rows match {}; the most specific is {}",
                    matching.size(),
                    identity.family(),
                    best.row().name());
        }
        log.debug(
                "Built-in row {} family={} match={}",
                best.row().name(),
                identity.family(),
                best.pattern().match());
        return new Picked(best.row(), best.pattern().match());
    }
}
