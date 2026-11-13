package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelKind;

/**
 * Compares one parsed name with a row of a name-case file: every key of {@code expected_json} must match, and every
 * key of {@code must_be_absent} (comma-separated) must be empty. Keys the row does not list are not checked, so a
 * later grammar may add facts without touching the earlier file.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameCaseAssertions {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Reads a case's input the way its kind says: a GGUF base-model name is words, anything else is an id. */
    static ParsedName parse(final ModelIdentityParser parser, final String inputKind, final String input) {
        return "gguf-base-model-name".equals(inputKind) ? parser.parseBaseModelName(input) : parser.parse(input);
    }

    /** The must-be-absent list without the keys grammar v2 added alias facts for. */
    static @Nullable String withoutKeysV2Added(final @Nullable String mustBeAbsent) {
        return mustBeAbsent == null
                ? null
                : String.join(
                        ",",
                        splitKeys(mustBeAbsent).stream()
                                .filter(key -> !"base_family".equals(key) && !"active_b".equals(key))
                                .toList());
    }

    static void assertMatches(
            final String caseId,
            final ParsedName parsed,
            final String expectedJson,
            final @Nullable String mustBeAbsent) {
        final Map<String, Object> actual = fieldsOf(parsed);
        readExpected(expectedJson).forEach((key, want) -> assertField(caseId, actual, key, want));
        splitKeys(mustBeAbsent)
                .forEach(key -> assertThat(actual)
                        .as("%s: %s must be empty", caseId, key)
                        .doesNotContainKey(key));
    }

    private static void assertField(
            final String caseId, final Map<String, Object> actual, final String key, final Object want) {
        assertThat(actual).as("%s: %s present", caseId, key).containsKey(key);
        final Object got = actual.get(key);
        if (want instanceof Number number) {
            assertThat(got).as("%s: %s", caseId, key).isInstanceOf(Number.class);
            assertThat(((Number) got).doubleValue()).as("%s: %s", caseId, key).isEqualTo(number.doubleValue());
        } else {
            assertThat(got).as("%s: %s", caseId, key).isEqualTo(want);
        }
    }

    private static Map<String, Object> readExpected(final String json) {
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("bad expected_json: " + json, e);
        }
    }

    private static List<String> splitKeys(final @Nullable String keys) {
        final List<String> result = new ArrayList<>();
        if (keys != null) {
            for (final String key : keys.split(",", -1)) {
                result.add(key.strip());
            }
        }
        return result;
    }

    /** The non-empty facts of a parse under the snake_case keys the case files use. */
    static Map<String, Object> fieldsOf(final ParsedName parsed) {
        final Map<String, Object> out = new LinkedHashMap<>();
        final var id = parsed.identity();
        put(out, "family", id.family());
        put(out, "version", id.version());
        put(out, "line", id.line());
        put(out, "size_b", id.sizeB());
        put(out, "eff_b", id.effB());
        put(out, "active_b", id.activeB());
        // qat and imatrix are listed as variants too (the quant advisor reads them) but the corpus keeps them as flags.
        put(
                out,
                "variant",
                id.variants().stream()
                        .filter(v -> !"qat".equals(v) && !"imatrix".equals(v))
                        .toList());
        put(
                out,
                "kind",
                id.kind() == ModelKind.LLM
                        ? null
                        : id.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        put(out, "base_family", id.baseFamily());
        put(out, "distill_of", id.distillOf());
        put(out, "needs_show", id.needsShow());
        putFacts(out, parsed.facts());
        putPackaging(out, parsed.facts());
        putOrigin(out, parsed.origin());
        return out;
    }

    private static void putPackaging(final Map<String, Object> out, final NameFacts f) {
        put(out, "quant_type", f.quantType());
        put(out, "imatrix", f.imatrix() ? Boolean.TRUE : null);
        put(out, "dwq", f.dwq() ? Boolean.TRUE : null);
        put(out, "implied_format", f.impliedFormat());
        put(out, "sidecar", f.sidecar());
        put(out, "date8", f.dateEight());
        put(out, "date_mmdd", f.monthDay());
        put(out, "finetune", f.finetune());
        put(out, "finetuner", f.finetuner());
    }

    private static void putOrigin(final Map<String, Object> out, final NameOrigin o) {
        put(out, "input_form", o.inputForm());
        put(out, "quantizer", o.quantizer());
        put(out, "org", o.org());
        put(out, "repo", o.repo());
        put(out, "file", o.file());
        put(out, "subfolder", o.subfolder());
        put(out, "revision", o.revision());
        put(out, "router_variant", o.routerVariants());
        put(out, "server_alias", o.serverAlias());
        put(out, "alias", o.alias());
    }

    private static void putFacts(final Map<String, Object> out, final NameFacts f) {
        put(out, "namespace", f.namespace());
        put(out, "format", f.format());
        put(out, "quant", f.quant());
        put(out, "quant_extra", f.quantExtra());
        put(out, "dynamic", f.dynamic() ? Boolean.TRUE : null);
        put(out, "qat", f.qat() ? Boolean.TRUE : null);
        put(out, "unquantized", f.unquantized() ? Boolean.TRUE : null);
        put(out, "date", f.date());
        put(out, "ctx", f.ctx());
        put(out, "spec", f.spec());
        put(out, "release", f.release());
        put(out, "shard", f.shard());
        put(out, "instance", f.instance());
        put(out, "unspecified", f.unspecified());
        put(out, "experts", f.experts());
        put(out, "expert_b", f.expertB());
    }

    private static void put(final Map<String, Object> out, final String key, final @Nullable Object value) {
        final boolean empty = value == null || (value instanceof List<?> list && list.isEmpty());
        if (!empty) {
            out.put(key, value);
        }
    }
}
