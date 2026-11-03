package ua.bookloom.llm.profile;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.llm.knowledge.BaseKeyResolver;

/**
 * Which known base model a fine-tune is built on, one row per fine-tune with the evidence beside it. A lookup at the
 * profile level only: it never sets {@link ModelIdentity#baseFamily()}, which the name grammar owns. A row says whether
 * the fine-tune takes the base's behaviour row or only its declared languages (a reasoning distill keeps neither the
 * base's template nor its thinking switch). Sizes decide where one name is built on two bases.
 */
@Slf4j
@Singleton
public final class BaseFamilyAliases implements BaseKeyResolver {

    private static final double ANY_MIN = 0.0;
    private static final double ANY_MAX = Double.MAX_VALUE;

    /**
     * The base model a fine-tune is built on.
     *
     * @param family the base's family as the name grammar writes it, such as {@code qwen}; non-null
     * @param version the base's generation, or null when any
     * @param displayName how the person reads the base, such as {@code Qwen3}; non-null
     * @param languageRowKey the key of the base's row in the declared-languages table; non-null
     * @param inheritsBehaviour whether the fine-tune takes the base's behaviour row; a reasoning distill does not
     * @param evidence why the fine-tune is taken for this base, in plain words; non-blank
     */
    public record Base(
            String family,
            @Nullable String version,
            String displayName,
            String languageRowKey,
            boolean inheritsBehaviour,
            String evidence) {
        public Base {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(languageRowKey, "languageRowKey");
            Objects.requireNonNull(evidence, "evidence");
            if (evidence.isBlank()) {
                throw new IllegalArgumentException("a base needs its evidence: " + displayName);
            }
        }
    }

    private record Entry(
            Set<String> families,
            @Nullable String version,
            @Nullable String distillOf,
            double minB,
            double maxB,
            Base base) {

        boolean matches(final ModelIdentity id) {
            if (id.family() == null || !families.contains(id.family())) {
                return false;
            }
            if (version != null && !version.equals(id.version())) {
                return false;
            }
            if (distillOf != null && !distillOf.equals(id.distillOf())) {
                return false;
            }
            return !isSized() || (id.sizeB() != null && id.sizeB() >= minB && id.sizeB() <= maxB);
        }

        private boolean isSized() {
            return minB > ANY_MIN || maxB < ANY_MAX;
        }
    }

    private static final String NOUS =
            "Nous Research built Hermes 4 on Qwen3 at 14B and on Llama 3.1 at 70B and 405B; the size, never the family"
                    + " name alone, says which. ";

    private static final List<Entry> ENTRIES = List.of(
            entry(
                    Set.of("hermes"),
                    "4",
                    null,
                    13.0,
                    16.0,
                    new Base("qwen", "3", "Qwen3", "qwen3", true, NOUS + "The 14B is built on Qwen3-14B.")),
            entry(
                    Set.of("hermes"),
                    "4",
                    null,
                    60.0,
                    80.0,
                    new Base(
                            "llama",
                            "3.1",
                            "Llama 3.1",
                            "llama-3.1",
                            true,
                            NOUS + "The 70B is built on Llama-3.1-70B; the 405B has no row.")),
            entry(
                    Set.of("mimo-distill"),
                    null,
                    "qwen",
                    ANY_MIN,
                    ANY_MAX,
                    new Base(
                            "qwen",
                            "3.5",
                            "Qwen 3.5",
                            "qwen3.5",
                            true,
                            "The MiMo V2.6 distill names Qwen as its student and its name grammar reads Qwen 3.5.")),
            entry(
                    Set.of("thomson"),
                    null,
                    null,
                    ANY_MIN,
                    ANY_MAX,
                    new Base(
                            "qwen",
                            "3.6",
                            "Qwen 3.6",
                            "qwen3.6",
                            true,
                            "Thomson 1.0 is taken to be built on Qwen 3.6 (the change's task text; the model card was"
                                    + " not read).")),
            entry(
                    Set.of("bigbang"),
                    null,
                    null,
                    ANY_MIN,
                    ANY_MAX,
                    new Base(
                            "qwen",
                            "3.6",
                            "Qwen 3.6",
                            "qwen3.6",
                            true,
                            "BigBang is taken to be built on Qwen 3.6 (the change's task text; the model card was not"
                                    + " read).")),
            entry(
                    Set.of("mamaylm", "mamaylm-gemma"),
                    null,
                    null,
                    11.0,
                    ANY_MAX,
                    new Base(
                            "gemma",
                            "3",
                            "Gemma 3",
                            "gemma3",
                            true,
                            "MamayLM 12B and larger are Ukrainian-adapted Gemma 3 (the MamayLM cards).")),
            entry(
                    Set.of("mamaylm", "mamaylm-gemma"),
                    null,
                    null,
                    8.0,
                    10.0,
                    new Base(
                            "gemma",
                            "2",
                            "Gemma 2",
                            "gemma2",
                            true,
                            "MamayLM 9B is a Ukrainian-adapted Gemma 2 (the MamayLM cards).")),
            entry(
                    Set.of("deepseek-r1-distill"),
                    null,
                    "qwen",
                    ANY_MIN,
                    ANY_MAX,
                    new Base(
                            "qwen",
                            "2.5",
                            "Qwen 2.5",
                            "qwen2.5",
                            false,
                            "The DeepSeek-R1 distills on Qwen are students of Qwen 2.5; they reason by their own"
                                    + " template, so only the declared languages are taken.")),
            entry(
                    Set.of("deepseek-r1-distill"),
                    null,
                    "llama",
                    ANY_MIN,
                    20.0,
                    new Base(
                            "llama",
                            "3.1",
                            "Llama 3.1",
                            "llama-3.1",
                            false,
                            "The 8B DeepSeek-R1 distill is a student of Llama 3.1; it reasons by its own template, so"
                                    + " only the declared languages are taken.")),
            entry(
                    Set.of("deepseek-r1-distill"),
                    null,
                    "llama",
                    60.0,
                    80.0,
                    new Base(
                            "llama",
                            "3.3",
                            "Llama 3.3",
                            "llama-3.3",
                            false,
                            "The 70B DeepSeek-R1 distill is a student of Llama 3.3; it reasons by its own template,"
                                    + " so only the declared languages are taken.")));

    private static Entry entry(
            final Set<String> families,
            final @Nullable String version,
            final @Nullable String distillOf,
            final double minB,
            final double maxB,
            final Base base) {
        return new Entry(families, version, distillOf, minB, maxB, base);
    }

    /**
     * The base a fine-tune is built on.
     *
     * @param id the parsed model name; non-null
     * @return the base, or empty when the model is not a known fine-tune
     */
    public Optional<Base> baseOf(final ModelIdentity id) {
        Objects.requireNonNull(id, "id");
        final Optional<Base> found = ENTRIES.stream()
                .filter(entry -> entry.matches(id))
                .map(Entry::base)
                .findFirst();
        log.debug(
                "base family model={} size={} base={}",
                id.family(),
                id.sizeB(),
                found.map(Base::displayName).orElse("none"));
        return found;
    }

    @Override
    public Optional<String> baseKeyOf(final ModelIdentity id) {
        return baseOf(id).map(Base::languageRowKey);
    }
}
