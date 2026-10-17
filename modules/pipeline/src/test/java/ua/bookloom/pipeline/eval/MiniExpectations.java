package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;

/**
 * What the mini eval book must come to ({@code eval/mini/expectations.json}): the checks every segment must pass, the
 * checks of single segments and groups, and what the glossary must hold after the names pass. Each carries the defect
 * it guards, so a reader sees which owner-run defect a failing expectation is the return of.
 *
 * @param every checks applied to every block; one result each, failing when any block fails
 * @param segments checks of one segment, or of a group of segments
 * @param glossary checks of glossary entries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MiniExpectations(List<Expectation> every, List<Expectation> segments, List<GlossaryExpectation> glossary) {

    private static final String RESOURCE = "/eval/mini/expectations.json";

    /** What a segment check tests. */
    enum CheckKind {
        /** The target holds no Latin word but the listed ones. */
        NO_LATIN_EXCEPT,
        /** The target holds no letter only Russian has. */
        NO_RUSSIAN_LETTERS,
        /** The target matches every listed regular expression. */
        MATCHES,
        /** The target matches none of the listed regular expressions. */
        ABSENT,
        /** The segment ended with a usable target and was not flagged. */
        NOT_FLAGGED,
        /** The segment carries no stored finding raised by any of the listed checks (a {@code raisedBy} name). */
        NO_FINDING,
        /** No past-tense word after the narrator's {@code я} is feminine. */
        NARRATOR_MALE,
        /** The target is at least the listed share of the source's length. */
        MIN_RATIO,
        /** The targets of a group of segments address one listener in one register (ти or Ви, not both). */
        ADDRESS_SAME
    }

    /**
     * One check of a segment or a group.
     *
     * @param id the check's id in the report
     * @param defect the defect it guards, in a short phrase
     * @param segment the block id checked, or null for a group check
     * @param group the block ids of a group check
     * @param check what is tested
     * @param args the check's arguments: words, patterns, a ratio or a finding name
     * @param policies the name policies it applies to; empty for all
     */
    record Expectation(
            String id,
            String defect,
            @Nullable String segment,
            List<String> group,
            CheckKind check,
            List<String> args,
            List<NamePolicy> policies) {

        /** Copies the lists. */
        Expectation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(check, "check");
            group = group == null ? List.of() : List.copyOf(group);
            args = args == null ? List.of() : List.copyOf(args);
            policies = policies == null ? List.of() : List.copyOf(policies);
        }
    }

    /**
     * One check of the glossary after the names pass.
     *
     * @param id the check's id in the report
     * @param defect the defect it guards
     * @param terms the source terms the entry may be filed under, the first one present is checked
     * @param absent whether none of the terms may be in the glossary (junk)
     * @param optional whether a glossary without the entry passes
     * @param type the type it must have, or null for any
     * @param typeNot a type it must not have, or null
     * @param gender the gender it must have, or null for any
     * @param verified true for a gender the person or the book's evidence settled, false for one that is not settled
     *     (unknown or only suggested), null for either
     * @param locked whether it is locked, or null for either
     * @param targetCyrillic whether its target must be written in Cyrillic with no Latin letter
     * @param targetKept whether its target must equal its term
     * @param targetMatches a regular expression its target must match, or null
     * @param minWords the least number of words its target has, 0 for any
     * @param policies the name policies it applies to; empty for all
     */
    record GlossaryExpectation(
            String id,
            String defect,
            List<String> terms,
            boolean absent,
            boolean optional,
            @Nullable TermType type,
            @Nullable TermType typeNot,
            @Nullable Gender gender,
            @Nullable Boolean verified,
            @Nullable Boolean locked,
            boolean targetCyrillic,
            boolean targetKept,
            @Nullable String targetMatches,
            int minWords,
            List<NamePolicy> policies) {

        /** Copies the lists. */
        GlossaryExpectation {
            Objects.requireNonNull(id, "id");
            terms = terms == null ? List.of() : List.copyOf(terms);
            policies = policies == null ? List.of() : List.copyOf(policies);
        }
    }

    /** Copies the lists. */
    MiniExpectations {
        every = every == null ? List.of() : List.copyOf(every);
        segments = segments == null ? List.of() : List.copyOf(segments);
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    static MiniExpectations load() {
        try (InputStream in = MiniExpectations.class.getResourceAsStream(RESOURCE)) {
            return JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                    .build()
                    .readValue(Objects.requireNonNull(in, RESOURCE), MiniExpectations.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every expectation id, for the tests that hold the file to its book. */
    List<String> ids() {
        return Stream.of(
                        every.stream().map(Expectation::id),
                        segments.stream().map(Expectation::id),
                        glossary.stream().map(GlossaryExpectation::id))
                .flatMap(s -> s)
                .toList();
    }
}
